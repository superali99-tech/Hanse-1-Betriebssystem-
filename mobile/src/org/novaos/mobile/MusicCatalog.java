package org.novaos.mobile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What Nova Musik plays: songs on the phone, radio stations (radio-browser.info, a free
 * community directory) and real songs from Deezer's public search (30-second previews; the
 * whole song opens in Deezer, Spotify or YouTube). Android-independent, tested.
 */
public final class MusicCatalog {
    public static final String LOCAL = "local", RADIO = "radio", PREVIEW = "preview";

    /** One thing to play. */
    public static final class Track {
        public final String kind, title, artist, url;
        public String album = "", art = "", link = "", id = "";
        public long durationMs;

        public Track(String kind, String title, String artist, String url) {
            this.kind = kind;
            this.title = title == null ? "" : title.trim();
            this.artist = artist == null ? "" : artist.trim();
            this.url = url;
        }

        public boolean live() {
            return RADIO.equals(kind);
        }
    }

    private MusicCatalog() {}

    private static final String RADIO_API = "https://de1.api.radio-browser.info/json/stations/";
    private static final String DEEZER_API = "https://api.deezer.com/";

    static String enc(String s) {
        try {
            return URLEncoder.encode(s.trim(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The most listened German stations. */
    public static String radioTopUrl() {
        return RADIO_API + "search?countrycode=DE&order=clickcount&reverse=true&limit=80&hidebroken=true";
    }

    public static String radioSearchUrl(String name) {
        return RADIO_API + "search?name=" + enc(name) + "&order=clickcount&reverse=true&limit=60&hidebroken=true";
    }

    public static String chartUrl() {
        return DEEZER_API + "chart/0/tracks?limit=50";
    }

    public static String songSearchUrl(String q) {
        return DEEZER_API + "search?q=" + enc(q) + "&limit=50";
    }

    /**
     * Stations of a radio-browser answer. Only encrypted (https) streams: NovaOS allows no
     * unencrypted connections. The same station is listed once (first = most listened).
     */
    public static List<Track> parseRadio(String json) {
        List<Track> out = new ArrayList<>();
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (RuntimeException e) {
            return out;
        }
        if (!(root instanceof List)) return out;
        Set<String> names = new HashSet<>();
        for (Object o : (List<?>) root) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            String url = str(m, "url_resolved");
            if (url.isEmpty()) url = str(m, "url");
            String name = cleanName(str(m, "name"));
            if (!url.startsWith("https://") || name.isEmpty()) continue;
            String codec = str(m, "codec").toUpperCase(java.util.Locale.ROOT);
            if (!codec.isEmpty() && !codec.equals("MP3") && !codec.equals("AAC") && !codec.equals("AAC+") && !codec.equals("OGG")
                    && !codec.equals("UNKNOWN")) continue;
            if (!names.add(name.toLowerCase(java.util.Locale.ROOT))) continue;
            Track t = new Track(RADIO, name, tags(str(m, "tags")), url);
            String icon = str(m, "favicon");
            t.art = icon.startsWith("https://") ? icon : "";
            t.link = str(m, "homepage").startsWith("https://") ? str(m, "homepage") : "";
            t.id = str(m, "stationuuid");
            out.add(t);
        }
        return out;
    }

    /** "  # TOP 100 DJ CHARTS - DJ REMIX " -> "TOP 100 DJ CHARTS - DJ REMIX". */
    static String cleanName(String n) {
        String s = n.replaceAll("[\\p{Cntrl}]", " ").trim();
        s = s.replaceAll("^[-#_*|.\\s]+", "").replaceAll("[-#_*|.\\s]+$", "").replaceAll("\\s{2,}", " ");
        return s.length() > 60 ? s.substring(0, 60).trim() + "…" : s;
    }

    /** First three genre tags: "pop,rock,top 40,hits" -> "Pop · Rock · Top 40". */
    static String tags(String t) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String tag : t.split(",")) {
            String x = tag.trim();
            if (x.isEmpty() || x.length() > 20) continue;
            if (n++ == 3) break;
            if (b.length() > 0) b.append(" · ");
            b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));
        }
        return b.toString();
    }

    /** Songs of a Deezer answer (search or charts): 30-second previews, cover, link to the whole song. */
    public static List<Track> parseDeezer(String json) {
        List<Track> out = new ArrayList<>();
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (RuntimeException e) {
            return out;
        }
        Object data = root instanceof Map ? ((Map<?, ?>) root).get("data") : null;
        if (!(data instanceof List)) return out;
        for (Object o : (List<?>) data) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            String preview = str(m, "preview");
            if (!preview.startsWith("https://")) continue;
            Object artist = m.get("artist"), album = m.get("album");
            Track t = new Track(PREVIEW, str(m, "title"), artist instanceof Map ? str((Map<?, ?>) artist, "name") : "", preview);
            if (album instanceof Map) {
                t.album = str((Map<?, ?>) album, "title");
                String cover = str((Map<?, ?>) album, "cover_medium");
                t.art = cover.startsWith("https://") ? cover : "";
            }
            String link = str(m, "link");
            t.link = link.startsWith("https://www.deezer.com/") ? link : "";
            Object d = m.get("duration");
            t.durationMs = d instanceof Number ? ((Number) d).longValue() * 1000 : 0;
            Object id = m.get("id");
            t.id = id == null ? "" : String.valueOf(id instanceof Double ? (long) (double) (Double) id : id);
            if (!t.title.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Where the whole song can be heard (opened in the app or the browser). */
    public static String spotifySearch(Track t) {
        return "https://open.spotify.com/search/" + enc(t.artist + " " + t.title).replace("+", "%20");
    }

    public static String youtubeSearch(Track t) {
        return "https://music.youtube.com/search?q=" + enc(t.artist + " " + t.title);
    }

    /** 245000 -> "4:05". */
    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private static String str(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v instanceof String ? ((String) v).trim() : "";
    }
}
