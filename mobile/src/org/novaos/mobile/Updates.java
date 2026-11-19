package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Checks the NovaOS website for a newer NovaOS Mobile (system software update).
 * The last answer is remembered, so the home screen can show "update available"
 * without asking the server every time (at most every 6 hours).
 */
final class Updates {
    private static final String PREFS = "novaos_update";
    private static final long INTERVAL_MS = 6 * 60 * 60 * 1000L;

    private Updates() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String baseUrl(Context c) {
        String u = c.getString(R.string.update_url);
        return u.endsWith("/") ? u : u + "/";
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

    /** Asks the server (blocking; call in the background). null = not reachable. */
    static UpdateInfo check(Context c) {
        try {
            String text = Download.text(baseUrl(c) + "update.properties");
            UpdateInfo u = UpdateInfo.parse(text);
            prefs(c).edit()
                    .putLong("checked", System.currentTimeMillis())
                    .putString("last", u != null ? text : "")
                    .apply();
            return u;
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
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
