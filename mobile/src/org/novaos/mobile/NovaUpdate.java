package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

/**
 * "Aktualisieren" for a Nova app right in the Nova Store (NovaOS 1.5): the update runs there,
 * with a ring around the icon and "Wird heruntergeladen … 45 %", like in other app stores.
 * The Nova apps live in NovaOS itself, so the update brings every Nova app it changes.
 */
final class NovaUpdate {
    private NovaUpdate() {}

    /** Starts the waiting update (key: the Nova app tapped, null = "Alle aktualisieren"). */
    static void start(Activity a, String key) {
        UpdateInfo u = Updates.pending(a);
        UpdateEngine e = UpdateEngine.get(a);
        if (u == null || e.running()) return;
        if (!Installer.allowedOrAsk(a)) {
            new AlertDialog.Builder(a).setMessage(R.string.update_allow).setPositiveButton(R.string.ok, null).show();
            return;
        }
        e.start(u);
    }

    /** Whether the update for this Nova app is running now. */
    static boolean busy(Context c, String key) {
        UpdateEngine e = UpdateEngine.get(c);
        if (!e.running()) return false;
        UpdateInfo u = e.update();
        return u != null && (key == null || u.apps.contains(key));
    }

    /** The whole way: downloading is the first half, checking and installing the second; -1 = no number. */
    static float fraction(UpdateEngine e) {
        if (e.stage() == UpdateEngine.INSTALL) return -1;
        return e.stage() == UpdateEngine.DOWNLOAD ? e.download() * 0.5f : 0.5f + e.confirmation() * 0.5f;
    }

    /** "Wird heruntergeladen … 45 %", "Wird installiert … 80 %", "Angehalten – 45 %". */
    static String status(Context c, UpdateEngine e) {
        int pct = Math.round(Math.max(0, fraction(e)) * 100);
        if (e.paused()) return c.getString(R.string.store_upd_paused, pct);
        if (e.stage() == UpdateEngine.INSTALL) return c.getString(R.string.store_upd_finish);
        if (e.stage() == UpdateEngine.DOWNLOAD) return c.getString(R.string.store_upd_download, Math.round(e.download() * 100));
        return c.getString(R.string.store_upd_install, pct);
    }
}
