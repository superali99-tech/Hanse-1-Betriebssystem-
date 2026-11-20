package org.novaos.mobile;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.Map;

/**
 * Nova Family on a child's phone: runs all the time (notification "Geschützt von deiner Familie"),
 * receives the parents' rules at once, counts the screen time and puts the lock screen over
 * every app while the phone is locked (bedtime, locked by the parents, screen time used up).
 * Calls (parents, emergency, incoming) and the torch always work.
 */
public class FamilyService extends Service {
    private static final int NOTE = 7800;
    private static volatile FamilyService running;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View overlay;
    private String overlayKey = "";
    private volatile boolean alive;
    private Thread net;
    private long callUntil;        // calls: no lock screen until (ms)
    private boolean inCall;
    private long lastTick = System.currentTimeMillis();
    private long lastStatus;
    private String lastState = "";
    private String blockedApp;     // a blocked app is in front (with usage access)
    private BroadcastReceiver screen;
    private PhoneStateListener phone;
    private String torchId;
    private boolean torchOn;

    static void start(Context c) {
        if (!Family.child(c)) return;
        Intent i = new Intent(c, FamilyService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException ignored) {
            // not allowed right now (background start limits): the job tries again
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, FamilyService.class));
    }

    /** The rules changed: check at once. */
    static void refresh(Context c) {
        FamilyService s = running;
        if (s != null) s.ui.post(s::tick);
        else start(c);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        wm = getSystemService(WindowManager.class);
        foreground();
        screen = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                tick();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(screen, f);
        listenCalls();
        alive = true;
        net = new Thread(this::network, "nova-family-net");
        net.start();
        ui.post(loop);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Family.child(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        foreground();
        tick();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        alive = false;
        running = null;
        ui.removeCallbacks(loop);
        if (net != null) net.interrupt();
        try {
            unregisterReceiver(screen);
        } catch (RuntimeException ignored) {
            // not registered
        }
        if (phone != null) {
            TelephonyManager tm = getSystemService(TelephonyManager.class);
            if (tm != null) tm.listen(phone, PhoneStateListener.LISTEN_NONE);
        }
        hideOverlay();
        super.onDestroy();
        if (Family.child(this)) start(this);  // the protection keeps running
    }

    @SuppressWarnings("deprecation")
    private void foreground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel("family_guard", getString(R.string.family_guard_channel), NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, FamilyActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "family_guard") : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_shield).setContentTitle(getString(R.string.family_guard_title))
                .setContentText(getString(R.string.family_guard_text)).setContentIntent(open).setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTE, n);
    }

    // ------------------------------------------------------------ the parents' messages

    private void network() {
        int backoff = 3;
        while (alive) {
            try {
                Family.flush(this);
                Family.poll(this);          // what came while offline
                Family.sendStatus(this, !Family.prefs(this).getBoolean("apps_sent", false));
                Family.prefs(this).edit().putBoolean("apps_sent", true).apply();
                lastStatus = System.currentTimeMillis();
                backoff = 3;
                Family.stream(this);        // then wait for news
            } catch (IOException | RuntimeException e) {
                if (!alive) return;
                try {
                    Thread.sleep(backoff * 1000L);
                } catch (InterruptedException ie) {
                    return;
                }
                backoff = Math.min(backoff * 2, 300);
            }
        }
    }

    // ------------------------------------------------------------ every few seconds

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            tick();
            ui.postDelayed(this, blockedCheckPossible() ? 2000 : 10000);
        }
    };

    private void tick() {
        if (!Family.child(this)) {
            hideOverlay();
            stopSelf();
            return;
        }
        long now = System.currentTimeMillis();
        PowerManager pm = getSystemService(PowerManager.class);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        boolean using = pm != null && pm.isInteractive() && (km == null || !km.isKeyguardLocked());
        long passed = now - lastTick;
        lastTick = now;
        FamilyPolicy.State s = Family.state(this);
        if (using && passed > 0 && passed < 60000 && !s.locked && !inCall) Family.addScreenSeconds(this, passed / 1000);
        s = Family.state(this);
        blockedApp = using && !s.locked ? foregroundBlocked() : null;
        boolean calling = inCall || now < callUntil;
        if ((s.locked || blockedApp != null) && !calling) showOverlay(s);
        else hideOverlay();
        String key = s.locked + s.reason + s.until;
        if (!key.equals(lastState) || now - lastStatus > 15 * 60 * 1000L) {  // tell the parents
            lastState = key;
            lastStatus = now;
            new Thread(() -> Family.sendStatus(this, false)).start();
        }
    }

    // ------------------------------------------------------------ lock screen over all apps

    private void showOverlay(FamilyPolicy.State s) {
        FamilyPolicy policy = Family.policy(this);
        String key = (blockedApp != null ? "app" + blockedApp : s.reason + s.until) + policy.version;
        if (overlay != null && key.equals(overlayKey)) return;
        hideOverlay();
        if (!Settings.canDrawOverlays(this)) {
            // without the permission: the lock screen as activity (weaker, the parents are told)
            if (s.locked) startActivity(new Intent(this, LockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return;
        }
        FamilyPolicy.State shown = s;
        if (blockedApp != null && !s.locked) {
            shown = new FamilyPolicy.State();
            shown.locked = true;
            shown.reason = "app";
            shown.until = -1;
        }
        overlay = blockedApp != null && !s.locked ? appBlocked() : LockScreen.build(this, shown, policy, actions());
        overlayKey = key;
        @SuppressWarnings("deprecation")
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try {
            wm.addView(overlay, lp);
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private View appBlocked() {
        FamilyPolicy.State st = new FamilyPolicy.State();
        st.locked = true;
        st.reason = "app";
        st.until = -1;
        View v = LockScreen.build(this, st, Family.policy(this), actions());
        return v;
    }

    private void hideOverlay() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        overlay = null;
        overlayKey = "";
    }

    private LockScreen.Actions actions() {
        return new LockScreen.Actions() {
            @Override
            public void call(String number) {
                callUntil = System.currentTimeMillis() + 90 * 1000L;
                hideOverlay();
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    Toast.makeText(FamilyService.this, R.string.nova_no_app, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void torch() {
                toggleTorch();
            }

            @Override
            public void ask(String kind, int minutes) {
                Family.sendAsync(FamilyService.this, MiniJson.obj("type", "request", "kind", kind, "minutes", minutes));
                Toast.makeText(FamilyService.this, R.string.lock_asked, Toast.LENGTH_LONG).show();
            }

            @Override
            public void home() {
                lastForeground = null;
                hideOverlay();
                startActivity(new Intent(FamilyService.this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }

            @Override
            public void pin(String pin) {
                if (Family.pinOk(FamilyService.this, pin)) {
                    Family.prefs(FamilyService.this).edit().putLong("local_free", System.currentTimeMillis() + 30 * 60 * 1000L).apply();
                    Family.sendAsync(FamilyService.this, MiniJson.obj("type", "alert", "what", "pin_unlock"));
                    Toast.makeText(FamilyService.this, R.string.lock_pin_ok, Toast.LENGTH_LONG).show();
                    tick();
                } else {
                    Toast.makeText(FamilyService.this, R.string.lock_pin_wrong, Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    private void toggleTorch() {
        CameraManager cm = getSystemService(CameraManager.class);
        try {
            if (torchId == null) {
                for (String id : cm.getCameraIdList()) {
                    if (Boolean.TRUE.equals(cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                        torchId = id;
                        break;
                    }
                }
            }
            if (torchId == null) {
                Toast.makeText(this, R.string.no_torch, Toast.LENGTH_SHORT).show();
                return;
            }
            torchOn = !torchOn;
            cm.setTorchMode(torchId, torchOn);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_torch, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ calls always work

    @SuppressWarnings("deprecation")
    private void listenCalls() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        if (tm == null) return;
        phone = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String number) {
                inCall = state != TelephonyManager.CALL_STATE_IDLE;
                if (!inCall) callUntil = Math.max(callUntil, System.currentTimeMillis() + 3000);
                ui.post(FamilyService.this::tick);
            }
        };
        try {
            tm.listen(phone, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (RuntimeException ignored) {
            phone = null;
        }
    }

    // ------------------------------------------------------------ blocked apps (with usage access)

    static boolean usageAccess(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        @SuppressWarnings("deprecation")
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean blockedCheckPossible() {
        return !Family.policy(this).blocked.isEmpty() && usageAccess(this);
    }

    private String lastForeground;

    private String foregroundBlocked() {
        if (!blockedCheckPossible()) return null;
        UsageStatsManager um = getSystemService(UsageStatsManager.class);
        if (um == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents ev = um.queryEvents(now - 60_000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) lastForeground = e.getPackageName();
        }
        return lastForeground != null && Family.policy(this).blocked.contains(lastForeground) ? lastForeground : null;
    }

    // ------------------------------------------------------------ what protects this phone

    static boolean isDefaultHome(Context c) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    static boolean adminActive(Context c) {
        DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isAdminActive(new ComponentName(c, FamilyAdmin.class));
    }

    static boolean batteryFree(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** For the parents' overview: which protections are on. */
    static Map<String, Object> protection(Context c) {
        return MiniJson.obj("overlay", Settings.canDrawOverlays(c), "admin", adminActive(c), "home", isDefaultHome(c),
                "battery", batteryFree(c), "usage", usageAccess(c),
                "phone", c.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED);
    }
}
