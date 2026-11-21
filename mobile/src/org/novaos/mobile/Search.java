package org.novaos.mobile;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Quick search in the lists of the Nova apps: every word typed must appear in one of the
 * texts, ignoring upper/lower case and accents ("muller" finds "Müller", "oeffi" finds "Öffi").
 * Android-independent, tested.
 */
public final class Search {
    private Search() {}

    /** Lower case without accents; ä/ö/ü/ß also as ae/oe/ue/ss (so both spellings match). */
    public static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        String folded = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return folded.replace("ß", "ss");
    }

    private static String umlauts(String lower) {
        return lower.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
    }

    public static boolean matches(String query, String... texts) {
        String q = normalize(query).trim();
        if (q.isEmpty()) return true;
        StringBuilder all = new StringBuilder();
        for (String t : texts) {
            if (t == null) continue;
            String lower = t.toLowerCase(Locale.ROOT);
            all.append(normalize(t)).append('\n').append(umlauts(lower)).append('\n');
        }
        String hay = all.toString();
        for (String word : q.split("\\s+")) if (!hay.contains(word)) return false;
        return true;
    }
}
