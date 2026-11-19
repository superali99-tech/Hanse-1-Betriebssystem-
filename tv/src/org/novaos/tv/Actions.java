package org.novaos.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
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

    /** YouTube apps that play a video link directly (TV app first). */
    private static final String[] YOUTUBE = {
            "com.google.android.youtube.tv", "com.google.android.youtube", "com.amazon.firetv.youtube"};

    /** Opens a YouTube video or channel in the YouTube app of the TV (Nova Filme). */
    static void youtube(Activity a, String url) {
        Uri uri = Uri.parse(url);
        for (String pkg : YOUTUBE) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // not installed: try the next one
            }
        }
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(a, R.string.youtube_missing, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Live TV apps of TV manufacturers (antenna/cable/satellite): Google "Live Channels",
     * MediaTek (e.g. Metz, Grundig), Sony, Philips, TCL, Xiaomi, Amlogic, AOSP.
     */
    static final String[] LIVE_TV = {
            "com.mediatek.wwtv.tvcenter", "com.google.android.tv", "com.sony.dtv.tvx", "org.droidtv.playtv",
            "com.tcl.tv", "com.xiaomi.mitv.tvplayer", "com.droidlogic.tvsource", "com.android.tv"};

    static Intent liveTvIntent(Activity a) {
        PackageManager pm = a.getPackageManager();
        for (String pkg : LIVE_TV) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        // other manufacturers: an app that opens the TV channel database and is a TV app
        // (generic viewers such as the Google app also claim content:// links)
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
        for (ResolveInfo r : pm.queryIntentActivities(view, 0)) {
            String p = r.activityInfo.packageName;
            if (p.contains(".tv") || p.contains("tvcenter") || p.contains("livetv")) return view.setPackage(p);
        }
        return null;
    }

    /** Opens the TV's own live TV (tuner) app; false if this device has none. */
    static boolean liveTv(Activity a) {
        Intent i = liveTvIntent(a);
        if (i == null) return false;
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException ex) {
            return false;
        }
    }

    /** Switches the TV's live TV app to this channel (antenna/cable/satellite). */
    static void tunerChannel(Activity a, TunerChannel ch) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUri(ch.id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ex) {
            if (!liveTv(a)) Toast.makeText(a, R.string.not_available, Toast.LENGTH_LONG).show();
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
