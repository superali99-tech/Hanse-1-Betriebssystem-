package org.novaos.mobile;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

/** Starting apps, Android settings pages and YouTube links. */
final class Launch {
    private Launch() {}

    static void app(Activity a, App app, View from) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            if (from != null) {
                // the app grows out of its icon, like on every phone
                a.startActivity(i, ActivityOptions.makeScaleUpAnimation(from, 0, 0,
                        from.getWidth(), from.getHeight()).toBundle());
            } else {
                a.startActivity(i);
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, R.string.launch_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Opens an Android settings page; falls back to the main settings. */
    static void settings(Activity a, String action) {
        try {
            a.startActivity(new Intent(action));
        } catch (ActivityNotFoundException e) {
            if (!Settings.ACTION_SETTINGS.equals(action)) settings(a, Settings.ACTION_SETTINGS);
            else Toast.makeText(a, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    static void appInfo(Activity a, App app) {
        withPackage(a, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app);
    }

    static void uninstall(Activity a, App app) {
        withPackage(a, Intent.ACTION_DELETE, app);
    }

    private static void withPackage(Activity a, String action, App app) {
        try {
            a.startActivity(new Intent(action, Uri.fromParts("package", app.packageName(), null)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    /** Fallback for Nova Filme videos that may not be played outside YouTube. */
    static void youtube(Activity a, String url) {
        Uri uri = Uri.parse(url);
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube"));
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(a, R.string.youtube_missing, Toast.LENGTH_LONG).show();
            }
        }
    }
}
