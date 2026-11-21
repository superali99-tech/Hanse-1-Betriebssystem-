package org.novaos.mobile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Knowledge answers for Nova from Wikipedia ("Wer ist Albert Einstein?", "Was ist ein
 * Eiffelturm?"): the short summary of the article, read aloud. Android-independent, tested.
 */
public final class Wiki {
    public static final class Summary {
        public String title = "", text = "", url = "";
        public boolean ambiguous;
    }

    private Wiki() {}

    /** Article names to try: as said, then With Capital Words; without "der/die/das/ein". */
    public static List<String> titles(String topic) {
        List<String> out = new ArrayList<>();
        String t = topic.trim().replaceAll("[?!.]+$", "")
                .replaceFirst("^(der|die|das|ein|eine|einen|the|a|an)\\s+", "").trim();
        if (t.isEmpty()) return out;
        out.add(Character.toUpperCase(t.charAt(0)) + t.substring(1));
        StringBuilder words = new StringBuilder();
        for (String w : t.split("\\s+")) {
            if (words.length() > 0) words.append(' ');
            words.append(w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        if (!out.contains(words.toString())) out.add(words.toString());
        return out;
    }

    public static String summaryUrl(String title, boolean german) {
        try {
            return "https://" + (german ? "de" : "en") + ".wikipedia.org/api/rest_v1/page/summary/"
                    + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20").replace("%2F", "/");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** null if the answer is no article (not found). */
    public static Summary parse(String json) {
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(root instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) root;
        Object extract = m.get("extract"), title = m.get("title"), type = m.get("type");
        if (!(extract instanceof String) || ((String) extract).trim().isEmpty()) return null;
        Summary s = new Summary();
        s.title = title instanceof String ? (String) title : "";
        s.text = ((String) extract).trim();
        s.ambiguous = "disambiguation".equals(type);
        Object urls = m.get("content_urls");
        if (urls instanceof Map && ((Map<?, ?>) urls).get("mobile") instanceof Map) {
            Object page = ((Map<?, ?>) ((Map<?, ?>) urls).get("mobile")).get("page");
            if (page instanceof String && ((String) page).startsWith("https://")) s.url = (String) page;
        }
        return s;
    }

    /** The first n sentences (abbreviations like "z. B." or "Dr." do not end a sentence). */
    public static String sentences(String text, int n) {
        StringBuilder out = new StringBuilder();
        int count = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '.' || ch == '!' || ch == '?') && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) {
                String before = text.substring(Math.max(start, i - 4), i).toLowerCase(Locale.ROOT);
                boolean abbreviation = before.matches(".*(\\b[a-zäöü]|\\bdr|\\bbzw|\\bca|\\bst|\\bnr|\\bvgl|\\busw|\\bgeb|\\bjh|\\d)$");
                if (abbreviation && i + 1 < text.length()) continue;
                out.append(text, start, i + 1);
                start = i + 1;
                if (++count == n) break;
            }
        }
        if (count < n && start < text.length()) out.append(text.substring(start));
        return out.toString().trim();
    }
}
