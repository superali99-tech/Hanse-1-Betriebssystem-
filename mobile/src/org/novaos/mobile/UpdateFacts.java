package org.novaos.mobile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * What the update pages say about the installed NovaOS, like the software update of Samsung
 * phones: "NovaOS 1.5", "31. Oktober 2026 um 16:37" and the list under
 * "Aktualisierungsinformationen". Android-independent, tested.
 */
final class UpdateFacts {
    /** The name of this NovaOS generation, shown big ("NovaOS 1.5"); the builds count on as 0.1.1.N. */
    static final String RELEASE = "1.5";

    private static final String[] MONTHS_DE = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
            "September", "Oktober", "November", "Dezember"};
    private static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};

    private UpdateFacts() {}

    /** "NovaOS 1.0" */
    static String title() {
        return "NovaOS " + RELEASE;
    }

    /** "31. Oktober 2026 um 16:37" / "31 October 2026 at 16:37"; 0 = unknown (""). */
    static String dateTime(long ms, TimeZone zone, boolean german) {
        if (ms <= 0) return "";
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(ms);
        return date(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), german)
                + (german ? " um " : " at ") + String.format(java.util.Locale.ROOT, "%d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /** "5. Oktober 2026" / "5 October 2026" */
    static String date(int year, int month, int day, boolean german) {
        String m = (german ? MONTHS_DE : MONTHS_EN)[Math.max(1, Math.min(12, month)) - 1];
        return german ? day + ". " + m + " " + year : day + " " + m + " " + year;
    }

    /** Android's security patch level "2026-10-05" as "5. Oktober 2026"; anything else unchanged. */
    static String patch(String iso, boolean german) {
        if (iso == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(iso.trim());
        if (!m.matches()) return iso.trim();
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) return iso.trim();
        return date(Integer.parseInt(m.group(1)), month, Integer.parseInt(m.group(3)), german);
    }

    /**
     * The points under "Aktualisierungsinformationen": NovaOS version, the phone's Android, the
     * build, the size of the installed NovaOS and the phone's security patch level.
     */
    static List<String> info(String android, String build, long bytes, String patchLevel, boolean german) {
        List<String> out = new ArrayList<>();
        out.add((german ? "NovaOS-Version: " : "NovaOS version: ") + RELEASE);
        if (android != null && !android.isEmpty()) out.add((german ? "Android-Version: " : "Android version: ") + android);
        out.add("Version: " + build);
        if (bytes > 0) out.add((german ? "Größe: " : "Size: ") + UpdateInfo.size(bytes, german));
        String p = patch(patchLevel, german);
        if (!p.isEmpty()) out.add((german ? "Sicherheitspatch-Ebene: " : "Security patch level: ") + p);
        return out;
    }
}
