package org.novaos.mobile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * What the parents set for a child's phone in Nova Family - locked now or until a time,
 * bedtime ("Ruhezeit", e.g. 21:00-07:00 on school days), daily screen time, blocked apps,
 * the parents' PIN - and whether the phone is locked right now. Android-independent, tested.
 */
public final class FamilyPolicy {
    public long version;
    public long lockUntil;             // 0 = not locked, -1 = until the parents unlock, else time in ms
    public long freeUntil;             // unlocked by the parents until (beats bedtime and screen time)
    public boolean bedtime;
    public int bedFrom = 21 * 60, bedTo = 7 * 60;  // minutes of the day
    public int days = 0x7F;            // bit 0 = Monday ... bit 6 = Sunday (the evening the bedtime starts)
    public int limitMinutes;           // screen time per day, 0 = no limit
    public String bonusDay = "";       // "more time" for this day (yyyy-MM-dd)
    public int bonusMinutes;
    public String message = "";        // shown on the child's lock screen
    public String pinHash = "", pinSalt = "";
    public final List<String> blocked = new ArrayList<>();          // package names
    public final List<String[]> callable = new ArrayList<>();       // {name, number} for "Anrufen" while locked

    public static final class State {
        public boolean locked;
        public String reason = "";     // lock, bedtime, limit
        public long until;             // when it is free again (ms), -1 = only the parents can unlock

        @Override
        public String toString() {
            return locked ? reason + " until " + until : "free";
        }
    }

    // ------------------------------------------------------------------ JSON

    public Map<String, Object> toJson() {
        List<Object> c = new ArrayList<>();
        for (String[] x : callable) c.add(MiniJson.obj("name", x[0], "number", x[1]));
        return MiniJson.obj("version", version, "lockUntil", lockUntil, "freeUntil", freeUntil, "bedtime", bedtime,
                "bedFrom", bedFrom, "bedTo", bedTo, "days", days, "limit", limitMinutes, "bonusDay", bonusDay,
                "bonus", bonusMinutes, "message", message, "pinHash", pinHash, "pinSalt", pinSalt, "blocked", blocked, "callable", c);
    }

    public static FamilyPolicy fromJson(Object o) {
        FamilyPolicy p = new FamilyPolicy();
        if (!(o instanceof Map)) return p;
        p.version = num(o, "version");
        p.lockUntil = num(o, "lockUntil");
        p.freeUntil = num(o, "freeUntil");
        p.bedtime = Boolean.TRUE.equals(MiniJson.get(o, "bedtime"));
        p.bedFrom = clampMinute((int) num(o, "bedFrom", 21 * 60));
        p.bedTo = clampMinute((int) num(o, "bedTo", 7 * 60));
        p.days = (int) num(o, "days", 0x7F) & 0x7F;
        p.limitMinutes = (int) Math.max(0, Math.min(24 * 60, num(o, "limit")));
        p.bonusDay = str(o, "bonusDay");
        p.bonusMinutes = (int) Math.max(0, Math.min(24 * 60, num(o, "bonus")));
        p.message = str(o, "message");
        p.pinHash = str(o, "pinHash");
        p.pinSalt = str(o, "pinSalt");
        Object b = MiniJson.get(o, "blocked");
        if (b instanceof List) for (Object x : (List<?>) b) if (x instanceof String) p.blocked.add((String) x);
        Object c = MiniJson.get(o, "callable");
        if (c instanceof List) {
            for (Object x : (List<?>) c) {
                String n = str(x, "name"), nr = str(x, "number");
                if (!nr.replaceAll("[^0-9+]", "").isEmpty()) p.callable.add(new String[] {n, nr});
            }
        }
        return p;
    }

    private static int clampMinute(int m) {
        return Math.max(0, Math.min(24 * 60 - 1, m));
    }

    private static long num(Object o, String k) {
        return num(o, k, 0);
    }

    private static long num(Object o, String k, long fallback) {
        Object v = MiniJson.get(o, k);
        return v instanceof Double ? ((Double) v).longValue() : fallback;
    }

    private static String str(Object o, String k) {
        Object v = MiniJson.get(o, k);
        return v instanceof String ? (String) v : "";
    }

    // ------------------------------------------------------------------ is the phone locked now?

    /** Monday = 0 ... Sunday = 6 */
    static int dayIndex(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    public static String day(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private boolean dayOn(int index) {
        return (days >> ((index + 7) % 7) & 1) == 1;
    }

    /** Whether now is in the bedtime (a night bedtime belongs to the evening it starts). */
    boolean inBedtime(Calendar now) {
        if (!bedtime || bedFrom == bedTo) return false;
        int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int d = dayIndex(now);
        if (bedFrom < bedTo) return dayOn(d) && m >= bedFrom && m < bedTo;
        return (m >= bedFrom && dayOn(d)) || (m < bedTo && dayOn(d - 1));
    }

    /** The next time (ms) the clock shows minuteOfDay, after now. */
    static long next(Calendar now, int minuteOfDay) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now.getTimeInMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public int allowedMinutes(String today) {
        return limitMinutes <= 0 ? 0 : limitMinutes + (today.equals(bonusDay) ? bonusMinutes : 0);
    }

    /**
     * @param usedMinutes screen time of today
     * @param localFree   unlocked on the phone with the parents' PIN until (ms)
     */
    public State evaluate(long now, TimeZone tz, int usedMinutes, long localFree) {
        State s = new State();
        if (localFree > now || freeUntil > now) return s;
        if (lockUntil == -1 || lockUntil > now) {
            s.locked = true;
            s.reason = "lock";
            s.until = lockUntil;
            return s;
        }
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(now);
        if (inBedtime(c)) {
            s.locked = true;
            s.reason = "bedtime";
            s.until = next(c, bedTo);
            return s;
        }
        int allowed = allowedMinutes(day(now, tz));
        if (allowed > 0 && usedMinutes >= allowed) {
            s.locked = true;
            s.reason = "limit";
            s.until = next(c, 0);
            return s;
        }
        return s;
    }

    /** "21:00" */
    public static String time(int minuteOfDay) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    /** "21:30" or "9.15" -> minutes of the day, -1 if it is no time. */
    public static int parseTime(String s) {
        if (s == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:uhr)?\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        if (!m.find()) return -1;
        int h = Integer.parseInt(m.group(1)), min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (h > 23 || min > 59) return -1;
        return h * 60 + min;
    }
}
