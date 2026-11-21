package org.novaos.mobile;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Installs an APK with Android's package installer. Android always asks the user to
 * confirm; NovaOS needs the permission "install unknown apps" once.
 */
final class Installer {
    interface Listener {
        void onResult(String pkg, boolean success, String message);
    }

    static final String ACTION = "org.novaos.mobile.INSTALL_STATUS";
    static final String EXTRA_PKG = "pkg";
    private static volatile Listener listener;

    private Installer() {}

    static void setListener(Listener l) {
        listener = l;
    }

    static void report(String pkg, boolean success, String message) {
        StoreInstall.result(pkg, success, message);  // always, even while no store page is open
        Listener l = listener;
        if (l != null) l.onResult(pkg, success, message);
    }

    /** true if NovaOS may install apps; otherwise opens the Android setting for it. */
    static boolean allowedOrAsk(Activity a) {
        if (a.getPackageManager().canRequestPackageInstalls()) return true;
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (RuntimeException ignored) {
            // no such settings page
        }
        return false;
    }

    /** Package name of an APK file (null if it is no valid APK). */
    @SuppressWarnings("deprecation")
    static PackageInfo info(Context c, File apk) {
        return c.getPackageManager().getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
    }

    /** Whether the APK is signed with the same key as this installed NovaOS (needed for updates). */
    @SuppressWarnings("deprecation")
    static boolean sameSigner(Context c, File apk) {
        try {
            PackageInfo mine = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            PackageInfo other = info(c, apk);
            if (other == null || other.signatures == null || mine.signatures == null) return false;
            return Arrays.equals(mine.signatures, other.signatures);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static void install(Context c, File apk, String pkg) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = s.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent status = new Intent(ACTION).setClass(c, InstallReceiver.class).putExtra(EXTRA_PKG, pkg);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 /* FLAG_MUTABLE: the installer adds the result */ : 0);
            PendingIntent pending = PendingIntent.getBroadcast(c, id, status, flags);
            s.commit(pending.getIntentSender());
        }
    }
}
