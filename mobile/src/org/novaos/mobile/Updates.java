package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.IOException;

/**
 * Checks for a newer NovaOS Mobile (system software update). The update is a public
 * package in GitHub's container registry (see {@link Registry}); no website is needed.
 * The last answer is remembered, so the home screen can show "update available"
 * without asking the server every time (at most every 6 hours).
 */
final class Updates {
    private static final String PREFS = "novaos_update";
    private static final long INTERVAL_MS = 6 * 60 * 60 * 1000L;
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

    /** Asks the registry (blocking; call in the background). See {@link #status(Context)}. */
    static UpdateInfo check(Context c) {
        int status;
        UpdateInfo u = null;
        String text = "";
        try {
            Registry r = registry(c);
            String auth = auth(r);
            String manifest = Download.text(r.manifestUrl(TAG), auth, Registry.MANIFEST_TYPE);
            String props = Registry.digest(manifest, "update.properties");
            if (props == null) throw new IOException("no update.properties");
            text = Download.text(r.blobUrl(props), auth, null);
            u = UpdateInfo.parse(text);
            String apk = u != null ? Registry.digest(manifest, u.apk) : null;
            // the file in the package must be exactly the described APK
            if (u == null || apk == null || !Registry.hex(apk).equals(u.sha256)) {
                u = null;
                text = "";
                status = INVALID;
            } else {
                status = OK;
            }
        } catch (Download.HttpException e) {
            status = e.code == 401 || e.code == 403 || e.code == 404 ? NOT_PUBLIC : UNREACHABLE;
        } catch (IOException | RuntimeException e) {
            status = UNREACHABLE;
        }
        SharedPreferences.Editor ed = prefs(c).edit().putInt("status", status);
        if (status == OK || status == NOT_PUBLIC || status == INVALID) {
            ed.putLong("checked", System.currentTimeMillis()).putString("last", text);
        }
        ed.apply();
        return u;
    }

    /** Result of the last check: OK, NOT_PUBLIC, UNREACHABLE or INVALID. */
    static int status(Context c) {
        return prefs(c).getInt("status", UNREACHABLE);
    }

    /** Downloads the APK of the update (blocking) and returns its SHA-256 (hex). */
    static String download(Context c, UpdateInfo u, File target, Download.Progress progress) throws IOException {
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
