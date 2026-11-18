package org.novaos.tv;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** App search: tolerant of case, accents and umlauts. Plain Java, unit-tested. */
public final class AppFilter {
    private AppFilter() {}

    public interface Labeler<T> {
        String label(T item);
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String n = s.toLowerCase(Locale.ROOT).replace("ß", "ss");
        n = Normalizer.normalize(n, Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (Character.getType(ch) != Character.NON_SPACING_MARK) b.append(ch);
        }
        return b.toString().trim();
    }

    /** 0 = no match; higher = better match. */
    public static int score(String label, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return 1;
        String l = normalize(label);
        if (l.equals(q)) return 100;
        if (l.startsWith(q)) return 80;
        String[] words = l.split("[\\s\\-_.:+]+");
        for (String w : words) if (w.startsWith(q)) return 60;
        if (l.contains(q)) return 40;
        StringBuilder initials = new StringBuilder();
        for (String w : words) if (!w.isEmpty()) initials.append(w.charAt(0));
        if (q.length() >= 2 && initials.toString().startsWith(q)) return 30;  // "zdf m" style
        return 0;
    }

    /** Matching items, best first; equal scores keep the input (alphabetical) order. */
    public static <T> List<T> filter(List<T> items, Labeler<T> labeler, String query) {
        List<T> out = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (T item : items) {
            int s = score(labeler.label(item), query);
            if (s <= 0) continue;
            int pos = out.size();
            while (pos > 0 && scores.get(pos - 1) < s) pos--;
            out.add(pos, item);
            scores.add(pos, s);
        }
        return out;
    }
}
