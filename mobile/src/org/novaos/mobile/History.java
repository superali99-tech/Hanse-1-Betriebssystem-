package org.novaos.mobile;

import java.util.ArrayList;
import java.util.List;

/** The version history of res/raw/history.txt ("== title", then "German|English" lines). Tested. */
public final class History {
    public static final class Entry {
        public final String title;
        public final List<String> de = new ArrayList<>(), en = new ArrayList<>();

        Entry(String title) {
            this.title = title;
        }

        public List<String> lines(boolean german) {
            return german || en.isEmpty() ? de : en;
        }
    }

    private History() {}

    public static List<Entry> parse(String text) {
        List<Entry> out = new ArrayList<>();
        Entry cur = null;
        for (String raw : (text == null ? "" : text).split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("==")) {
                cur = new Entry(line.substring(2).trim());
                out.add(cur);
                continue;
            }
            if (cur == null) continue;
            int bar = line.indexOf('|');
            cur.de.add(bar < 0 ? line : line.substring(0, bar).trim());
            cur.en.add(bar < 0 ? line : line.substring(bar + 1).trim());
        }
        return out;
    }
}
