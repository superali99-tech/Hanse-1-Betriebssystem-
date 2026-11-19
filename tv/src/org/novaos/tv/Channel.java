package org.novaos.tv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A TV channel with an internet live stream (list: res/raw/sender.m3u).
 * Android-independent, so the parser is covered by the logic tests.
 */
final class Channel {
    final int number;
    final String name;
    final String group;
    final String url;
    /** tile colour, ARGB */
    final int color;

    Channel(int number, String name, String group, String url, int color) {
        this.number = number;
        this.name = name;
        this.group = group;
        this.url = url;
        this.color = color;
    }

    private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9-]+)=\"([^\"]*)\"");
    static final int DEFAULT_COLOR = 0xFF2A3157;

    /** Parses an M3U playlist (#EXTINF lines with tvg-chno, group-title, nova-color). */
    static List<Channel> parse(String m3u) {
        List<Channel> out = new ArrayList<>();
        if (m3u == null) return out;
        String info = null;
        for (String raw : m3u.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                info = line;
            } else if (line.startsWith("#")) {
                // comment or header
            } else if (info != null) {
                Channel c = fromInfo(info, line, out.size() + 1);
                if (c != null) out.add(c);
                info = null;
            }
        }
        return out;
    }

    private static Channel fromInfo(String info, String url, int fallbackNumber) {
        if (!url.startsWith("https://")) return null;  // Android blocks plain http anyway
        Map<String, String> attrs = new HashMap<>();
        Matcher m = ATTR.matcher(info);
        int end = info.indexOf(':');
        while (m.find()) {
            attrs.put(m.group(1).toLowerCase(), m.group(2));
            end = m.end();
        }
        int comma = info.indexOf(',', end);
        String name = comma >= 0 ? info.substring(comma + 1).trim() : "";
        if (name.isEmpty()) return null;
        int number = fallbackNumber;
        try {
            number = Integer.parseInt(attrs.getOrDefault("tvg-chno", "").trim());
        } catch (NumberFormatException ignored) {
            // keep the position in the list
        }
        String group = attrs.getOrDefault("group-title", "");
        return new Channel(number, name, group, url, parseColor(attrs.get("nova-color")));
    }

    /** "#RRGGBB" -> opaque ARGB; anything else -> default tile colour. */
    static int parseColor(String s) {
        if (s == null || !s.matches("#[0-9A-Fa-f]{6}")) return DEFAULT_COLOR;
        return 0xFF000000 | Integer.parseInt(s.substring(1), 16);
    }

    /** Next index when zapping (wraps around at both ends). */
    static int step(int size, int index, int delta) {
        if (size <= 0) return 0;
        return ((index + delta) % size + size) % size;
    }

    /** Index of the channel with this number, or -1. */
    static int indexOfNumber(List<Channel> list, int number) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).number == number) return i;
        }
        return -1;
    }
}
