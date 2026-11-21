package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;

import org.novaos.tv.KeyList;

import java.util.ArrayList;
import java.util.List;

/** Options when an app is held: favorites, app info, uninstall. */
final class AppMenu {
    interface Changed {
        void onChanged();
    }

    private AppMenu() {}

    static void show(Activity a, App app, Changed changed) {
        show(a, app, changed, new ArrayList<>(), new ArrayList<>());
    }

    /** With extra entries first (e.g. moving the app in the app overview). */
    static void show(Activity a, App app, Changed changed, List<String> extraLabels, List<Runnable> extraActions) {
        List<String> favs = Favorites.get(a);
        boolean isFav = favs.contains(app.key());
        List<String> labels = new ArrayList<>(extraLabels);
        List<Runnable> actions = new ArrayList<>(extraActions);
        labels.add(a.getString(isFav ? R.string.menu_unpin : R.string.menu_pin));
        actions.add(() -> {
            KeyList.toggle(favs, app.key());
            Favorites.set(a, favs);
            changed.onChanged();
        });
        if (isFav && favs.size() > 1) {
            labels.add(a.getString(R.string.menu_left));
            actions.add(() -> {
                if (KeyList.move(favs, app.key(), -1)) {
                    Favorites.set(a, favs);
                    changed.onChanged();
                }
            });
            labels.add(a.getString(R.string.menu_right));
            actions.add(() -> {
                if (KeyList.move(favs, app.key(), 1)) {
                    Favorites.set(a, favs);
                    changed.onChanged();
                }
            });
        }
        labels.add(a.getString(R.string.menu_info));
        actions.add(() -> Launch.appInfo(a, app));
        if (!app.system) {
            labels.add(a.getString(R.string.menu_uninstall));
            actions.add(() -> Launch.uninstall(a, app));
        }
        new AlertDialog.Builder(a)
                .setTitle(app.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }
}
