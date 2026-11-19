package org.novaos.mobile;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** An installed app with a start entry. */
final class App {
    final String label;
    final ComponentName component;
    final Drawable icon;
    final boolean system;

    App(String label, ComponentName component, Drawable icon, boolean system) {
        this.label = label;
        this.component = component;
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
