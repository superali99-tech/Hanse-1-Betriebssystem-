package org.novaos.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.novaos.tv.KeyList;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NovaOS Mobile home screen (launcher): greeting, clock, app search, Nova cards (TV,
 * Nova Filme), quick settings, favorites and a dock with phone, messages, apps,
 * camera and browser. Swipe up = all apps, swipe down = notifications.
 */
public class HomeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout rows, dock;
    private ScrollView scroll;
    private TextView greeting;
    private List<App> apps = new ArrayList<>();
    private String shown;
    private BroadcastReceiver packageReceiver;
    private GestureDetector gestures;
    private View torchButton;
    private String torchId;
    private boolean torchOn;
    private CameraManager.TorchCallback torchCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_home);
        rows = findViewById(R.id.rows);
        dock = findViewById(R.id.dock);
        scroll = findViewById(R.id.scroll);
        greeting = findViewById(R.id.greeting);
        TextClock date = findViewById(R.id.date);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        findViewById(R.id.search).setOnClickListener(v -> openApps(true));
        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.root).setOnLongClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vy) < Math.abs(vx) * 1.5f || Math.abs(vy) < 900) return false;
                float dy = e2.getY() - e1.getY();
                if (dy < -Ui.dp(HomeActivity.this, 60) && !scroll.canScrollVertically(1)) {
                    openApps(false);
                    return true;
                }
                if (dy > Ui.dp(HomeActivity.this, 60) && !scroll.canScrollVertically(-1)) {
                    expandNotifications();
                    return true;
                }
                return false;
            }
        });

        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                loadApps();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
        setupTorch();
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        loadApps();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        scroll.smoothScrollTo(0, 0);  // home button pressed again: back to the top
    }

    @Override
    public void onBackPressed() {
        scroll.smoothScrollTo(0, 0);  // the home screen does not close
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        if (torchCallback != null) getSystemService(CameraManager.class).unregisterTorchCallback(torchCallback);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures.onTouchEvent(ev)) {
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void updateGreeting() {
        int[] res = {R.string.greeting_morning, R.string.greeting_day, R.string.greeting_evening, R.string.greeting_night};
        greeting.setText(res[Greeting.of(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))]);
    }

    private void openApps(boolean search) {
        startActivity(new Intent(this, AppsActivity.class).putExtra(AppsActivity.EXTRA_SEARCH, search));
    }

    private void loadApps() {
        worker.execute(() -> {
            List<App> list = Apps.load(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                apps = list;
                StringBuilder sig = new StringBuilder(Favorites.raw(this));
                for (App a : list) sig.append('|').append(a.key()).append(a.label);
                if (!sig.toString().equals(shown)) {
                    shown = sig.toString();
                    rebuild();
                }
            });
        });
    }

    // ------------------------------------------------------------ content
    private void rebuild() {
        rows.removeAllViews();
        int side = Ui.dp(this, 6);

        // Nova: TV and Nova Filme
        rows.addView(Ui.section(this, R.string.nova));
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(novaCard(R.drawable.ic_antenna, getString(R.string.card_tv),
                getString(R.string.card_tv_sub, Channels.get(this).size()), true,
                v -> startActivity(new Intent(this, TvActivity.class))), weight(0, side));
        cards.addView(novaCard(R.drawable.ic_play, getString(R.string.card_filme),
                getString(R.string.card_filme_sub), false,
                v -> startActivity(new Intent(this, FilmeActivity.class))), weight(side, 0));
        rows.addView(cards);

        // quick settings
        rows.addView(Ui.section(this, R.string.quick));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.addView(quickButton(R.drawable.ic_network, R.string.qs_wifi, v -> Launch.settings(this, Settings.ACTION_WIFI_SETTINGS)));
        quick.addView(quickButton(R.drawable.ic_bluetooth, R.string.qs_bluetooth, v -> Launch.settings(this, Settings.ACTION_BLUETOOTH_SETTINGS)));
        torchButton = quickButton(R.drawable.ic_torch, R.string.qs_torch, v -> toggleTorch());
        quick.addView(torchButton);
        quick.addView(quickButton(R.drawable.ic_display, R.string.qs_display, v -> Launch.settings(this, Settings.ACTION_DISPLAY_SETTINGS)));
        quick.addView(quickButton(R.drawable.ic_sound, R.string.qs_sound, v -> Launch.settings(this, Settings.ACTION_SOUND_SETTINGS)));
        quick.addView(quickButton(R.drawable.ic_battery, R.string.qs_battery, v -> Launch.settings(this, Intent.ACTION_POWER_USAGE_SUMMARY)));
        quick.addView(quickButton(R.drawable.ic_settings, R.string.qs_settings, v -> Launch.settings(this, Settings.ACTION_SETTINGS)));
        hs.addView(quick);
        rows.addView(hs);
        showTorch();

        // favorites
        rows.addView(Ui.section(this, R.string.favorites));
        Set<String> installed = new HashSet<>();
        for (App a : apps) installed.add(a.key());
        List<String> favs = KeyList.retain(Favorites.get(this), installed);
        if (favs.isEmpty()) {
            TextView t = Ui.text(this, getString(R.string.fav_empty), 14, getColor(R.color.text_muted), false);
            t.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            rows.addView(t);
        } else {
            int cols = Greeting.columns(Ui.widthDp(this) - 40, 84, 4, 7);
            LinearLayout row = null;
            for (int i = 0; i < favs.size(); i++) {
                if (i % cols == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    rows.addView(row);
                }
                App a = Apps.find(apps, favs.get(i));
                row.addView(appView(a, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            int rest = (cols - favs.size() % cols) % cols;
            for (int i = 0; i < rest; i++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            }
        }
        buildDock();
    }

    private LinearLayout.LayoutParams weight(int left, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 118), 1);
        lp.leftMargin = left;
        lp.rightMargin = right;
        return lp;
    }

    private View novaCard(int icon, String title, String sub, boolean accent, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(accent ? R.drawable.accent_card_bg : R.drawable.card_bg);
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
        View gap = new View(this);
        card.addView(gap, new LinearLayout.LayoutParams(1, 0, 1));
        card.addView(Ui.text(this, title, 18, getColor(R.color.text), true));
        TextView s = Ui.text(this, sub, 13, 0xDDFFFFFF, false);
        s.setSingleLine(true);
        card.addView(s);
        card.setClickable(true);
        card.setOnClickListener(click);
        card.setContentDescription(title);
        return card;
    }

    private View quickButton(int icon, int label, View.OnClickListener click) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.round_bg);
        int p = Ui.dp(this, 14);
        b.setPadding(p, p, p, p);
        b.setClickable(true);
        b.setOnClickListener(click);
        b.setContentDescription(getString(label));
        col.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        TextView t = Ui.text(this, getString(label), 12, getColor(R.color.text), false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.dp(this, 74), ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(this, 6);
        col.addView(t, tl);
        col.setPadding(0, 0, Ui.dp(this, 2), 0);
        return col;
    }

    private View appView(App a, boolean label) {
        LinearLayout cell = Ui.appCell(this, a, label);
        cell.setOnClickListener(v -> Launch.app(this, a, v));
        cell.setOnLongClickListener(v -> {
            AppMenu.show(this, a, () -> {
                shown = null;
                loadApps();
            });
            return true;
        });
        return cell;
    }

    /** Dock: phone, messages, all apps, camera, browser (the phone's default apps). */
    private void buildDock() {
        dock.removeAllViews();
        App[] slots = {
            Apps.defaultFor(this, apps, new Intent(Intent.ACTION_DIAL)),
            Apps.defaultFor(this, apps, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            null,
            Apps.defaultFor(this, apps, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            Apps.defaultFor(this, apps, new Intent(Intent.ACTION_VIEW, Uri.parse("https://"))),
        };
        for (int i = 0; i < slots.length; i++) {
            View v;
            if (i == 2) {
                ImageView all = new ImageView(this);
                all.setImageResource(R.drawable.ic_apps);
                all.setBackgroundResource(R.drawable.round_bg);
                int p = Ui.dp(this, 13);
                all.setPadding(p, p, p, p);
                all.setContentDescription(getString(R.string.all_apps));
                all.setOnClickListener(x -> openApps(false));
                LinearLayout box = new LinearLayout(this);
                box.setGravity(Gravity.CENTER);
                box.addView(all, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
                v = box;
            } else if (slots[i] != null) {
                v = appView(slots[i], false);
            } else {
                // empty slot (phone without this app): 1 px high, or it would take all the height
                dock.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
                continue;
            }
            dock.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    // ------------------------------------------------------------ system
    @SuppressLint("WrongConstant")
    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception | LinkageError e) {
            // not allowed on this phone: nothing happens
        }
    }

    private void setupTorch() {
        CameraManager cm = getSystemService(CameraManager.class);
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    torchId = id;
                    break;
                }
            }
        } catch (CameraAccessException | RuntimeException e) {
            torchId = null;
        }
        if (torchId == null) return;
        torchCallback = new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String id, boolean enabled) {
                if (id.equals(torchId)) {
                    torchOn = enabled;
                    showTorch();
                }
            }
        };
        cm.registerTorchCallback(torchCallback, null);
    }

    private void toggleTorch() {
        if (torchId == null) {
            Toast.makeText(this, R.string.no_torch, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            getSystemService(CameraManager.class).setTorchMode(torchId, !torchOn);
        } catch (CameraAccessException | RuntimeException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void showTorch() {
        if (torchButton instanceof ViewGroup) ((ViewGroup) torchButton).getChildAt(0).setActivated(torchOn);
    }
}
