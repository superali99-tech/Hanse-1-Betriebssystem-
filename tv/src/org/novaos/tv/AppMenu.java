package org.novaos.tv;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/** Options for an app (hold OK or press the menu key): favorites, app info, uninstall. */
final class AppMenu {
    interface Changed {
        void onChanged();
    }

    private AppMenu() {}

    static void show(Activity a, AppEntry e, Changed changed) {
        List<String> favs = Favorites.get(a);
        boolean isFav = favs.contains(e.key());
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(a.getString(isFav ? R.string.menu_unpin : R.string.menu_pin));
        actions.add(() -> {
            KeyList.toggle(favs, e.key());
            Favorites.set(a, favs);
            changed.onChanged();
        });
        if (isFav && favs.size() > 1) {
            labels.add(a.getString(R.string.menu_left));
            actions.add(() -> {
                if (KeyList.move(favs, e.key(), -1)) {
                    Favorites.set(a, favs);
                    changed.onChanged();
                }
            });
            labels.add(a.getString(R.string.menu_right));
            actions.add(() -> {
                if (KeyList.move(favs, e.key(), 1)) {
                    Favorites.set(a, favs);
                    changed.onChanged();
                }
            });
        }
        labels.add(a.getString(R.string.menu_info));
        actions.add(() -> Actions.appInfo(a, e));
        if (!e.system) {
            labels.add(a.getString(R.string.menu_uninstall));
            actions.add(() -> Actions.uninstall(a, e));
        }
        new AlertDialog.Builder(a)
                .setTitle(e.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }
}
