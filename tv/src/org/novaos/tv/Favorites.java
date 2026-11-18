package org.novaos.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** Favorite apps, stored only on this device (SharedPreferences). */
final class Favorites {
    private static final String PREFS = "novaos_tv";
    private static final String KEY = "favorites";

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
