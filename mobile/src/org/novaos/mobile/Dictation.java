package org.novaos.mobile;

import java.util.Locale;

/**
 * Nova writes for you: turns dictated text into a message (spoken punctuation and emojis
 * like "Punkt", "Fragezeichen", "Daumen hoch"), and understands the short answers when
 * Nova asks "Soll ich senden?". Android-independent, tested.
 */
public final class Dictation {
    private Dictation() {}

    private static final String[][] DE = {
        {"neuer absatz", "\n\n"}, {"neue zeile", "\n"}, {"fragezeichen", "?"}, {"ausrufezeichen", "!"},
        {"doppelpunkt", ":"}, {"semikolon", ";"}, {"punkt", "."}, {"komma", ","},
        {"daumen hoch", "👍"}, {"daumen runter", "👎"}, {"lachender smiley", "😂"}, {"lach smiley", "😂"},
        {"zwinker smiley", "😉"}, {"zwinkernder smiley", "😉"}, {"trauriger smiley", "😢"}, {"kuss smiley", "😘"},
        {"herz emoji", "❤️"}, {"herzchen", "❤️"}, {"smiley", "😊"},
    };
    private static final String[][] EN = {
        {"new paragraph", "\n\n"}, {"new line", "\n"}, {"question mark", "?"}, {"exclamation mark", "!"},
        {"exclamation point", "!"}, {"colon", ":"}, {"full stop", "."}, {"period", "."}, {"comma", ","},
        {"thumbs up", "👍"}, {"thumbs down", "👎"}, {"laughing emoji", "😂"}, {"winking emoji", "😉"},
        {"sad emoji", "😢"}, {"kiss emoji", "😘"}, {"heart emoji", "❤️"}, {"smiley", "😊"},
    };

    /** "hallo lisa komma wie geht es dir fragezeichen" -> "Hallo lisa, wie geht es dir?" */
    public static String clean(String spoken, boolean german) {
        if (spoken == null) return "";
        String s = " " + spoken.trim().replaceAll("\\s+", " ") + " ";
        for (String[] w : german ? DE : EN) {
            s = s.replaceAll("(?i)(?<=\\s)" + java.util.regex.Pattern.quote(w[0]) + "(?=\\s)", java.util.regex.Matcher.quoteReplacement("\u0000" + w[1]));
        }
        // punctuation sticks to the word before it, emojis keep a space
        s = s.replaceAll("\\s*\u0000([.,?!:;])", "$1").replaceAll("\\s*\u0000(\n+)\\s*", "$1").replace("\u0000", "");
        s = s.replaceAll("[ \\t]+", " ").replaceAll(" *\n *", "\n").trim();
        return capitalize(s);
    }

    /** First letter and the first letter after . ? ! in capitals. */
    static String capitalize(String s) {
        StringBuilder b = new StringBuilder(s);
        boolean up = true;
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (up && Character.isLetter(c)) {
                b.setCharAt(i, Character.toUpperCase(c));
                up = false;
            } else if (c == '.' || c == '?' || c == '!' || c == '\n') {
                up = true;
            } else if (!Character.isWhitespace(c)) {
                up = false;
            }
        }
        return b.toString();
    }

    private static String norm(String s) {
        return " " + (s == null ? "" : s).toLowerCase(Locale.GERMAN).replaceAll("[^\\p{L} ]", " ").replaceAll("\\s+", " ").trim() + " ";
    }

    /** "Ja", "senden", "okay", "schick es ab" ... */
    public static boolean isYes(String s) {
        String t = norm(s);
        if (isNo(s)) return false;
        for (String w : new String[] {" ja ", " jawohl ", " genau ", " senden ", " sende ", " absenden ", " abschicken ", " schick ",
                " schicken ", " okay ", " ok ", " passt ", " richtig ", " yes ", " yeah ", " yep ", " send ", " sure ", " correct "}) {
            if (t.contains(w)) return true;
        }
        return false;
    }

    /** "Nein", "abbrechen", "nicht senden" ... */
    public static boolean isNo(String s) {
        String t = norm(s);
        for (String w : new String[] {" nein ", " nee ", " nö ", " abbrechen ", " nicht ", " stopp ", " stop ", " lösch ", " löschen ",
                " verwerfen ", " no ", " cancel ", " don t ", " dont ", " delete "}) {
            if (t.contains(w)) return true;
        }
        return false;
    }

    /** "Nochmal", "noch mal", "neu" -> dictate again. */
    public static boolean isAgain(String s) {
        String t = norm(s);
        return t.contains(" nochmal ") || t.contains(" noch mal ") || t.contains(" neu ") || t.contains(" again ") || t.contains(" retry ");
    }

    /** "Lies vor", "vorlesen", "read" -> read the new messages aloud. */
    public static boolean isRead(String s) {
        String t = norm(s);
        return t.contains(" vorlesen ") || t.contains(" lies ") || t.contains(" lese ") || t.contains(" read ");
    }
}
