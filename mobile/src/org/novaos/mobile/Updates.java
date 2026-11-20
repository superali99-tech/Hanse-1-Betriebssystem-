package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.IOException;

/**
 * Checks for a newer NovaOS Mobile (system software update): the GitHub release
 * "mobile-update" of the (public) repository, or the public package in GitHub's container
 * registry (see {@link Registry}). No website is needed.
 * The last answer is remembered, so the home screen can show "update available"
 * without asking the server every time (at most every 6 hours).
 */
final class Updates {
    private static final String PREFS = "novaos_update";
    private static final long INTERVAL_MS = 15 * 60 * 1000L;  // home screen: at most every 15 minutes
    static final String TAG = "latest";

    /** Result of a check. */
    static final int OK = 0, NOT_PUBLIC = 1, UNREACHABLE = 2, INVALID = 3;

    private Updates() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Registry registry(Context c) {
        return new Registry(c.getString(R.string.update_registry), c.getString(R.string.update_package));
    }

    /** "Bearer ..." for anonymous reading; HttpException 401/403 if the package is not public. */
    static String auth(Registry r) throws IOException {
        String token = Registry.token(Download.text(r.tokenUrl()));
        if (token == null) throw new IOException("no token");
        return "Bearer " + token;
    }

    @SuppressWarnings("deprecation")
    static long installedCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    static String releaseAsset(Context c, String file) {
        return Registry.releaseAsset(c.getString(R.string.update_repo), c.getString(R.string.update_release), file);
    }

    /**
     * Asks for the newest version (blocking; call in the background): first the GitHub
     * release "mobile-update" (public repository), then the ghcr.io package. The result
     * is remembered; see {@link #status(Context)}.
     */
    static UpdateInfo check(Context c) {
        int status;
        String source = "";
        UpdateInfo u = null;
        String text = "";
        int releaseStatus;
        try {
            text = Download.text(releaseAsset(c, "update.properties"));
            u = UpdateInfo.parse(text);
            releaseStatus = u != null ? OK : INVALID;
        } catch (Download.HttpException e) {
            releaseStatus = e.code == 401 || e.code == 403 || e.code == 404 ? NOT_PUBLIC : UNREACHABLE;
        } catch (IOException | RuntimeException e) {
            releaseStatus = UNREACHABLE;
        }
        if (releaseStatus == OK) {
            status = OK;
            source = "release";
        } else {
            status = checkRegistry(c);
            if (status == OK) {
                source = "registry";
                text = prefs(c).getString("registry_text", "");
                u = UpdateInfo.parse(text);
            } else {
                u = null;
                text = "";
                // "not public" only if neither channel answered at all
                if (status == NOT_PUBLIC && releaseStatus == UNREACHABLE) status = UNREACHABLE;
            }
        }
        SharedPreferences.Editor ed = prefs(c).edit().putInt("status", status).putString("source", source);
        if (status != UNREACHABLE) ed.putLong("checked", System.currentTimeMillis()).putString("last", text);
        ed.apply();
        return u;
    }

    /** The ghcr.io package: OK (description in "registry_text"), NOT_PUBLIC, UNREACHABLE or INVALID. */
    private static int checkRegistry(Context c) {
        try {
            Registry r = registry(c);
            String auth = auth(r);
            String manifest = Download.text(r.manifestUrl(TAG), auth, Registry.MANIFEST_TYPE);
            String props = Registry.digest(manifest, "update.properties");
            if (props == null) return INVALID;
            String text = Download.text(r.blobUrl(props), auth, null);
            UpdateInfo u = UpdateInfo.parse(text);
            String apk = u != null ? Registry.digest(manifest, u.apk) : null;
            // the file in the package must be exactly the described APK
            if (u == null || apk == null || !Registry.hex(apk).equals(u.sha256)) return INVALID;
            prefs(c).edit().putString("registry_text", text).apply();
            return OK;
        } catch (Download.HttpException e) {
            return e.code == 401 || e.code == 403 || e.code == 404 ? NOT_PUBLIC : UNREACHABLE;
        } catch (IOException | RuntimeException e) {
            return UNREACHABLE;
        }
    }

    /**
     * Background check (UpdateJob, home screen): looks for a newer version, downloads and
     * checks it when the phone is not on mobile data, and shows the notification.
     */
    static UpdateInfo checkAndPrepare(Context c) {
        UpdateInfo u = check(c);
        if (u == null || !u.newerThan(installedCode(c))) return null;
        boolean ready = prepare(c, u) != null;
        try {
            UpdateNotice.show(c, u, ready);
        } catch (RuntimeException ignored) {
            // no notification: the home screen and the settings still show the update
        }
        return u;
    }

    static File apkFile(Context c, UpdateInfo u) {
        return new File(new File(c.getCacheDir(), "apk"), "novaos-update-" + u.versionCode + ".apk");
    }

    /** The already downloaded and checked APK of this update, or null. */
    static File readyApk(Context c, UpdateInfo u) {
        File apk = apkFile(c, u);
        return apk.isFile() && u.sha256.equals(prefs(c).getString("ready_sha", "")) ? apk : null;
    }

    /** Downloads and checks the update (only without mobile data). The checked APK, or null. */
    static File prepare(Context c, UpdateInfo u) {
        File ready = readyApk(c, u);
        if (ready != null) return ready;
        android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
        if (cm == null || cm.isActiveNetworkMetered()) return null;
        File apk = apkFile(c, u);
        //noinspection ResultOfMethodCallIgnored
        apk.getParentFile().mkdirs();
        try {
            String sha = download(c, u, apk, null);
            if (sha.equals(u.sha256) && Installer.sameSigner(c, apk)) {
                prefs(c).edit().putString("ready_sha", u.sha256).apply();
                return apk;
            }
        } catch (IOException | RuntimeException ignored) {
            // the update page downloads it again
        }
        //noinspection ResultOfMethodCallIgnored
        apk.delete();
        return null;
    }

    /** Result of the last check: OK, NOT_PUBLIC, UNREACHABLE or INVALID. */
    static int status(Context c) {
        return prefs(c).getInt("status", UNREACHABLE);
    }

    /** Downloads the APK of the update (blocking) and returns its SHA-256 (hex); the caller compares it. */
    static String download(Context c, UpdateInfo u, File target, Download.Progress progress) throws IOException {
        if ("release".equals(prefs(c).getString("source", ""))) {
            return Download.file(releaseAsset(c, u.apk), target, progress);
        }
        Registry r = registry(c);
        String auth = auth(r);
        String apk = Registry.digest(Download.text(r.manifestUrl(TAG), auth, Registry.MANIFEST_TYPE), u.apk);
        if (apk == null || !Registry.hex(apk).equals(u.sha256)) throw new IOException("update changed, check again");
        return Download.file(r.blobUrl(apk), auth, target, progress);
    }

    /** The remembered answer, if it offers a newer version than the installed one. */
    static UpdateInfo pending(Context c) {
        UpdateInfo u = UpdateInfo.parse(prefs(c).getString("last", ""));
        return u != null && u.newerThan(installedCode(c)) ? u : null;
    }

    static boolean due(Context c) {
        long last = prefs(c).getLong("checked", 0);
        return System.currentTimeMillis() - last > INTERVAL_MS || System.currentTimeMillis() < last;
    }

    /** true once after an update was installed (or on the very first start): play the start animation. */
    static boolean justUpdated(Context c) {
        long code = installedCode(c);
        long seen = prefs(c).getLong("seen", 0);
        return seen != code;
    }

    static void markSeen(Context c) {
        prefs(c).edit().putLong("seen", installedCode(c)).apply();
    }

    static boolean firstStart(Context c) {
        return prefs(c).getLong("seen", 0) == 0;
    }
}
