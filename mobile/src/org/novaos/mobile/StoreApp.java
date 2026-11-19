package org.novaos.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** An app of the Nova Store catalogue (res/raw/store.txt). Android-independent, tested. */
public final class StoreApp {
    public static final String FDROID = "fdroid", PLAY = "play";
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");

    public final String source, pkg, name, category, textDe, textEn;
    public final boolean recommended;

    StoreApp(String source, String pkg, String name, String category, String textDe, String textEn, boolean recommended) {
        this.source = source;
        this.pkg = pkg;
        this.name = name;
        this.category = category;
        this.textDe = textDe;
        this.textEn = textEn;
        this.recommended = recommended;
    }

    public boolean fromFdroid() {
        return FDROID.equals(source);
    }

    public String text(boolean german) {
        return german ? textDe : textEn;
    }

    /** F-Droid metadata (current version) - a small JSON answer per app. */
    public String fdroidApi() {
        return "https://f-droid.org/api/v1/packages/" + pkg;
    }

    public String fdroidApk(long versionCode) {
        return "https://f-droid.org/repo/" + pkg + "_" + versionCode + ".apk";
    }

    public String fdroidIcon() {
        return "https://f-droid.org/repo/" + pkg + "/en-US/icon.png";
    }

    public String fdroidIconOld(long versionCode) {
        return "https://f-droid.org/repo/icons-640/" + pkg + "." + versionCode + ".png";
    }

    public String playUrl() {
        return "https://play.google.com/store/apps/details?id=" + pkg;
    }

    /** Parses the catalogue; broken lines and unknown sources are skipped. */
    public static List<StoreApp> parse(String text) {
        List<StoreApp> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\|", -1);
            if (f.length < 6) continue;
            String src = f[0].trim();
            if (!FDROID.equals(src) && !PLAY.equals(src)) continue;
            String pkg = f[1].trim();
            if (!PACKAGE.matcher(pkg).matches() || f[2].trim().isEmpty()) continue;
            boolean rec = f.length > 6 && "*".equals(f[6].trim());
            out.add(new StoreApp(src, pkg, f[2].trim(), f[3].trim(), f[4].trim(), f[5].trim(), rec));
        }
        return out;
    }

    /** "recommended" first, then the categories in catalogue order. */
    public static Map<String, List<StoreApp>> sections(List<StoreApp> apps) {
        Map<String, List<StoreApp>> out = new LinkedHashMap<>();
        List<StoreApp> rec = new ArrayList<>();
        for (StoreApp a : apps) if (a.recommended) rec.add(a);
        if (!rec.isEmpty()) out.put("empfohlen", rec);
        for (StoreApp a : apps) {
            List<StoreApp> l = out.get(a.category);
            if (l == null || "empfohlen".equals(a.category)) out.put(a.category, l = new ArrayList<>());
            l.add(a);
        }
        for (Map.Entry<String, List<StoreApp>> e : out.entrySet()) e.setValue(Collections.unmodifiableList(e.getValue()));
        return out;
    }

    /** suggestedVersionCode of the F-Droid API answer (no JSON library needed), -1 if missing. */
    public static long suggestedCode(String json) {
        if (json == null) return -1;
        java.util.regex.Matcher m = Pattern.compile("\"suggestedVersionCode\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    public static String versionName(String json, long code) {
        if (json == null) return "";
        java.util.regex.Matcher m = Pattern.compile(
                "\\{\\s*\"versionName\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"versionCode\"\\s*:\\s*" + code + "\\s*\\}").matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
