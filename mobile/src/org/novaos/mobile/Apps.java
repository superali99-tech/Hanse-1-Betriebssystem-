package org.novaos.mobile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The apps of the phone (start entries), sorted by name; kept in memory. */
final class Apps {
    private static volatile List<App> cache = Collections.emptyList();

    private Apps() {}

    static List<App> cached() {
        return cache;
    }

    /** Apps for the home screen (on a child's phone without the apps the parents blocked). */
    static List<App> load(Context c) {
        return load(c, false);
    }

    static List<App> load(Context c, boolean all) {
        PackageManager pm = c.getPackageManager();
        List<String> blocked = all ? new ArrayList<>() : Family.blocked(c);
        List<App> list = new ArrayList<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            ActivityInfo ai = ri.activityInfo;
            // NovaOS's own apps (Store, Camera, ...) are listed, the home screen itself is not
            if (ai == null || ai.name.equals(HomeActivity.class.getName()) || blocked.contains(ai.packageName)) continue;
            boolean system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            list.add(new App(String.valueOf(ri.loadLabel(pm)), new ComponentName(ai.packageName, ai.name),
                    ri.loadIcon(pm), system));
        }
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        list.sort((a, b) -> collator.compare(a.label, b.label));
        List<App> result = Collections.unmodifiableList(list);
        if (!all) cache = result;
        return result;
    }

    static App find(List<App> apps, String key) {
        for (App a : apps) if (a.key().equals(key)) return a;
        return null;
    }

    /** First start entry of an app package (e.g. the phone app for the dock). */
    static App byPackage(List<App> apps, String pkg) {
        if (pkg == null) return null;
        for (App a : apps) if (a.packageName().equals(pkg)) return a;
        return null;
    }

    /** One of NovaOS's own apps (Nova Kamera, ...), if listed. */
    static App own(List<App> apps, Class<?> activity) {
        for (App a : apps) if (a.component.getClassName().equals(activity.getName())) return a;
        return null;
    }

    /** The app that handles an intent by default (dialer, messages, camera, browser). */
    static App defaultFor(Context c, List<App> apps, Intent intent) {
        PackageManager pm = c.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        String pkg = ri != null && ri.activityInfo != null ? ri.activityInfo.packageName : null;
        App a = "android".equals(pkg) ? null : byPackage(apps, pkg);  // "android" = chooser
        if (a != null) return a;
        for (ResolveInfo r : pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            a = byPackage(apps, r.activityInfo.packageName);
            if (a != null) return a;
        }
        return null;
    }
}
