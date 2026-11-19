package org.novaos.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A YouTube video in Nova Filme, read from a channel's public RSS feed
 * (https://www.youtube.com/feeds/videos.xml). Android-independent.
 */
public final class Video {
    public final String id;
    public final String title;
    /** ISO-8601, e.g. 2026-11-18T17:00:12+00:00 (sorts as text) */
    public final String published;
    public final Catalog.Source source;

    Video(String id, String title, String published, Catalog.Source source) {
        this.id = id;
        this.title = title;
        this.published = published == null ? "" : published;
        this.source = source;
    }

    public String watchUrl() {
        return "https://www.youtube.com/watch?v=" + id;
    }

    /** 320x180 preview picture (16:9, small enough for rows of 30 tiles in memory). */
    public String thumbnailUrl() {
        return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
    }

    public static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    /** oEmbed answers 200 if the video may be shown in an embedded player, 401 if not. */
    public String oembedUrl() {
        return "https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D" + id;
    }

    /** Playing time for the info bar: 75 -> "1:15", 5530 -> "1:32:10". */
    public static String formatTime(double seconds) {
        long s = seconds > 0 && !Double.isInfinite(seconds) ? (long) seconds : 0;
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }

    /** Reads the entries of a YouTube Atom feed (no XML library needed for this simple format). */
    public static List<Video> parseFeed(String xml, Catalog.Source source) {
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

    public static String unescape(String s) {
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
