package org.novaos.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Details of an F-Droid app for the Nova Store details page (res/raw/store_meta.json, made by
 * tools/store_meta.py from F-Droid's index). Android-independent, tested.
 */
public final class StoreMeta {
    public String author = "", license = "", web = "", source = "", summaryDe = "", summaryEn = "", descDe = "", descEn = "", version = "";
    public String newsDe = "", newsEn = "", changelog = "";
    public long added, updated, code, size, newsDate;
    public int minSdk, permissions;
    public List<String> anti = Collections.emptyList(), shots = Collections.emptyList();

    /** All apps of the JSON file by package name; broken entries are skipped. */
    public static Map<String, StoreMeta> parseAll(String json) {
        Map<String, StoreMeta> out = new HashMap<>();
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (RuntimeException e) {
            return out;
        }
        if (!(root instanceof Map)) return out;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) root).entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) e.getValue();
            StoreMeta s = new StoreMeta();
            s.author = str(m, "author");
            s.license = str(m, "license");
            s.web = url(str(m, "web"));
            s.source = url(str(m, "source"));
            s.summaryDe = str(m, "summary_de");
            s.summaryEn = str(m, "summary_en");
            s.descDe = str(m, "desc_de");
            s.descEn = str(m, "desc_en");
            s.version = str(m, "version");
            s.added = num(m, "added");
            s.updated = num(m, "updated");
            s.code = num(m, "code");
            s.size = num(m, "size");
            s.minSdk = (int) num(m, "minSdk");
            s.permissions = (int) num(m, "permissions");
            s.anti = list(m.get("anti"), false);
            s.newsDe = str(m, "news_de");
            s.newsEn = str(m, "news_en");
            s.newsDate = num(m, "news_date");
            s.changelog = url(str(m, "changelog"));
            s.shots = list(m.get("shots"), true);
            out.put(String.valueOf(e.getKey()), s);
        }
        return out;
    }

    private static String str(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v instanceof String ? ((String) v).trim() : "";
    }

    private static long num(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    /** What is new in this version (German if there is, otherwise English). */
    public String news(boolean german) {
        return german && !newsDe.isEmpty() ? newsDe : !newsEn.isEmpty() ? newsEn : newsDe;
    }

    /** Only https links are opened. */
    private static String url(String u) {
        return u.startsWith("https://") ? u : "";
    }

    /** Screenshot paths must stay inside the F-Droid repository ("/pkg/locale/phoneScreenshots/x.png"). */
    private static List<String> list(Object v, boolean paths) {
        List<String> out = new ArrayList<>();
        if (!(v instanceof List)) return out;
        for (Object o : (List<?>) v) {
            if (!(o instanceof String)) continue;
            String s = (String) o;
            if (paths && (!s.startsWith("/") || s.contains("..") || !s.matches("[A-Za-z0-9._/@-]+"))) continue;
            out.add(s);
        }
        return out;
    }
}
