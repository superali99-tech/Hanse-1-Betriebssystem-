package org.novaos.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Neuerungen" with the blue dot, like in the Play Store: shown for 3 hours after an app was
 * updated, and again when the next update for it is waiting; can be switched off.
 * Lines of res/raw/whatsnew.txt can name their Nova apps: "{chat,mail} Neu: …|New: …".
 * Android-independent, tested.
 */
public final class News {
    public static final long SHOW_MS = 3 * 3600 * 1000L;
    private static final Pattern TAG = Pattern.compile("^\\{([a-z, ]+)\\}\\s*(.*)$");

    private News() {}

    /** Show "Neuerungen" now? updatedAt = when the app (or NovaOS) was updated on this phone. */
    public static boolean show(boolean enabled, long updatedAt, long now, boolean upcoming) {
        if (!enabled) return false;
        if (upcoming) return true;
        return updatedAt > 0 && now >= updatedAt && now - updatedAt < SHOW_MS;
    }

    /** "{chat,mail} Text" -> "Text". */
    public static String strip(String line) {
        Matcher m = TAG.matcher(line.trim());
        return m.matches() ? m.group(2).trim() : line.trim();
    }

    /** The apps a line names ("{chat,mail} …" -> chat, mail); empty when it names none. */
    public static List<String> tags(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = TAG.matcher(line.trim());
        if (m.matches()) for (String t : m.group(1).split(",")) if (!t.trim().isEmpty()) out.add(t.trim());
        return out;
    }

    /**
     * What is new for one Nova app in a whatsnew.txt text: the lines that name it; if no line
     * names it but "@apps:" does, all lines. Empty when the version does not change the app.
     */
    public static List<String> forApp(String text, String key, boolean german) {
        List<String> named = new ArrayList<>(), all = new ArrayList<>();
        if (text == null) return named;
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("@")) continue;
            String[] f = strip(line).split("\\|", 2);
            String shown = (german || f.length < 2 ? f[0] : f[1]).trim();
            all.add(shown);
            if (tags(line).contains(key)) named.add(shown);
        }
        if (!named.isEmpty()) return named;
        return UpdateInfo.changedApps(text).contains(key) ? all : named;
    }
}
