package org.novaos.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Nova Family on this phone: the role (parent or child), the family code, the children
 * (on a parent's phone) or the rules (on a child's phone), and the encrypted messages over
 * the relay: join, policy, status, request, answer, alert.
 */
final class Family {
    interface Listener {
        void onFamilyChanged();
    }

    static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Family() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("family", Context.MODE_PRIVATE);
    }

    static String role(Context c) {
        return prefs(c).getString("role", "");
    }

    static boolean child(Context c) {
        return "child".equals(role(c));
    }

    static boolean parent(Context c) {
        return "parent".equals(role(c));
    }

    static String code(Context c) {
        return prefs(c).getString("code", "");
    }

    static String me(Context c) {
        SharedPreferences p = prefs(c);
        String id = p.getString("device", "");
        if (id.isEmpty()) {
            id = FamilyCrypto.randomId();
            p.edit().putString("device", id).apply();
        }
        return id;
    }

    static String relay(Context c) {
        String own = prefs(c).getString("relay", "");
        return own.isEmpty() ? c.getString(R.string.family_relay) : own;
    }

    private static String topic(Context c) {
        return FamilyCrypto.topic(code(c));
    }

    private static byte[] cachedKey;
    private static String cachedFor = "";

    private static synchronized byte[] key(Context c) {
        String code = code(c);
        if (!code.equals(cachedFor)) {
            cachedKey = FamilyCrypto.key(code);
            cachedFor = code;
        }
        return cachedKey;
    }

    private static Thread listener;

    /** Parents' phone: waits for the children's messages while NovaOS runs (requests arrive at once). */
    static synchronized void listen(Context c) {
        if (!parent(c) || (listener != null && listener.isAlive())) return;
        Context app = c.getApplicationContext();
        listener = new Thread(() -> {
            int backoff = 3;
            while (parent(app)) {
                try {
                    poll(app);
                    backoff = 3;
                    stream(app);
                } catch (IOException | RuntimeException e) {
                    try {
                        Thread.sleep(backoff * 1000L);
                    } catch (InterruptedException ie) {
                        return;
                    }
                    backoff = Math.min(backoff * 2, 300);
                }
            }
        }, "nova-family-listen");
        listener.start();
    }

    static void changed() {
        for (Listener l : LISTENERS) UI.post(l::onFamilyChanged);
    }

    // ------------------------------------------------------------ setting up

    /** This phone becomes a parent's phone (new family, or joining with the code of the other parent). */
    static void becomeParent(Context c, String code, String name, String phone, String pin) {
        SharedPreferences.Editor e = prefs(c).edit().putString("role", "parent").putString("code", code)
                .putString("name", name).putString("phone", phone).putString("since", "12h");
        if (pin != null && !pin.isEmpty()) {
            String salt = FamilyCrypto.randomId();
            e.putString("pin_salt", salt).putString("pin_hash", FamilyCrypto.pinHash(pin, salt));
        }
        e.apply();
        FamilyJob.schedule(c);
        sendAsync(c, MiniJson.obj("type", "ping", "to", "*", "apps", true));
        listen(c);
    }

    /** This phone becomes a child's phone of the family with this code. */
    static void becomeChild(Context c, String code, String name) {
        prefs(c).edit().putString("role", "child").putString("code", code).putString("name", name).putString("since", "12h").apply();
        sendAsync(c, MiniJson.obj("type", "join", "name", name, "model", Build.MANUFACTURER + " " + Build.MODEL));
        FamilyService.start(c);
        FamilyJob.schedule(c);
    }

    /** Leaves the family (child: only with the parents' PIN or when the parents release it). */
    static void leave(Context c) {
        if (child(c)) sendAsync(c, MiniJson.obj("type", "alert", "what", "left"));
        String device = me(c), relay = prefs(c).getString("relay", "");
        prefs(c).edit().clear().putString("device", device).putString("relay", relay).apply();
        FamilyService.stop(c);
        changed();
    }

    // ------------------------------------------------------------ child: the rules

    static FamilyPolicy policy(Context c) {
        try {
            return FamilyPolicy.fromJson(MiniJson.parse(prefs(c).getString("policy", "{}")));
        } catch (IllegalArgumentException e) {
            return new FamilyPolicy();
        }
    }

    static long localFree(Context c) {
        return prefs(c).getLong("local_free", 0);
    }

    static boolean pinOk(Context c, String pin) {
        FamilyPolicy p = policy(c);
        if (p.pinHash.isEmpty()) return false;
        return FamilyCrypto.pinHash(pin, p.pinSalt).equals(p.pinHash);
    }

    /** Screen time of today in minutes. */
    static int screenMinutes(Context c) {
        SharedPreferences p = prefs(c);
        String today = FamilyPolicy.day(System.currentTimeMillis(), TimeZone.getDefault());
        return today.equals(p.getString("screen_day", "")) ? (int) (p.getLong("screen_sec", 0) / 60) : 0;
    }

    static void addScreenSeconds(Context c, long seconds) {
        SharedPreferences p = prefs(c);
        String today = FamilyPolicy.day(System.currentTimeMillis(), TimeZone.getDefault());
        long sec = today.equals(p.getString("screen_day", "")) ? p.getLong("screen_sec", 0) : 0;
        p.edit().putString("screen_day", today).putLong("screen_sec", sec + seconds).apply();
    }

    static FamilyPolicy.State state(Context c) {
        if (!child(c)) return new FamilyPolicy.State();
        return policy(c).evaluate(System.currentTimeMillis(), TimeZone.getDefault(), screenMinutes(c), localFree(c));
    }

    /** Apps the parents blocked (hidden on the home screen of a child's phone). */
    static List<String> blocked(Context c) {
        return child(c) ? policy(c).blocked : new ArrayList<>();
    }

    // ------------------------------------------------------------ parent: the children

    static final class Child {
        String id = "", name = "", model = "", reason = "";
        long lastSeen, until, applied;  // applied: version of the rules the child's phone reported
        int battery = -1, screen;
        boolean locked;
        Map<String, Object> protection = new LinkedHashMap<>();
        FamilyPolicy policy = new FamilyPolicy();
        List<String[]> apps = new ArrayList<>();
    }

    static List<Child> children(Context c) {
        List<Child> out = new ArrayList<>();
        try {
            Object all = MiniJson.parse(prefs(c).getString("children", "{}"));
            if (all instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) all).entrySet()) out.add(childFrom(String.valueOf(e.getKey()), e.getValue()));
            }
        } catch (IllegalArgumentException ignored) {
            // none
        }
        return out;
    }

    static Child child(Context c, String id) {
        for (Child x : children(c)) if (x.id.equals(id)) return x;
        return null;
    }

    private static Child childFrom(String id, Object o) {
        Child ch = new Child();
        ch.id = id;
        ch.name = str(o, "name");
        ch.model = str(o, "model");
        ch.reason = str(o, "reason");
        ch.lastSeen = num(o, "lastSeen");
        ch.until = num(o, "until");
        ch.battery = (int) num(o, "battery", -1);
        ch.screen = (int) num(o, "screen");
        ch.locked = Boolean.TRUE.equals(MiniJson.get(o, "locked"));
        ch.applied = num(o, "applied");
        Object pr = MiniJson.get(o, "protection");
        if (pr instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) pr).entrySet()) ch.protection.put(String.valueOf(e.getKey()), e.getValue());
        ch.policy = FamilyPolicy.fromJson(MiniJson.get(o, "policy"));
        Object apps = MiniJson.get(o, "apps");
        if (apps instanceof List) for (Object a : (List<?>) apps) ch.apps.add(new String[] {str(a, "p"), str(a, "l")});
        return ch;
    }

    private static synchronized void saveChild(Context c, Child ch) {
        Map<String, Object> all = new LinkedHashMap<>();
        for (Child x : children(c)) if (!x.id.equals(ch.id)) all.put(x.id, childJson(x));
        all.put(ch.id, childJson(ch));
        prefs(c).edit().putString("children", MiniJson.write(all)).apply();
    }

    static synchronized void removeChild(Context c, String id) {
        Map<String, Object> all = new LinkedHashMap<>();
        for (Child x : children(c)) if (!x.id.equals(id)) all.put(x.id, childJson(x));
        prefs(c).edit().putString("children", MiniJson.write(all)).apply();
    }

    private static Map<String, Object> childJson(Child ch) {
        List<Object> apps = new ArrayList<>();
        for (String[] a : ch.apps) apps.add(MiniJson.obj("p", a[0], "l", a[1]));
        return MiniJson.obj("name", ch.name, "model", ch.model, "reason", ch.reason, "lastSeen", ch.lastSeen, "until", ch.until,
                "battery", ch.battery, "screen", ch.screen, "locked", ch.locked, "applied", ch.applied, "protection", ch.protection,
                "policy", ch.policy.toJson(), "apps", apps);
    }

    /** The parents change the rules of a child: saved here and sent to the child's phone. */
    static void setPolicy(Context c, String childId, FamilyPolicy p) {
        Child ch = child(c, childId);
        if (ch == null) return;
        p.version = Math.max(System.currentTimeMillis(), p.version + 1);
        SharedPreferences pr = prefs(c);
        if (!pr.getString("pin_hash", "").isEmpty()) {  // this parent set a PIN: it is the family's PIN
            p.pinHash = pr.getString("pin_hash", "");
            p.pinSalt = pr.getString("pin_salt", "");
        }
        String myPhone = pr.getString("phone", "");
        if (!myPhone.replaceAll("[^0-9+]", "").isEmpty()) {
            boolean known = false;
            for (String[] x : p.callable) known |= x[1].replaceAll("[^0-9+]", "").equals(myPhone.replaceAll("[^0-9+]", ""));
            if (!known) p.callable.add(new String[] {pr.getString("name", ""), myPhone});
        }
        ch.policy = p;
        saveChild(c, ch);
        sendAsync(c, MiniJson.obj("type", "policy", "to", childId, "policy", p.toJson()));
        changed();
    }

    // ------------------------------------------------------------ messages

    /** Sends a message to the family (in the background; kept for later if there is no network). */
    static void sendAsync(Context c, Map<String, Object> msg) {
        Context app = c.getApplicationContext();
        new Thread(() -> send(app, msg), "nova-family-send").start();
    }

    static synchronized void send(Context c, Map<String, Object> msg) {
        if (code(c).isEmpty()) return;
        msg.put("from", me(c));
        msg.put("t", System.currentTimeMillis());
        String sealed = FamilyCrypto.seal(key(c), topic(c), MiniJson.write(msg));
        try {
            flush(c);
            FamilyRelay.publish(relay(c), topic(c), sealed);
        } catch (IOException | RuntimeException e) {
            List<String> out = outbox(c);
            out.add(sealed);
            while (out.size() > 30) out.remove(0);
            prefs(c).edit().putString("outbox", MiniJson.write(out)).apply();
        }
    }

    private static List<String> outbox(Context c) {
        List<String> out = new ArrayList<>();
        try {
            Object l = MiniJson.parse(prefs(c).getString("outbox", "[]"));
            if (l instanceof List) for (Object o : (List<?>) l) if (o instanceof String) out.add((String) o);
        } catch (IllegalArgumentException ignored) {
            // empty
        }
        return out;
    }

    /** Messages that could not be sent before. */
    static synchronized void flush(Context c) throws IOException {
        List<String> out = outbox(c);
        while (!out.isEmpty()) {
            FamilyRelay.publish(relay(c), topic(c), out.get(0));
            out.remove(0);
            prefs(c).edit().putString("outbox", MiniJson.write(out)).apply();
        }
    }

    /** All new messages since the last one (parents' phone in the background, or as fallback). */
    static void poll(Context c) throws IOException {
        if (code(c).isEmpty()) return;
        flush(c);
        for (FamilyRelay.Message m : FamilyRelay.poll(relay(c), topic(c), since(c))) received(c, m);
    }

    static String since(Context c) {
        return prefs(c).getString("since", "12h");
    }

    static void stream(Context c) throws IOException {
        FamilyRelay.stream(relay(c), topic(c), since(c), m -> received(c, m));
    }

    static synchronized void received(Context c, FamilyRelay.Message m) {
        if (!m.id.isEmpty()) prefs(c).edit().putString("since", m.id).apply();
        String plain = FamilyCrypto.open(key(c), topic(c), m.text);
        if (plain == null) return;  // not from this family
        Object msg;
        try {
            msg = MiniJson.parse(plain);
        } catch (IllegalArgumentException e) {
            return;
        }
        String from = str(msg, "from"), type = str(msg, "type"), to = str(msg, "to");
        long t = num(msg, "t");
        if (from.isEmpty() || from.equals(me(c))) return;
        // replays: only newer messages of each phone, not older than two days
        long now = System.currentTimeMillis();
        if (t < now - 48L * 3600 * 1000 || t > now + 10L * 60 * 1000) return;
        String lastKey = "last_" + from;
        if (t <= prefs(c).getLong(lastKey, 0)) return;
        prefs(c).edit().putLong(lastKey, t).apply();
        if (!to.isEmpty() && !to.equals("*") && !to.equals(me(c))) return;
        if (child(c)) childReceived(c, type, msg);
        else if (parent(c)) parentReceived(c, from, type, msg, t);
        changed();
    }

    private static void childReceived(Context c, String type, Object msg) {
        switch (type) {
            case "policy": {
                FamilyPolicy p = FamilyPolicy.fromJson(MiniJson.get(msg, "policy"));
                if (p.version > policy(c).version) prefs(c).edit().putString("policy", MiniJson.write(p.toJson())).apply();
                FamilyService.refresh(c);
                sendStatus(c, false);
                break;
            }
            case "release":
                leave(c);
                break;
            case "ping":
                sendStatus(c, Boolean.TRUE.equals(MiniJson.get(msg, "apps")));
                break;
            case "answer":
                notifyChild(c, str(msg, "text"));
                break;
            default:
                break;
        }
    }

    private static void parentReceived(Context c, String from, String type, Object msg, long t) {
        Child ch = child(c, from);
        switch (type) {
            case "join": {
                boolean isNew = ch == null;
                if (ch == null) {
                    ch = new Child();
                    ch.id = from;
                }
                ch.name = str(msg, "name");
                ch.model = str(msg, "model");
                ch.lastSeen = t;
                saveChild(c, ch);
                if (isNew) {
                    FamilyPolicy p = new FamilyPolicy();
                    setPolicy(c, from, p);  // the PIN and the parents' number reach the child
                    notifyParent(c, "join" + from, c.getString(R.string.family_joined_title), c.getString(R.string.family_joined_text, ch.name), null);
                }
                break;
            }
            case "status": {
                if (ch == null) {
                    ch = new Child();
                    ch.id = from;
                }
                ch.name = str(msg, "name");
                ch.model = str(msg, "model");
                ch.lastSeen = t;
                ch.battery = (int) num(msg, "battery", -1);
                ch.screen = (int) num(msg, "screen");
                ch.locked = Boolean.TRUE.equals(MiniJson.get(msg, "locked"));
                ch.reason = str(msg, "reason");
                ch.until = num(msg, "until");
                Object pr = MiniJson.get(msg, "protection");
                ch.protection.clear();
                if (pr instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) pr).entrySet()) ch.protection.put(String.valueOf(e.getKey()), e.getValue());
                FamilyPolicy theirs = FamilyPolicy.fromJson(MiniJson.get(msg, "policy"));
                ch.applied = theirs.version;
                if (theirs.version > ch.policy.version) ch.policy = theirs;  // e.g. set by the other parent
                Object apps = MiniJson.get(msg, "apps");
                if (apps instanceof List) {
                    ch.apps.clear();
                    for (Object a : (List<?>) apps) ch.apps.add(new String[] {str(a, "p"), str(a, "l")});
                }
                boolean old = theirs.version < ch.policy.version;
                saveChild(c, ch);
                if (old) sendAsync(c, MiniJson.obj("type", "policy", "to", from, "policy", ch.policy.toJson()));
                break;
            }
            case "request": {
                String who = ch != null ? ch.name : "?";
                String kind = str(msg, "kind");
                int minutes = (int) num(msg, "minutes", 30);
                String text = kind.equals("unlock") ? c.getString(R.string.family_req_unlock, who)
                        : c.getString(R.string.family_req_time, who, minutes);
                notifyParent(c, "req" + from, c.getString(R.string.family_req_title), text, from + "|" + kind + "|" + minutes);
                break;
            }
            case "alert": {
                String who = ch != null ? ch.name : "?";
                String what = str(msg, "what");
                int res;
                switch (what) {
                    case "admin_off": res = R.string.family_alert_admin; break;
                    case "overlay_off": res = R.string.family_alert_overlay; break;
                    case "pin_unlock": res = R.string.family_alert_pin; break;
                    case "left": res = R.string.family_alert_left; break;
                    default: res = R.string.family_alert_other; break;
                }
                notifyParent(c, "alert" + from + what, c.getString(R.string.family_alert_title, who), c.getString(res, who), null);
                break;
            }
            default:
                break;
        }
    }

    /** The child's phone tells the parents how it is (and its rules, so a second parent learns them). */
    static void sendStatus(Context c, boolean withApps) {
        if (!child(c)) return;
        FamilyPolicy.State s = state(c);
        BatteryManager bm = c.getSystemService(BatteryManager.class);
        int battery = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Map<String, Object> msg = MiniJson.obj("type", "status", "name", prefs(c).getString("name", ""),
                "model", Build.MANUFACTURER + " " + Build.MODEL, "battery", battery, "screen", screenMinutes(c),
                "locked", s.locked, "reason", s.reason, "until", s.until, "protection", FamilyService.protection(c),
                "policy", policy(c).toJson());
        if (withApps) {
            List<Object> apps = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (App a : Apps.load(c, true)) {
                if (a.packageName().equals(c.getPackageName()) || !seen.add(a.packageName())) continue;
                apps.add(MiniJson.obj("p", a.packageName(), "l", a.label.length() > 24 ? a.label.substring(0, 24) : a.label));
                if (apps.size() >= 50) break;  // relay messages stay small
            }
            msg.put("apps", apps);
        }
        send(c, msg);
    }

    // ------------------------------------------------------------ notifications

    static final String CHANNEL = "family";

    private static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.family_channel), NotificationManager.IMPORTANCE_HIGH));
        }
    }

    private static boolean mayNotify(Context c) {
        return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    /** request: "childId|kind|minutes" adds the buttons "Erlauben" / "Ablehnen". */
    @SuppressWarnings("deprecation")
    private static void notifyParent(Context c, String tag, String title, String text, String request) {
        if (!mayNotify(c)) return;
        channel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        PendingIntent open = PendingIntent.getActivity(c, tag.hashCode(), new Intent(c, FamilyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_family).setColor(c.getColor(R.color.nova_violet)).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        if (request != null) {
            Intent yes = new Intent(c, Answer.class).putExtra("request", request).putExtra("ok", true).putExtra("tag", tag);
            Intent no = new Intent(c, Answer.class).putExtra("request", request).putExtra("ok", false).putExtra("tag", tag);
            b.addAction(new Notification.Action.Builder(null, c.getString(R.string.family_allow),
                    PendingIntent.getBroadcast(c, (tag + "y").hashCode(), yes, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
            b.addAction(new Notification.Action.Builder(null, c.getString(R.string.family_deny),
                    PendingIntent.getBroadcast(c, (tag + "n").hashCode(), no, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
        }
        nm.notify(tag.hashCode(), b.build());
    }

    @SuppressWarnings("deprecation")
    private static void notifyChild(Context c, String text) {
        if (!mayNotify(c) || text.isEmpty()) return;
        channel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_family).setColor(c.getColor(R.color.nova_violet)).setContentTitle(c.getString(R.string.app_family))
                .setContentText(text).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        nm.notify(7801, b.build());
    }

    /** The parents answer a request from the notification ("Erlauben" / "Ablehnen"). */
    static void answer(Context c, String childId, String kind, int minutes, boolean ok) {
        Child ch = child(c, childId);
        if (ch == null) return;
        if (ok) {
            FamilyPolicy p = ch.policy;
            long now = System.currentTimeMillis();
            if (kind.equals("unlock")) {
                p.lockUntil = 0;
                p.freeUntil = now + 60L * 60 * 1000;
            } else {
                String today = FamilyPolicy.day(now, TimeZone.getDefault());
                // locked by hand or bedtime: free for these minutes; screen time used up: more screen time
                FamilyPolicy.State st = p.evaluate(now, TimeZone.getDefault(), ch.screen, 0);
                if (st.locked && !"limit".equals(st.reason)) {
                    p.freeUntil = Math.max(p.freeUntil, now) + minutes * 60L * 1000;
                } else {
                    p.bonusMinutes = today.equals(p.bonusDay) ? p.bonusMinutes + minutes : minutes;
                    p.bonusDay = today;
                }
            }
            setPolicy(c, childId, p);
        }
        sendAsync(c, MiniJson.obj("type", "answer", "to", childId, "ok", ok,
                "text", c.getString(ok ? R.string.family_answer_yes : R.string.family_answer_no)));
    }

    public static class Answer extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            String[] r = String.valueOf(i.getStringExtra("request")).split("\\|");
            if (r.length == 3) {
                int minutes;
                try {
                    minutes = Integer.parseInt(r[2]);
                } catch (NumberFormatException e) {
                    minutes = 30;
                }
                answer(c, r[0], r[1], minutes, i.getBooleanExtra("ok", false));
            }
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(String.valueOf(i.getStringExtra("tag")).hashCode());
        }
    }

    private static String str(Object o, String k) {
        Object v = MiniJson.get(o, k);
        return v instanceof String ? (String) v : "";
    }

    private static long num(Object o, String k) {
        return num(o, k, 0);
    }

    private static long num(Object o, String k, long fallback) {
        Object v = MiniJson.get(o, k);
        return v instanceof Double ? ((Double) v).longValue() : fallback;
    }

    // ------------------------------------------------------------ background

    /** Parents: new statuses and requests every 15 minutes; children: keeps the protection running. */
    public static class FamilyJob extends JobService {
        private static final int ID = 4714;

        static void schedule(Context c) {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null || js.getPendingJob(ID) != null) return;
            try {
                js.schedule(new JobInfo.Builder(ID, new ComponentName(c, FamilyJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
            } catch (RuntimeException ignored) {
                // the app polls when it is open
            }
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            Context c = getApplicationContext();
            new Thread(() -> {
                try {
                    if (child(c)) {
                        FamilyService.start(c);
                        sendStatus(c, false);
                    } else if (parent(c)) {
                        poll(c);
                    }
                } catch (IOException | RuntimeException ignored) {
                    // next time
                }
                jobFinished(params, false);
            }, "nova-family-job").start();
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return true;
        }
    }
}
