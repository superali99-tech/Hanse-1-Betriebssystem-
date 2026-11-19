package org.novaos.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nova Filme: which YouTube channels are shown and how their videos are sorted
 * into rows. Videos play in the official embedded YouTube player inside the app. Only official channels of the rights holders (film distributors
 * that publish complete films for free, broadcasters, studios) - no uploads by
 * third parties. Android-independent (tested by LogicTest).
 */
public final class Catalog {
    private Catalog() {}

    public enum Category { FILME, SERIEN, DOKUS, KINDER }

    public static final class Source {
        public final String channelId;
        public final String name;
        public final Category category;

        Source(String channelId, String name, Category category) {
            this.channelId = channelId;
            this.name = name;
            this.category = category;
        }

        /** Public feed of the channel's videos without Shorts and live streams ("UULF" list). */
        public String feedUrl() {
            return "https://www.youtube.com/feeds/videos.xml?playlist_id=UULF" + channelId.substring(2);
        }
    }

    /**
     * Checked 19.11.2026: channel IDs belong to the official channels named here, and the
     * channels allow their videos in embedded players (Nova Filme plays inside the app).
     * Not included because they forbid embedding: Die Biene Maja, ZDFtivi, KiKA.
     */
    public static final List<Source> SOURCES = Collections.unmodifiableList(Arrays.asList(
            // complete feature films, free with ads, licensed by the distributors
            new Source("UCGkHFrwE8KEuOHPVsIOskAg", "Netzkino", Category.FILME),
            new Source("UCh4JH8axA_jUjB8YoRnm0cg", "Moviedome", Category.FILME),
            // series (only complete episodes are taken from these channels)
            new Source("UCk9uI40bPBRkC7lo6y8wQ7Q", "ZDFneo", Category.SERIEN),
            new Source("UCOgPGtSnFR6GM-AkzCnxqMQ", "funk", Category.SERIEN),
            // documentaries
            new Source("UCA3mpqm67CpJ13YfA8qAnow", "Terra X History", Category.DOKUS),
            new Source("UCLLibJTCy3sXjHLVaDimnpQ", "ARTE", Category.DOKUS),
            new Source("UCUuab1dctZzN5ZmRmQnTzkg", "WDR Doku", Category.DOKUS),
            new Source("UCTPAHk1b-h-WGQn9cfGlw2Q", "NDR Doku", Category.DOKUS),
            // children and family
            new Source("UC4gENfIXqpg-CCVN7felClg", "TOGGO", Category.KINDER),
            new Source("UCSGswD7ZPf5MTU1e-AG7EZg", "PAW Patrol & Dino Ranch", Category.KINDER),
            new Source("UCrPvUBpoqPcocrlbKE31Wlw", "Shaun das Schaf", Category.KINDER),
            new Source("UCRWSxXBnz9IRS4SgRhG2wpQ", "Die Maus", Category.KINDER),
            new Source("UCQtsd17U8NOM1VRI8oxdwiQ", "Checker Welt", Category.KINDER)));

    public static final int MAX_PER_ROW = 30;

    private static final Pattern EPISODE = Pattern.compile(
            "(?iu)\\b(folge|folgen|episode|staffel)\\b|\\bS\\d{1,2} ?E\\d{1,3}\\b");
    private static final Pattern SKIP = Pattern.compile("(?iu)trailer|teaser|#shorts|\\bpreview\\b");

    public static boolean isEpisode(String title) {
        return EPISODE.matcher(title).find();
    }

    /** Row a video belongs to, or null if it is not shown (trailers, clips of series channels). */
    public static Category categoryOf(Video v) {
        if (SKIP.matcher(v.title).find()) return null;
        switch (v.source.category) {
            case FILME:
                return isEpisode(v.title) ? Category.SERIEN : Category.FILME;
            case SERIEN:
                return isEpisode(v.title) ? Category.SERIEN : null;
            default:
                return v.source.category;
        }
    }

    /**
     * Builds the rows: videos of the sources of a row take turns (newest first),
     * so one busy channel does not fill the whole row. Duplicates are dropped.
     */
    public static Map<Category, List<Video>> rows(List<List<Video>> feeds) {
        Map<Category, List<List<Video>>> perCat = new EnumMap<>(Category.class);
        for (Category c : Category.values()) perCat.put(c, new ArrayList<>());
        for (List<Video> feed : feeds) {
            Map<Category, List<Video>> split = new EnumMap<>(Category.class);
            for (Video v : feed) {
                Category c = categoryOf(v);
                if (c == null) continue;
                List<Video> l = split.get(c);
                if (l == null) split.put(c, l = new ArrayList<>());
                l.add(v);
            }
            for (Map.Entry<Category, List<Video>> e : split.entrySet()) {
                List<Video> l = e.getValue();
                Collections.sort(l, (a, b) -> b.published.compareTo(a.published));
                perCat.get(e.getKey()).add(l);
            }
        }
        Map<Category, List<Video>> out = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            List<Video> row = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            List<List<Video>> lists = perCat.get(c);
            for (int i = 0; row.size() < MAX_PER_ROW; i++) {
                boolean any = false;
                for (List<Video> l : lists) {
                    if (i >= l.size()) continue;
                    any = true;
                    Video v = l.get(i);
                    if (seen.add(v.id) && row.size() < MAX_PER_ROW) row.add(v);
                }
                if (!any) break;
            }
            out.put(c, row);
        }
        return out;
    }

    // ------------------------------------------------------------ titles
    private static final Pattern FILM_NAME = Pattern.compile("(?iu)\\((?:ganzer )?film:\\s*([^)]+)\\)");
    private static final Pattern SEO_PARENS = Pattern.compile(
            "(?iu)\\s*\\(([^)]*\\b(film|filme|folge|deutsch|länge|serie|episode|komplett)\\b[^)]*)\\)");
    private static final Pattern NOISE = Pattern.compile(
            "(?iu)^(ganze folgen?|doku( hd)?( reupload)?|zusammenstellung|zeichentrick\\w*( für kinder)?|"
                    + "reportage( für kinder)?|terra x|die maus|kinderserie|#.*|"
                    // channel brands alone or as prefix ("ARTE Reportage", "NDR Story", "WDR Doku")
                    + "(toggo|zdftivi|zdfneo|zdf|arte|wdr|ndr|br|kika|funk)(\\s.*)?)$");
    /** "(EPISODE 7: Kalems Geist, ganze Folge, ...)" -> episode number and name */
    private static final Pattern EPISODE_INFO = Pattern.compile(
            "(?iu)\\b(?:episode|folge)\\s+(\\d+)(?:\\s*:\\s*([^,)]+))?");
    private static final Pattern HASHTAG = Pattern.compile("(?u)\\s*#\\p{L}[\\p{L}\\p{N}_]*");
    private static final String SPACE = "[\\s\\u00A0\\u2007\\u202F]+";

    /** Short title for a tile: without channel names, "Ganzer Film", genre lists and emojis. */
    public static String cleanTitle(String title, String sourceName) {
        String t = HASHTAG.matcher(stripEmoji(title)).replaceAll("").replaceAll(SPACE, " ");
        Matcher m = FILM_NAME.matcher(t);
        if (m.find()) return tidy(m.group(1));
        // keep the episode of a series ("Tell (EPISODE 7: Kalems Geist, ganze Folge)")
        String episode = "";
        Matcher seo = SEO_PARENS.matcher(t);
        if (seo.find()) {
            Matcher ep = EPISODE_INFO.matcher(seo.group(1));
            if (ep.find()) episode = "Folge " + ep.group(1) + (ep.group(2) != null ? ": " + ep.group(2).trim() : "");
        }
        t = SEO_PARENS.matcher(t).replaceAll("");
        // "Red Sky - Grandioser Actionfilm ... - Ganzer Film bei Moviedome" -> "Red Sky"
        String[] dash = t.split(" [-–] ");
        if (dash.length >= 2 && dash[dash.length - 1].toLowerCase(Locale.ROOT).startsWith("ganzer film")) {
            t = dash[0];
        }
        // "Wild Kat | Der Verrat | Ganze Folge | TOGGO" -> "Wild Kat · Der Verrat"
        String[] parts = t.split("\\s*\\|\\s*");
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (int i = 0; i < parts.length && kept < 2; i++) {
            String p = parts[i].trim().replaceAll("(?iu)\\s+deutsch$", "");
            if (p.isEmpty()) continue;
            if (i > 0 && (NOISE.matcher(p).matches() || p.equalsIgnoreCase(sourceName))) continue;
            if (kept > 0) sb.append(" · ");
            sb.append(p);
            kept++;
        }
        if (!episode.isEmpty() && kept < 2) sb.append(" · ").append(episode);
        String out = tidy(sb.toString());
        return out.isEmpty() ? tidy(title) : out;
    }

    private static String tidy(String s) {
        return s.replaceAll(SPACE, " ").replaceAll("[\\s,;:–-]+$", "").trim();
    }

    /** Emojis are often missing in TV fonts and make titles noisy. */
    public static String stripEmoji(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean emoji = cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || cp == 0xFE0F || cp == 0x200D
                    || (cp >= 0x2B00 && cp <= 0x2BFF);
            if (!emoji) out.appendCodePoint(cp);
        }
        return out.toString();
    }
}
