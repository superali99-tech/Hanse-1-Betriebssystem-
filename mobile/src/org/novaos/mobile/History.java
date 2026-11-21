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

        /** "NovaOS 0.1.1.58 · 20.11.2026" -> "0.1.1.58". */
        public String version() {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("NovaOS\\s+(\\S+)").matcher(title);
            return m.find() ? m.group(1) : title;
        }

        /** The release date of the title ("20.11.2026"), local midnight in ms; 0 if there is none. */
        public long released() {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})").matcher(title);
            if (!m.find()) return 0;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.clear();
            cal.set(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)), 12, 0);
            return cal.getTimeInMillis();
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
            line = News.strip(line);  // "{map} …": which Nova app a point belongs to, not shown
            int bar = line.indexOf('|');
            cur.de.add(bar < 0 ? line : line.substring(0, bar).trim());
            cur.en.add(bar < 0 ? line : line.substring(bar + 1).trim());
        }
        return out;
    }
}
