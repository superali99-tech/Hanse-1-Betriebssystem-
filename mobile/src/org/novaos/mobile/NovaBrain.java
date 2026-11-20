package org.novaos.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nova, the NovaOS assistant: understands spoken or typed commands in German and English
 * without any cloud AI (fixed patterns). Android-independent, so it is fully tested; the
 * Nova app carries out the result (open app, alarm, torch, ...).
 */
public final class NovaBrain {
    public enum Kind {
        TIME, DATE, ALARM, TIMER, TORCH, OPEN_APP, SETTINGS, CALL, MESSAGE, NOTE, WEATHER, TV,
        SEARCH, CALC, VOLUME, PHOTO, HELP, TALK, UNKNOWN
    }

    /** What Nova understood. Unused fields stay at their defaults. */
    public static final class Command {
        public final Kind kind;
        public String text = "";      // app, contact, note, query, channel, city, setting, message
        public String body = "";      // text of a message
        public int hour = -1, minute = 0, seconds = 0;
        public int level = 0;         // torch: 1 on, 0 off; volume: 1 up, -1 down, 0 mute
        public double value;          // result of a calculation
        public String answer = "";    // ready answer (talk, help, calculation, not understood)

        Command(Kind kind) {
            this.kind = kind;
        }

        @Override
        public String toString() {
            return kind + "(" + text + (body.isEmpty() ? "" : "|" + body) + (hour >= 0 ? " " + hour + ":" + minute : "")
                    + (seconds > 0 ? " " + seconds + "s" : "") + ")";
        }
    }

    private NovaBrain() {}

    // "Hey Nova ...", or a bare "Nova," with a comma (so "Nova Store öffnen" keeps its name)
    private static final Pattern WAKE = Pattern.compile("^(?:(?:hey|hi|hei|hallo|hello|ok|okay|he)\\s+nova\\b[\\s,:]*|nova\\s*[,:]\\s*)");
    private static final String[] POLITE = {"bitte ", "please ", "kannst du ", "könntest du ", "würdest du ", "can you ",
            "could you ", "would you ", "ich möchte ", "ich will ", "i want to ", "i'd like to "};

    /** Lower case, without wake word, punctuation and polite words. */
    public static String clean(String spoken) {
        return cleanBoth(spoken)[0];
    }

    /**
     * {lower case, original spelling} of the cleaned text: commands are matched in lower case,
     * notes, names, cities and messages keep how they were said or typed.
     */
    static String[] cleanBoth(String spoken) {
        if (spoken == null) return new String[] {"", ""};
        String o = spoken.trim();
        String s = o.toLowerCase(Locale.GERMAN);
        if (s.length() != o.length()) o = s;  // rare letters change their length: no original spelling
        Matcher w = WAKE.matcher(s);
        if (w.find()) {
            s = s.substring(w.end());
            o = o.substring(w.end());
        }
        String punct = "(?<!\\d)[,.](?!\\d)|[?!;\"„“”]";
        s = s.replaceAll(punct, " ").replaceAll("\\s+", " ").trim();
        o = o.replaceAll(punct, " ").replaceAll("\\s+", " ").trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String p : POLITE) {
                if (s.startsWith(p)) {
                    s = s.substring(p.length()).trim();
                    o = o.substring(o.length() - s.length());
                    changed = true;
                }
            }
        }
        Matcher end = Pattern.compile("\\s+(bitte|please)$").matcher(s);
        if (end.find()) {
            s = s.substring(0, end.start());
            o = o.substring(0, s.length());
        }
        return new String[] {s.trim(), o.trim()};
    }

    /** The original spelling of a part of the cleaned lower-case text. */
    private static String spelled(String part, String c, String o) {
        if (part == null || part.isEmpty() || c.length() != o.length()) return part;
        int i = c.indexOf(part);
        return i < 0 ? part : o.substring(i, i + part.length());
    }

    /** True if the spoken text starts with the wake word ("Hey Nova ..."). */
    public static boolean hasWakeWord(String spoken) {
        if (spoken == null) return false;
        String s = spoken.toLowerCase(Locale.GERMAN).replaceAll("[,.!?]", " ").replaceAll("\\s+", " ").trim();
        return s.matches("^(hey|hi|hallo|hello|ok|okay|he|hei)\\s+nova\\b.*");
    }

    public static Command understand(String spoken, boolean german) {
        return understand(spoken, german, new ArrayList<>(), 0);
    }

    /**
     * @param channels names of the TV channels (for "spiele ZDF")
     * @param seed     picks a joke
     */
    public static Command understand(String spoken, boolean german, List<String> channels, int seed) {
        String[] both = cleanBoth(spoken);
        Command r = understandClean(both[0], german, channels, seed);
        switch (r.kind) {
            case OPEN_APP: case CALL: case MESSAGE: case NOTE: case WEATHER: case SEARCH: case UNKNOWN:
                r.text = spelled(r.text, both[0], both[1]);
                r.body = spelled(r.body, both[0], both[1]);
                break;
            default:
                break;
        }
        return r;
    }

    private static Command understandClean(String c, boolean german, List<String> channels, int seed) {
        if (c.isEmpty()) return talk(german ? "Ja? Ich höre." : "Yes? I'm listening.");
        Command r;
        if ((r = help(c, german)) != null) return r;
        if ((r = time(c)) != null) return r;
        if ((r = timer(c, german)) != null) return r;
        if ((r = alarm(c)) != null) return r;
        if ((r = torch(c)) != null) return r;
        if ((r = call(c)) != null) return r;
        if ((r = message(c)) != null) return r;
        if ((r = note(c)) != null) return r;
        if ((r = calc(c, german)) != null) return r;
        if ((r = weather(c)) != null) return r;
        if ((r = volume(c)) != null) return r;
        if ((r = photo(c)) != null) return r;
        if ((r = tv(c, channels)) != null) return r;
        if ((r = search(c)) != null) return r;
        if ((r = open(c)) != null) return r;
        if ((r = smallTalk(c, german, seed)) != null) return r;
        Command u = new Command(Kind.UNKNOWN);
        u.text = c;
        u.answer = german ? "Das habe ich leider nicht verstanden. Sag zum Beispiel: „Wie spät ist es?“, „Timer 5 Minuten“ oder „Öffne Kamera“."
                : "Sorry, I didn't understand that. Try: \"What time is it?\", \"Timer 5 minutes\" or \"Open camera\".";
        return u;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean has(String c, String... words) {
        for (String w : words) {
            if (Pattern.compile("(^|\\s)" + Pattern.quote(w) + "($|\\s)").matcher(c).find()) return true;
        }
        return false;
    }

    private static boolean contains(String c, String... parts) {
        for (String p : parts) if (c.contains(p)) return true;
        return false;
    }

    private static Command talk(String answer) {
        Command t = new Command(Kind.TALK);
        t.answer = answer;
        return t;
    }

    private static final Map<String, Integer> NUMBERS = new HashMap<>();
    private static final String[] DE = {"null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun",
            "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn", "zwanzig"};
    private static final String[] EN = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
    private static final String[] DE_UNITS = {"ein", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"};
    private static final String[] DE_TENS = {"zwanzig", "dreißig", "vierzig", "fünfzig"};
    private static final String[] EN_TENS = {"twenty", "thirty", "forty", "fifty"};

    static {
        for (int i = 0; i < DE.length; i++) NUMBERS.put(DE[i], i);
        for (int i = 0; i < EN.length; i++) NUMBERS.put(EN[i], i);
        NUMBERS.put("dreißig", 30);
        NUMBERS.put("dreissig", 30);
        NUMBERS.put("vierzig", 40);
        NUMBERS.put("fünfzig", 50);
        NUMBERS.put("sechzig", 60);
        NUMBERS.put("hundert", 100);
        NUMBERS.put("thirty", 30);
        NUMBERS.put("forty", 40);
        NUMBERS.put("fifty", 50);
        NUMBERS.put("sixty", 60);
        NUMBERS.put("hundred", 100);
        for (int u = 0; u < DE_UNITS.length; u++) {
            for (int t = 0; t < DE_TENS.length; t++) NUMBERS.put(DE_UNITS[u] + "und" + DE_TENS[t], (t + 2) * 10 + u + 1);
        }
    }

    /** Number words to digits ("fünf minuten" -> "5 minuten", "twenty five" -> "25"); articles before units count as 1. */
    static String numbers(String c) {
        String s = " " + c + " ";
        for (int t = 0; t < EN_TENS.length; t++) {
            for (int u = 1; u <= 9; u++) {
                s = s.replace(" " + EN_TENS[t] + " " + EN[u] + " ", " " + ((t + 2) * 10 + u) + " ")
                        .replace(" " + EN_TENS[t] + "-" + EN[u] + " ", " " + ((t + 2) * 10 + u) + " ");
            }
        }
        StringBuilder out = new StringBuilder();
        for (String w : s.trim().split(" ")) {
            Integer n = NUMBERS.get(w);
            out.append(n != null ? String.valueOf(n) : w).append(' ');
        }
        s = " " + out.toString().trim() + " ";
        // "eine minute", "a minute", "an hour"
        s = s.replaceAll(" (ein|eine|einen|einer|a|an) (minute|minuten|stunde|sekunde|minutes|minute|hour|second) ", " 1 $2 ");
        return s.trim();
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ commands

    private static Command help(String c, boolean german) {
        if (!contains(c, "was kannst du", "hilfe", "what can you do", "help me") && !c.equals("help")) return null;
        Command h = new Command(Kind.HELP);
        h.answer = german
                ? "Ich kann zum Beispiel: die Uhrzeit sagen, einen Wecker oder Timer stellen, die Taschenlampe schalten, Apps öffnen, "
                + "jemanden anrufen, eine Notiz schreiben, das Wetter sagen, einen Sender einschalten und rechnen."
                : "For example I can tell the time, set an alarm or a timer, switch the torch, open apps, call someone, "
                + "take a note, tell the weather, play a TV channel and do sums.";
        return h;
    }

    private static Command time(String c) {
        if (contains(c, "wie spät", "uhrzeit", "wie viel uhr", "wieviel uhr", "what time", "the time")) return new Command(Kind.TIME);
        if (contains(c, "welcher tag", "welches datum", "datum", "der wievielte", "den wievielten", "what day", "the date",
                "what's the date", "welchen tag")) {
            return new Command(Kind.DATE);
        }
        return null;
    }

    private static final Pattern DURATION = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(stunden|stunde|std|hours|hour|hrs|h|minuten|minute|min|minutes|sekunden|sekunde|sek|seconds|second|sec|s)\\b");

    private static Command timer(String c, boolean german) {
        if (!contains(c, "timer", "countdown", "kurzzeitwecker")) return null;
        String n = numbers(c);
        double total = 0;
        Matcher m = DURATION.matcher(n);
        while (m.find()) {
            double v = Double.parseDouble(m.group(1).replace(',', '.'));
            String unit = m.group(2);
            if (unit.startsWith("st") || unit.startsWith("h")) total += v * 3600;
            else if (unit.startsWith("min")) total += v * 60;
            else total += v;
        }
        if (contains(n, "halbe stunde", "half an hour", "half hour")) total += 1800;
        if (contains(n, "viertelstunde", "viertel stunde", "quarter of an hour", "quarter hour")) total += 900;
        if (contains(n, "anderthalb stunden", "eineinhalb stunden")) total += 5400;
        Command t = new Command(Kind.TIMER);
        t.seconds = (int) Math.round(total);
        if (t.seconds <= 0) {
            t.answer = german ? "Wie lange? Sag zum Beispiel: „Timer 5 Minuten“." : "How long? Say for example: \"Timer 5 minutes\".";
        }
        return t;
    }

    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2})[:.](\\d{2})");
    private static final Pattern HALF = Pattern.compile("halb (\\d{1,2})");
    private static final Pattern QUARTER_PAST = Pattern.compile("viertel nach (\\d{1,2})");
    private static final Pattern QUARTER_TO = Pattern.compile("viertel vor (\\d{1,2})");
    private static final Pattern UHR = Pattern.compile("(\\d{1,2})\\s*uhr(?:\\s+(\\d{1,2}))?");
    private static final Pattern AMPM = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a m|p m)\\b");
    private static final Pattern AT = Pattern.compile("\\b(?:um|auf|für|gegen|at|for)\\s+(\\d{1,2})\\b");

    private static Command alarm(String c) {
        if (!contains(c, "wecker", "weck mich", "wecke mich", "alarm", "wake me")) return null;
        String n = numbers(c);
        Command a = new Command(Kind.ALARM);
        Matcher m;
        if ((m = AMPM.matcher(n)).find()) {
            int h = toInt(m.group(1)) % 12;
            a.hour = m.group(3).startsWith("p") ? h + 12 : h;
            a.minute = m.group(2) != null ? toInt(m.group(2)) : 0;
        } else if ((m = CLOCK.matcher(n)).find()) {
            a.hour = toInt(m.group(1));
            a.minute = toInt(m.group(2));
        } else if ((m = HALF.matcher(n)).find()) {
            a.hour = (toInt(m.group(1)) + 23) % 24;
            a.minute = 30;
        } else if ((m = QUARTER_PAST.matcher(n)).find()) {
            a.hour = toInt(m.group(1));
            a.minute = 15;
        } else if ((m = QUARTER_TO.matcher(n)).find()) {
            a.hour = (toInt(m.group(1)) + 23) % 24;
            a.minute = 45;
        } else if ((m = UHR.matcher(n)).find()) {
            a.hour = toInt(m.group(1));
            a.minute = m.group(2) != null ? toInt(m.group(2)) : 0;
        } else if ((m = AT.matcher(n)).find()) {
            a.hour = toInt(m.group(1));
        }
        if (a.hour > 23 || a.minute > 59) {
            a.hour = -1;
            a.minute = 0;
        }
        return a;
    }

    private static Command torch(String c) {
        if (!contains(c, "taschenlampe", "lampe", "flashlight", "torch") && !has(c, "licht", "light")) return null;
        Command t = new Command(Kind.TORCH);
        t.level = has(c, "aus", "off", "ausschalten", "ausmachen", "abschalten") ? 0 : 1;
        return t;
    }

    // ---- calculator

    private static final String[][] OPS = {
            {"geteilt durch", "/"}, {"dividiert durch", "/"}, {"divided by", "/"}, {"multipliziert mit", "*"},
            {"multiplied by", "*"}, {"plus", "+"}, {"minus", "-"}, {"mal", "*"}, {"times", "*"}, {"durch", "/"},
            {"over", "/"}, {"hoch", "^"}, {"to the power of", "^"}, {"wurzel aus", "√"}, {"wurzel von", "√"},
            {"square root of", "√"}, {"x", "*"}, {"×", "*"}, {"÷", "/"}};
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:prozent|%|percent)\\s+(?:von|of)\\s+(\\d+(?:[.,]\\d+)?)");

    private static Command calc(String c, boolean german) {
        String n = numbers(c);
        if (!n.matches(".*\\d.*")) return null;
        Matcher p = PERCENT.matcher(n);
        String expr;
        if (p.find()) {
            expr = p.group(1) + "/100*" + p.group(2);
        } else {
            String s = " " + n + " ";
            boolean op = false;
            for (String[] o : OPS) {
                String w = " " + o[0] + " ";
                if (s.contains(w)) {
                    s = s.replace(w, " " + o[1] + " ");
                    op = true;
                }
            }
            if (!op && !s.matches(".*\\d\\s*[-+*/^]\\s*\\d.*")) return null;
            // keep only the arithmetic part ("was ist 5 * 7" -> "5 * 7")
            Matcher m = Pattern.compile("[-+*/^√(]?\\s*[√(]?\\s*\\d[\\d.,\\s+\\-*/^√()]*").matcher(s);
            if (!m.find()) return null;
            expr = m.group().trim();
        }
        try {
            double v = new Calc(expr.replace(',', '.')).parse();
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("no number");
            Command r = new Command(Kind.CALC);
            r.value = v;
            r.text = expr;
            r.answer = (german ? "Das ergibt " : "That makes ") + format(v, german) + ".";
            return r;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 35 -> "35", 2.5 -> "2,5" (German) / "2.5". */
    public static String format(double v, boolean german) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e15) return String.valueOf((long) Math.rint(v));
        String s = String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        return german ? s.replace('.', ',') : s;
    }

    /** Small calculator: + - * / ^ √ and brackets, usual precedence. */
    static final class Calc {
        private final String s;
        private int i;

        Calc(String s) {
            this.s = s.replace(" ", "");
        }

        double parse() {
            double v = sum();
            if (i != s.length()) throw new IllegalArgumentException("unexpected " + s.substring(i));
            return v;
        }

        private double sum() {
            double v = product();
            while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                char op = s.charAt(i++);
                double r = product();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }

        private double product() {
            double v = power();
            while (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/')) {
                char op = s.charAt(i++);
                double r = power();
                v = op == '*' ? v * r : v / r;
            }
            return v;
        }

        private double power() {
            double v = unary();
            if (i < s.length() && s.charAt(i) == '^') {
                i++;
                v = Math.pow(v, power());
            }
            return v;
        }

        private double unary() {
            if (i < s.length() && s.charAt(i) == '-') {
                i++;
                return -unary();
            }
            if (i < s.length() && s.charAt(i) == '√') {
                i++;
                return Math.sqrt(unary());
            }
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                double v = sum();
                if (i >= s.length() || s.charAt(i) != ')') throw new IllegalArgumentException("missing )");
                i++;
                return v;
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (start == i) throw new IllegalArgumentException("number expected");
            return Double.parseDouble(s.substring(start, i));
        }
    }

    // ---- other commands

    private static final Pattern CITY = Pattern.compile("\\b(?:in|für|for|at)\\s+([\\p{L}][\\p{L} .-]*)$");

    private static Command weather(String c) {
        if (!contains(c, "wetter", "weather", "regnet", "regen", "rain", "temperatur", "temperature", "wie warm", "how warm")) {
            return null;
        }
        Command w = new Command(Kind.WEATHER);
        Matcher m = CITY.matcher(c);
        if (m.find()) {
            String city = m.group(1).replaceAll("\\s+(heute|morgen|today|tomorrow|jetzt|now)$", "").trim();
            if (!city.matches("(heute|morgen|today|tomorrow|jetzt|now)")) w.text = city;
        }
        return w;
    }

    private static String stripPossessive(String name) {
        return name.replaceAll("^(meine|meinen|meinem|meiner|mein|my|die|den|der|das|the)\\s+", "").trim();
    }

    private static final Pattern[] CALL = {
            Pattern.compile("^(?:ruf|rufe|ruft)\\s+(.+?)\\s+an$"),
            Pattern.compile("^(?:anrufen|telefoniere mit|telefonieren mit|call|phone|dial)\\s+(.+)$"),
            Pattern.compile("^(.+?)\\s+anrufen$")};

    private static Command call(String c) {
        for (Pattern p : CALL) {
            Matcher m = p.matcher(c);
            if (m.find()) {
                Command r = new Command(Kind.CALL);
                r.text = stripPossessive(m.group(1));
                return r;
            }
        }
        return null;
    }

    private static final Pattern[] MESSAGE = {
            Pattern.compile("^(?:schreib|schreibe|sende|schick|schicke)\\s+(?:eine\\s+|ne\\s+)?(?:sms|nachricht)\\s+an\\s+(.+?)(?:\\s*(?::|dass)\\s*(.+))?$"),
            Pattern.compile("^(?:schreib|schreibe|sende|schick|schicke)\\s+(.+?)\\s+(?:eine\\s+|ne\\s+)?(?:sms|nachricht)(?:\\s*(?:dass|:)\\s*(.+))?$"),
            Pattern.compile("^(?:nachricht|sms)\\s+an\\s+(.+?)(?:\\s*(?::|dass)\\s*(.+))?$"),
            Pattern.compile("^(?:send\\s+(?:a\\s+)?(?:message|text|sms)\\s+to|message|text)\\s+(.+?)(?:\\s+(?:saying|that)\\s+(.+))?$")};

    private static Command message(String c) {
        for (Pattern p : MESSAGE) {
            Matcher m = p.matcher(c);
            if (m.find()) {
                Command r = new Command(Kind.MESSAGE);
                r.text = stripPossessive(m.group(1));
                r.body = m.group(2) != null ? m.group(2).trim() : "";
                return r;
            }
        }
        return null;
    }

    private static final Pattern NOTE = Pattern.compile(
            "^(?:notiere|notier|notiz|neue notiz|merke dir|merk dir|schreib auf|schreibe auf|take a note|make a note|note|remember)\\s*:?\\s*(.+)$");

    private static Command note(String c) {
        Matcher m = NOTE.matcher(c);
        if (!m.find()) return null;
        Command r = new Command(Kind.NOTE);
        r.text = m.group(1).trim();
        return r;
    }

    private static Command volume(String c) {
        if (has(c, "lauter", "louder") || contains(c, "volume up", "lautstärke hoch", "lautstärke erhöhen", "ton lauter")) {
            Command v = new Command(Kind.VOLUME);
            v.level = 1;
            return v;
        }
        if (has(c, "leiser", "quieter") || contains(c, "volume down", "lautstärke runter", "ton leiser")) {
            Command v = new Command(Kind.VOLUME);
            v.level = -1;
            return v;
        }
        if (has(c, "stumm", "lautlos", "mute") || contains(c, "ton aus", "sound off")) {
            Command v = new Command(Kind.VOLUME);
            v.level = 0;
            return v;
        }
        return null;
    }

    private static Command photo(String c) {
        if (contains(c, "foto", "photo", "picture", "selfie", "bild machen", "mach ein bild")
                && !contains(c, "galerie", "gallery", "zeig", "show")) {
            return new Command(Kind.PHOTO);
        }
        return null;
    }

    private static final Pattern PLAY = Pattern.compile("^(?:spiel|spiele|zeig|zeige|schalte auf|schalt auf|schalte|wechsle zu|play|watch|switch to|put on)\\s+(.+)$");

    private static Command tv(String c, List<String> channels) {
        if (c.matches("(fernsehen|fernseher|tv|fernsehen schauen|watch tv|television)")) return new Command(Kind.TV);
        Matcher m = PLAY.matcher(c);
        String wanted = m.find() ? m.group(1) : c;
        wanted = wanted.replaceAll("\\s+(ein|an|on)$", "").replaceAll("^(den sender|sender|kanal|channel)\\s+", "").trim();
        if (wanted.equals("ard") || wanted.equals("erste")) wanted = "das erste";
        boolean asked = m.matches();  // "spiele ..." allows a shortened name
        for (int pass = 0; pass < 2; pass++) {
            for (String ch : channels) {
                String n = ch.toLowerCase(Locale.GERMAN);
                boolean hit = pass == 0 ? n.equals(wanted)
                        : asked && wanted.length() >= 2 && (n.startsWith(wanted) || wanted.startsWith(n + " "));
                if (hit) {
                    Command t = new Command(Kind.TV);
                    t.text = ch;
                    return t;
                }
            }
        }
        return null;
    }

    private static final Pattern SEARCH = Pattern.compile("^(?:suche|such|google|search|finde|look up)\\s+(?:nach\\s+|for\\s+|im internet nach\\s+)?(.+)$");
    private static final Pattern QUESTION = Pattern.compile("^(?:wer ist|wer war|was ist|was sind|wo ist|wo liegt|who is|who was|what is|where is)\\s+(.+)$");

    private static Command search(String c) {
        Matcher m = SEARCH.matcher(c);
        if (m.find()) {
            Command s = new Command(Kind.SEARCH);
            s.text = m.group(1).trim();
            return s;
        }
        if (QUESTION.matcher(c).find()) {  // knowledge questions: Nova has no cloud AI, so a web search
            Command s = new Command(Kind.SEARCH);
            s.text = c;
            return s;
        }
        return null;
    }

    private static final Pattern[] OPEN = {
            Pattern.compile("^(?:öffne|öffnen|starte|start|open|launch|zeig mir|zeige mir|geh zu|gehe zu|go to)\\s+(?:die\\s+|den\\s+|das\\s+|the\\s+)?(.+?)(?:\\s+app)?$"),
            Pattern.compile("^(.+?)(?:\\s+app)?\\s+(?:öffnen|starten|aufmachen)$")};
    private static final Map<String, String> SETTINGS = new HashMap<>();

    static {
        for (String w : Arrays.asList("wlan", "wifi", "wi-fi", "w-lan", "internet")) SETTINGS.put(w, "wifi");
        SETTINGS.put("bluetooth", "bluetooth");
        for (String w : Arrays.asList("display", "bildschirm", "helligkeit", "brightness", "anzeige")) SETTINGS.put(w, "display");
        for (String w : Arrays.asList("ton", "sound", "lautstärke", "töne")) SETTINGS.put(w, "sound");
        for (String w : Arrays.asList("akku", "battery", "batterie")) SETTINGS.put(w, "battery");
        for (String w : Arrays.asList("einstellungen", "settings", "android einstellungen")) SETTINGS.put(w, "settings");
        for (String w : Arrays.asList("nova einstellungen", "novaos einstellungen", "nova settings")) SETTINGS.put(w, "nova");
    }

    private static Command open(String c) {
        String name = null;
        for (Pattern p : OPEN) {
            Matcher m = p.matcher(c);
            if (m.find()) {
                name = m.group(1).trim();
                break;
            }
        }
        if (name == null) {
            String s = SETTINGS.get(c.replaceAll("^(die|den|das)\\s+", ""));
            if (s != null) {
                Command r = new Command(Kind.SETTINGS);
                r.text = s;
                return r;
            }
            return null;
        }
        String setting = SETTINGS.get(name.replace("-einstellungen", " einstellungen"));
        if (setting == null && name.endsWith(" einstellungen")) setting = SETTINGS.get(name.substring(0, name.length() - 14));
        if (setting != null) {
            Command r = new Command(Kind.SETTINGS);
            r.text = setting;
            return r;
        }
        Command r = new Command(Kind.OPEN_APP);
        r.text = stripPossessive(name);
        return r;
    }

    private static final String[] JOKES_DE = {
            "Warum können Geister so schlecht lügen? Weil man durch sie hindurchsieht.",
            "Was ist orange und läuft durch den Wald? Eine Wanderine.",
            "Treffen sich zwei Magnete. Sagt der eine: Was soll ich heute bloß anziehen?",
            "Was macht ein Handy beim Arzt? Es lässt sich durchchecken – mit NovaOS natürlich ganz entspannt."};
    private static final String[] JOKES_EN = {
            "Why don't skeletons fight each other? They don't have the guts.",
            "What do you call a fake noodle? An impasta.",
            "Why did the phone wear glasses? It lost its contacts."};

    private static Command smallTalk(String c, boolean german, int seed) {
        if (contains(c, "witz", "joke", "lustig", "funny")) {
            String[] j = german ? JOKES_DE : JOKES_EN;
            return talk(j[Math.floorMod(seed, j.length)]);
        }
        if (contains(c, "wie geht es dir", "wie gehts", "wie geht's", "how are you")) {
            return talk(german ? "Mir geht es super, danke! Und was kann ich für dich tun?" : "I'm great, thanks! What can I do for you?");
        }
        if (contains(c, "wer bist du", "was bist du", "wie heißt du", "who are you", "what are you", "your name")) {
            return talk(german ? "Ich bin Nova, der Assistent von NovaOS. Ich arbeite ohne Cloud direkt auf deinem Gerät."
                    : "I'm Nova, the NovaOS assistant. I work right on your device, without any cloud.");
        }
        if (has(c, "danke", "dankeschön", "thanks", "thank") || contains(c, "vielen dank", "thank you")) {
            return talk(german ? "Gern geschehen!" : "You're welcome!");
        }
        if (c.matches("(hallo|hi|hey|servus|moin|moin moin|guten morgen|guten tag|guten abend|hello|good morning|good evening|good afternoon).*")) {
            return talk(german ? "Hallo! Wie kann ich helfen?" : "Hello! How can I help?");
        }
        if (contains(c, "ich liebe dich", "i love you")) {
            return talk(german ? "Das ist lieb! Ich bin immer für dich da." : "That's sweet! I'm always here for you.");
        }
        return null;
    }
}
