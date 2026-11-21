package org.novaos.mobile;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Neuerungen" of an app in the Nova Store: for a Nova app the lines of its NovaOS update,
 * for an F-Droid app what the developer wrote for the version. Shown (with the blue dot) for
 * 3 hours after the app was updated on this phone and while an update for it is waiting;
 * "Neuerungen anzeigen" in the ⋮ menu switches it off.
 */
final class AppNews {
    static final class Item {
        boolean upcoming;     // the update is still waiting
        String version = "";
        long date;            // installed / released (0 = unknown)
        List<String> lines = new ArrayList<>();
        String link = "";     // full list of changes (F-Droid apps)
    }

    private AppNews() {}

    static boolean enabled(Context c) {
        return c.getSharedPreferences("store", Context.MODE_PRIVATE).getBoolean("news", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("store", Context.MODE_PRIVATE).edit().putBoolean("news", on).apply();
    }

    private static boolean german() {
        return Locale.getDefault().getLanguage().equals("de");
    }

    /** News of a Nova app, or null (nothing new, more than 3 hours ago, or switched off). */
    static Item nova(Context c, String key) {
        if (!enabled(c)) return null;
        boolean de = german();
        UpdateInfo pending = Updates.pending(c);
        if (pending != null && pending.apps.contains(key)) {
            Item i = new Item();
            i.upcoming = true;
            i.version = pending.label();
            i.lines = pending.appNotes(key, de);
            return i.lines.isEmpty() ? null : i;
        }
        List<String> lines = News.forApp(StoreCatalog.raw(c, R.raw.whatsnew), key, de);
        long at = Updates.installedAt(c);
        if (lines.isEmpty() || !News.show(true, at, System.currentTimeMillis(), false)) return null;
        Item i = new Item();
        i.version = Updates.displayName(c);
        i.date = at;
        i.lines = lines;
        return i;
    }

    /** News of an F-Droid app (installed: 3 hours after its update, or when a newer version waits). */
    @SuppressWarnings("deprecation")
    static Item fdroid(Context c, StoreApp a, long latestCode, String latestName) {
        if (!enabled(c) || !a.fromFdroid()) return null;
        PackageInfo pi;
        try {
            pi = c.getPackageManager().getPackageInfo(a.pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;  // not installed: nothing "new" for this phone
        }
        long installed = android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        boolean upcoming = latestCode > installed;
        if (!News.show(true, pi.lastUpdateTime, System.currentTimeMillis(), upcoming)) return null;
        StoreMeta m = StoreCatalog.meta(c, a.pkg);
        Item i = new Item();
        i.upcoming = upcoming;
        i.version = upcoming ? latestName : pi.versionName == null ? "" : pi.versionName;
        i.date = upcoming ? (m != null ? m.newsDate : 0) : pi.lastUpdateTime;
        String text = m != null && (m.code == installed || m.code == latestCode) ? m.news(german()) : "";
        if (!text.isEmpty()) {
            for (String line : text.split("\n")) if (!line.trim().isEmpty()) i.lines.add(line.trim().replaceFirst("^[•*∙-]\\s*", ""));
        } else {
            i.lines.add(c.getString(R.string.news_no_text, i.version));
        }
        if (m != null) i.link = m.changelog;
        return i;
    }

    /** Is the app updated (or waiting for an update) - for the blue dot in the list? */
    static boolean dot(Context c, StoreApp a, long latestCode) {
        return fdroid(c, a, latestCode, "") != null;
    }
}
