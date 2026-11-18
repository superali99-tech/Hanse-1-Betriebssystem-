package org.novaos.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the installed apps from the package manager (TV apps first, then other apps). */
final class AppRepository {
    private static volatile List<AppEntry> cache = Collections.emptyList();

    private AppRepository() {}

    static List<AppEntry> cached() {
        return cache;
    }

    static List<AppEntry> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        collect(c, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, byPackage);
        collect(c, pm, Intent.CATEGORY_LAUNCHER, false, byPackage);
        List<AppEntry> list = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        list.sort((a, b) -> collator.compare(a.label, b.label));
        cache = Collections.unmodifiableList(list);
        return cache;
    }

    @SuppressWarnings("deprecation")
    private static void collect(Context c, PackageManager pm, String category, boolean leanback,
                                Map<String, AppEntry> out) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName.equals(c.getPackageName()) || out.containsKey(ai.packageName)) {
                continue;  // one tile per app; NovaOS TV itself is not listed
            }
            Drawable banner = null;
            if (leanback) {
                banner = ai.loadBanner(pm);
                if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            }
            Drawable icon = ri.loadIcon(pm);
            boolean system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            out.put(ai.packageName, new AppEntry(String.valueOf(ri.loadLabel(pm)),
                    new ComponentName(ai.packageName, ai.name), leanback, banner, icon, system));
        }
    }

    static AppEntry find(List<AppEntry> apps, String key) {
        for (AppEntry e : apps) if (e.key().equals(key)) return e;
        return null;
    }
}
