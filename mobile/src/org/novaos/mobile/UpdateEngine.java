package org.novaos.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Runs the NovaOS software update in the background - independent of the update page, so
 * turning the phone, leaving the page or using other apps does not stop it (like on Samsung
 * phones: "Du kannst dein Telefon während des Updates ruhig benutzen"). Two parts: download
 * (real bytes, can be paused and continued) and confirmation (check the file and the NovaOS
 * key, prepare, install). A notification shows the progress.
 */
final class UpdateEngine {
    interface Listener {
        void updateChanged();
    }

    static final int IDLE = 0, DOWNLOAD = 1, VERIFY = 2, PREPARE = 3, INSTALL = 4;
    /** How long the stages take at least (the download as long as it really needs). */
    private static final long DOWNLOAD_MS = 8_000, PRELOADED_MS = 3_000, VERIFY_MS = 30_000, PREPARE_MS = 70_000, INSTALL_MS = 4_000;
    /** A Nova app from the Nova Store: no long system-update stages, no start animation afterwards. */
    private boolean quick;

    private long dlMs() {
        return quick ? 1_500 : DOWNLOAD_MS;
    }

    private long preMs() {
        return quick ? 500 : PRELOADED_MS;
    }

    private long vfMs() {
        return quick ? 1_500 : VERIFY_MS;
    }

    private long prMs() {
        return quick ? 1_000 : PREPARE_MS;
    }

    boolean quick() {
        return quick;
    }
    private static final String CHANNEL = "update_progress";
    private static final int NOTIFICATION = 2;
    private static UpdateEngine instance;

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile UpdateInfo update;
    private volatile int stage = IDLE;
    private volatile boolean paused, preloaded;
    private volatile long bytes, total = -1, stageStart, pausedAt, pausedFor;
    private volatile String error = "";
    private volatile double bytesPerSecond;
    private long lastBytes, lastBytesAt, lastNotified;

    private static final class Paused extends RuntimeException {}

    static synchronized UpdateEngine get(Context c) {
        if (instance == null) instance = new UpdateEngine(c.getApplicationContext());
        return instance;
    }

    private UpdateEngine(Context c) {
        app = c;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        ui.post(() -> {
            for (Listener l : listeners) l.updateChanged();
            notifyProgress(true);
        });
    }

    // ------------------------------------------------------------ state for the page

    boolean running() {
        return stage != IDLE;
    }

    int stage() {
        return stage;
    }

    boolean paused() {
        return paused;
    }

    UpdateInfo update() {
        return update;
    }

    String error() {
        return error;
    }

    boolean preloaded() {
        return preloaded;
    }

    long bytes() {
        return stage > DOWNLOAD ? sizeOf() : bytes;
    }

    long size() {
        return sizeOf();
    }

    private long sizeOf() {
        UpdateInfo u = update;
        return total > 0 ? total : u != null ? u.size : 0;
    }

    double speed() {
        return paused || stage != DOWNLOAD || preloaded ? 0 : bytesPerSecond;
    }

    /** Time of this stage without the pauses. */
    private long active() {
        long now = System.currentTimeMillis();
        return Math.max(0, now - stageStart - pausedFor - (paused ? now - pausedAt : 0));
    }

    /** First part of the bar: the download, 0..1 (never ahead of the bytes really loaded). */
    float download() {
        if (stage == IDLE) return 0;
        if (stage > DOWNLOAD) return 1;
        long size = sizeOf();
        float real = preloaded ? 1f : size > 0 ? Math.min(1f, bytes / (float) size) : 0f;
        return Math.min(real, active() / (float) (preloaded ? preMs() : dlMs()));
    }

    /** Second part: confirmation (check, prepare, install), 0..1. */
    float confirmation() {
        if (stage <= DOWNLOAD) return 0;
        long before = stage == VERIFY ? 0 : stage == PREPARE ? vfMs() : vfMs() + prMs();
        long span = stage == VERIFY ? vfMs() : stage == PREPARE ? prMs() : INSTALL_MS;
        float f = (before + Math.min(active(), span)) / (float) (vfMs() + prMs() + INSTALL_MS);
        return Math.min(stage == INSTALL ? 0.99f : 0.98f, f);
    }

    /** About how long it still takes (ms). */
    long left() {
        long rest = vfMs() + prMs() + INSTALL_MS;
        if (stage == DOWNLOAD) {
            long size = sizeOf();
            long download = bytesPerSecond > 0 && size > bytes ? (long) ((size - bytes) * 1000 / bytesPerSecond) : 0;
            return Math.max(download, (preloaded ? preMs() : dlMs()) - active()) + rest;
        }
        if (stage == VERIFY) return vfMs() - Math.min(active(), vfMs()) + prMs() + INSTALL_MS;
        if (stage == PREPARE) return prMs() - Math.min(active(), prMs()) + INSTALL_MS;
        if (stage == INSTALL) return Math.max(0, INSTALL_MS - active());
        return 0;
    }

    // ------------------------------------------------------------ control

    void start(UpdateInfo u) {
        start(u, false);
    }

    /** quick: the update of a Nova app in the Nova Store - fast, and NovaOS starts again quietly. */
    void start(UpdateInfo u, boolean quick) {
        if (running() || u == null) return;
        this.quick = quick;
        Updates.prefs(app).edit().putBoolean("quiet", quick).apply();
        update = u;
        error = "";
        paused = false;
        preloaded = false;
        bytes = 0;
        total = -1;
        bytesPerSecond = 0;
        lastBytesAt = 0;
        setStage(DOWNLOAD);
        new Thread(this::work, "novaos-update").start();
        ui.post(ticker);
    }

    /** Keeps the notification current while the update runs (also without the page). */
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            notifyProgress(false);
            if (running()) ui.postDelayed(this, 1000);
        }
    };

    void pause() {
        if (!running() || paused || stage == INSTALL) return;
        pausedAt = System.currentTimeMillis();
        paused = true;
        changed();
    }

    void resume() {
        if (!paused) return;
        pausedFor += System.currentTimeMillis() - pausedAt;
        paused = false;
        bytesPerSecond = 0;
        lastBytesAt = 0;
        synchronized (this) {
            notifyAll();
        }
        changed();
    }

    private void setStage(int s) {
        stageStart = System.currentTimeMillis();
        pausedFor = 0;
        stage = s;
        changed();
    }

    /** Waits until the stage had its time; pauses do not count. */
    private void hold(long ms) throws InterruptedException {
        while (active() < ms || paused) {
            if (paused) {
                synchronized (this) {
                    if (paused) wait(1000);
                }
            } else {
                Thread.sleep(Math.min(250, ms - active() + 1));
            }
        }
    }

    private void work() {
        UpdateInfo u = update;
        File apk = Updates.apkFile(app, u);
        //noinspection ResultOfMethodCallIgnored
        apk.getParentFile().mkdirs();
        try {
            // 1. download (or the copy already downloaded in the background); pausing keeps the part
            String sha;
            File ready = Updates.readyApk(app, u);
            if (ready != null) {
                preloaded = true;
                bytes = total = ready.length();
                sha = Download.sha256(ready);
            } else {
                while (true) {
                    try {
                        sha = Updates.downloadResumable(app, u, apk, (done, all) -> {
                            if (paused) throw new Paused();
                            bytes = done;
                            total = all;
                            measure(done);
                        });
                        break;
                    } catch (Paused p) {
                        hold(0);  // until "Fortsetzen"
                    }
                }
            }
            hold(preloaded ? preMs() : dlMs());
            // 2. confirmation: exactly the described file, signed with the NovaOS key
            setStage(VERIFY);
            if (!sha.equals(u.sha256)) {
                fail(app.getString(R.string.update_bad_hash), apk);
                return;
            }
            if (!Installer.sameSigner(app, apk)) {
                fail(app.getString(R.string.update_other_key), apk);
                return;
            }
            hold(vfMs());
            // 3. prepare: tidy up old preview pictures, the app list for the restart
            setStage(PREPARE);
            File[] thumbs = new File(app.getCacheDir(), "thumbs").listFiles();
            long old = System.currentTimeMillis() - 14L * 24 * 3600 * 1000;
            if (thumbs != null) for (File t : thumbs) if (t.lastModified() < old) //noinspection ResultOfMethodCallIgnored
                t.delete();
            Apps.load(app);
            hold(prMs());
            // 4. install: Android asks once, then NovaOS restarts
            setStage(INSTALL);
            Installer.install(app, apk, app.getPackageName());
        } catch (Exception e) {
            fail(app.getString(R.string.update_failed, String.valueOf(e.getMessage())), apk);
        }
    }

    private void measure(long done) {
        long now = System.currentTimeMillis();
        if (now - lastBytesAt < 1000) return;
        if (lastBytesAt > 0) bytesPerSecond = Math.max(0, done - lastBytes) * 1000.0 / (now - lastBytesAt);
        lastBytes = done;
        lastBytesAt = now;
    }

    private void fail(String message, File apk) {
        //noinspection ResultOfMethodCallIgnored
        apk.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(apk.getPath() + ".part").delete();
        error = message;
        paused = false;
        stage = IDLE;
        changed();
    }

    /** Result of Android's installer (Installer.report). Success: Android restarts NovaOS. */
    static void result(String pkg, boolean ok, String message) {
        UpdateEngine e = instance;
        if (e == null || e.stage != INSTALL || !e.app.getPackageName().equals(pkg)) return;
        if (!ok) {
            e.error = e.app.getString(R.string.update_failed, message == null ? "?" : message);
            e.stage = IDLE;
            e.changed();
        }
    }

    // ------------------------------------------------------------ notification with the progress

    private void notifyProgress(boolean force) {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (!running()) {
            nm.cancel(NOTIFICATION);
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastNotified < 1000) return;
        lastNotified = now;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, app.getString(R.string.update_title), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(app, CHANNEL) : new Notification.Builder(app);
        boolean downloading = stage == DOWNLOAD;
        int pct = (int) ((downloading ? download() : confirmation()) * 100);
        b.setSmallIcon(R.drawable.ic_refresh)
                .setColor(app.getColor(R.color.nova_violet))
                .setContentTitle(app.getString(paused ? R.string.update_paused_title : R.string.update_running_title))
                .setContentText(app.getString(downloading ? R.string.update_part_download : R.string.update_part_confirm) + " · " + pct + " %")
                .setProgress(100, pct, false)
                .setOngoing(!paused)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(PendingIntent.getActivity(app, 2, new Intent(app, UpdateActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            nm.notify(NOTIFICATION, b.build());
        } catch (SecurityException ignored) {
            // Android 13+ without permission for notifications
        }
    }

}
