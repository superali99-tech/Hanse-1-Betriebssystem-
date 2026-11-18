package org.novaos.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

/** Starting apps and TV settings pages. */
final class Actions {
    private Actions() {}

    static void launch(Activity a, AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(e.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(a, R.string.launch_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Opens a settings page; falls back to the main TV settings if this TV has no such page. */
    static void settings(Activity a, String action) {
        try {
            a.startActivity(new Intent(action));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(a, R.string.not_available, Toast.LENGTH_LONG).show();
            if (!Settings.ACTION_SETTINGS.equals(action)) {
                try {
                    a.startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (ActivityNotFoundException ignored) {
                    // nothing else to try
                }
            }
        }
    }

    static void appInfo(Activity a, AppEntry e) {
        settingsWithData(a, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, e);
    }

    static void uninstall(Activity a, AppEntry e) {
        settingsWithData(a, Intent.ACTION_DELETE, e);
    }

    private static void settingsWithData(Activity a, String action, AppEntry e) {
        try {
            a.startActivity(new Intent(action, Uri.fromParts("package", e.packageName(), null)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(a, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
