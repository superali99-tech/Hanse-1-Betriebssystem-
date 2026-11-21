package org.novaos.mobile;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads and installs Nova Store apps from F-Droid. Shared by the store list and the
 * details page, so both show the same progress ("x % of y MB") and either can cancel.
 */
final class StoreInstall {
    interface Listener {
        /** Something changed for this app (progress, finished, failed: message != null). */
        void changed(String pkg, String message);
    }

    /** Progress of a running download: bytes done and total (-1 = unknown). */
    static final class State {
        volatile long done, total = -1, installingSince;
        volatile boolean installing, cancelled, checking;

        int percent() {
            return total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
        }
    }

    private static final Map<String, State> running = new ConcurrentHashMap<>();
    private static final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private static final ExecutorService worker = Executors.newFixedThreadPool(2);
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private StoreInstall() {}

    private static final class Cancelled extends RuntimeException {}

    static State state(String pkg) {
        return running.get(pkg);
    }

    static void addListener(Listener l) {
        listeners.add(l);
        // back from Android's installer: if it was closed without a result, the app is not "installing" any more
        long now = System.currentTimeMillis();
        for (Map.Entry<String, State> e : running.entrySet()) {
            State st = e.getValue();
            if (st.installing && now - st.installingSince > 15_000) {
                running.remove(e.getKey());
                ui.post(() -> notifyAll(e.getKey(), null));
            }
        }
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Result of Android's installer (Installer.report): finished, failed or cancelled by the user. */
    static void result(String pkg, boolean success, String message) {
        State s = running.remove(pkg);
        if (s == null) return;  // not a store installation (e.g. the NovaOS update)
        ui.post(() -> notifyAll(pkg, success ? null : message == null ? "" : message));
    }

    private static void notifyAll(String pkg, String message) {
        for (Listener l : listeners) l.changed(pkg, message);
    }

    static void cancel(String pkg) {
        State s = running.get(pkg);
        if (s != null && !s.installing) s.cancelled = true;
    }

    /** The downloaded APKs are only needed until Android has installed them. */
    static void cleanup(Context c) {
        java.io.File[] files = new java.io.File(c.getCacheDir(), "apk").listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            String pkg = f.getName().replaceFirst("\\.apk(\\.part)?$", "");
            if (!running.containsKey(pkg)) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    /** Starts the download of the F-Droid version versionCode, then Android's installer. */
    static void start(Context context, StoreApp a, long versionCode) {
        if (running.containsKey(a.pkg)) return;
        Context app = context.getApplicationContext();
        State s = new State();
        running.put(a.pkg, s);
        notifyAll(a.pkg, null);
        worker.execute(() -> {
            File dir = new File(app.getCacheDir(), "apk");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File apk = new File(dir, a.pkg + ".apk");
            long[] last = {0};
            try {
                Download.file(a.fdroidApk(versionCode), apk, (done, total) -> {
                    if (s.cancelled) throw new Cancelled();
                    s.done = done;
                    s.total = total;
                    long now = System.currentTimeMillis();
                    if (now - last[0] > 200) {  // not more often than 5 times a second
                        last[0] = now;
                        ui.post(() -> notifyAll(a.pkg, null));
                    }
                });
                if (s.cancelled) throw new Cancelled();
                s.checking = true;
                ui.post(() -> notifyAll(a.pkg, null));
                PackageInfo info = Installer.info(app, apk);
                if (info == null || !a.pkg.equals(info.packageName)) throw new IOException(app.getString(R.string.store_wrong_app));
                if (s.cancelled) throw new Cancelled();
                s.installingSince = System.currentTimeMillis();
                s.installing = true;
                ui.post(() -> notifyAll(a.pkg, null));
                Installer.install(app, apk, a.pkg);
            } catch (Cancelled e) {
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                running.remove(a.pkg);
                ui.post(() -> notifyAll(a.pkg, null));
            } catch (IOException | RuntimeException e) {
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                running.remove(a.pkg);
                String m = String.valueOf(e.getMessage());
                ui.post(() -> notifyAll(a.pkg, m));
            }
        });
    }
}
