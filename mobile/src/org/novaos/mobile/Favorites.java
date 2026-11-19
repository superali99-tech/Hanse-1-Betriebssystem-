package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.novaos.tv.KeyList;

import java.util.List;

/** Favorite apps on the home screen, stored only on this device. */
final class Favorites {
    private static final String PREFS = "novaos_mobile", KEY = "favorites";

    private Favorites() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String raw(Context c) {
        return prefs(c).getString(KEY, "");
    }

    static List<String> get(Context c) {
        return KeyList.decode(raw(c));
    }

    static void set(Context c, List<String> keys) {
        prefs(c).edit().putString(KEY, KeyList.encode(keys)).apply();
    }

    static void clear(Context c) {
        prefs(c).edit().remove(KEY).apply();
    }
}
