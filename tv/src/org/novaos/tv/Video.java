package org.novaos.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A YouTube video in Nova Filme, read from a channel's public RSS feed
 * (https://www.youtube.com/feeds/videos.xml). Android-independent.
 */
final class Video {
    final String id;
    final String title;
    /** ISO-8601, e.g. 2026-11-18T17:00:12+00:00 (sorts as text) */
    final String published;
    final Catalog.Source source;

    Video(String id, String title, String published, Catalog.Source source) {
        this.id = id;
        this.title = title;
        this.published = published == null ? "" : published;
        this.source = source;
    }

    String watchUrl() {
        return "https://www.youtube.com/watch?v=" + id;
    }

    /** 320x180 preview picture (16:9, small enough for rows of 30 tiles in memory). */
    String thumbnailUrl() {
        return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
    }

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    /** Reads the entries of a YouTube Atom feed (no XML library needed for this simple format). */
    static List<Video> parseFeed(String xml, Catalog.Source source) {
        List<Video> out = new ArrayList<>();
        if (xml == null) return out;
        int pos = 0;
        while (true) {
            int start = xml.indexOf("<entry>", pos);
            if (start < 0) break;
            int end = xml.indexOf("</entry>", start);
            if (end < 0) break;
            String e = xml.substring(start, end);
            pos = end + 8;
            String id = tag(e, "yt:videoId");
            String title = tag(e, "title");
            if (id == null || title == null || !VIDEO_ID.matcher(id).matches()) continue;
            out.add(new Video(id, unescape(title).trim(), tag(e, "published"), source));
        }
        return out;
    }

    private static String tag(String s, String name) {
        int a = s.indexOf("<" + name + ">");
        if (a < 0) return null;
        a += name.length() + 2;
        int b = s.indexOf("</" + name + ">", a);
        return b < 0 ? null : s.substring(a, b);
    }

    static String unescape(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int semi = c == '&' ? s.indexOf(';', i) : -1;
            if (semi < 0 || semi - i > 10) {
                out.append(c);
                continue;
            }
            String ent = s.substring(i + 1, semi);
            String rep = null;
            switch (ent) {
                case "amp": rep = "&"; break;
                case "lt": rep = "<"; break;
                case "gt": rep = ">"; break;
                case "quot": rep = "\""; break;
                case "apos": rep = "'"; break;
                default:
                    try {
                        if (ent.startsWith("#x")) rep = new String(Character.toChars(Integer.parseInt(ent.substring(2), 16)));
                        else if (ent.startsWith("#")) rep = new String(Character.toChars(Integer.parseInt(ent.substring(1))));
                    } catch (IllegalArgumentException ignored) {
                        // not an entity: keep the text as it is
                    }
            }
            if (rep == null) {
                out.append(c);
            } else {
                out.append(rep);
                i = semi;
            }
        }
        return out.toString();
    }
}
