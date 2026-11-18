package org.novaos.tv;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** One installed app as shown on a tile. */
final class AppEntry {
    final String label;
    final ComponentName component;
    final boolean leanback;   // has a TV launcher entry (opens with the TV interface)
    final Drawable banner;    // 16:9 TV banner, null if the app has none
    final Drawable icon;
    final boolean system;

    AppEntry(String label, ComponentName component, boolean leanback, Drawable banner, Drawable icon, boolean system) {
        this.label = label;
        this.component = component;
        this.leanback = leanback;
        this.banner = banner;
        this.icon = icon;
        this.system = system;
    }

    String key() {
        return component.flattenToShortString();
    }

    String packageName() {
        return component.getPackageName();
    }
}
