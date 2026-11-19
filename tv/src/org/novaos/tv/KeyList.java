package org.novaos.tv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Ordered list of app keys (favorites), stored as one string. Plain Java, unit-tested. */
public final class KeyList {
    public static final String SEP = "\n";

    private KeyList() {}

    public static List<String> decode(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String k : raw.split(SEP)) {
                k = k.trim();
                if (!k.isEmpty()) out.add(k);
            }
        }
        return new ArrayList<>(out);
    }

    public static String encode(List<String> keys) {
        return String.join(SEP, keys);
    }

    /** Adds or removes {@code key}; returns true if it is in the list afterwards. */
    public static boolean toggle(List<String> keys, String key) {
        if (keys.remove(key)) return false;
        keys.add(key);
        return true;
    }

    /** Moves {@code key} by {@code delta} positions; returns true if something changed. */
    public static boolean move(List<String> keys, String key, int delta) {
        int i = keys.indexOf(key);
        if (i < 0) return false;
        int j = Math.max(0, Math.min(keys.size() - 1, i + delta));
        if (i == j) return false;
        keys.remove(i);
        keys.add(j, key);
        return true;
    }

    /** Drops keys of apps that are no longer installed. */
    public static List<String> retain(List<String> keys, Collection<String> existing) {
        List<String> out = new ArrayList<>();
        for (String k : keys) if (existing.contains(k)) out.add(k);
        return out;
    }
}
