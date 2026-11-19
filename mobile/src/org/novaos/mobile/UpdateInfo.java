package org.novaos.mobile;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * NovaOS system software update: the description published next to the APK
 * (mobile/update.properties on the NovaOS website). Android-independent, tested.
 */
public final class UpdateInfo {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9._-]+\\.apk");

    public final long versionCode;
    public final String versionName, apk, sha256;
    public final long size;
    public final List<String> notesDe, notesEn;

    UpdateInfo(long versionCode, String versionName, String apk, String sha256, long size,
               List<String> notesDe, List<String> notesEn) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apk = apk;
        this.sha256 = sha256;
        this.size = size;
        this.notesDe = notesDe;
        this.notesEn = notesEn;
    }

    /** null if the text is not a valid update description. */
    public static UpdateInfo parse(String text) {
        if (text == null) return null;
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        try {
            long code = Long.parseLong(p.getProperty("versionCode", "").trim());
            String name = p.getProperty("versionName", "").trim();
            String apk = p.getProperty("apk", "").trim();
            String sha = p.getProperty("sha256", "").trim().toLowerCase(java.util.Locale.ROOT);
            long size = Long.parseLong(p.getProperty("size", "0").trim());
            if (code <= 0 || name.isEmpty() || !FILE.matcher(apk).matches() || !SHA256.matcher(sha).matches()) return null;
            return new UpdateInfo(code, name, apk, sha, size, notes(p, "de"), notes(p, "en"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> notes(Properties p, String lang) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String s = p.getProperty("note." + lang + "." + i);
            if (s != null && !s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    public boolean newerThan(long installedCode) {
        return versionCode > installedCode;
    }

    public List<String> notes(boolean german) {
        return german || notesEn.isEmpty() ? notesDe : notesEn;
    }

    /** "what's new" lines of res/raw/whatsnew.txt ("German|English" per line). */
    public static List<String> whatsNew(String text, boolean german) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\|", 2);
            out.add((german || f.length < 2 ? f[0] : f[1]).trim());
        }
        return out;
    }

    /** 2413216 -> "2,3 MB" (German) / "2.3 MB". */
    public static String size(long bytes, boolean german) {
        String s;
        if (bytes >= 1024L * 1024) s = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        else if (bytes >= 1024) s = String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0);
        else s = bytes + " B";
        return german ? s.replace('.', ',') : s;
    }
}
