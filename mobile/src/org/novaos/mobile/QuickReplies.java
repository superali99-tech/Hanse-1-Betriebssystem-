package org.novaos.mobile;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Nova's quick answers in Nova Chat: one tap sends them (for everyone who cannot or does
 * not want to type). Chosen from the last message of the other person, offline.
 * Android-independent, tested.
 */
public final class QuickReplies {
    private QuickReplies() {}

    /** Up to four answers to the message (kind: text, image, audio, ...). */
    public static List<String> to(String body, String kind, boolean german) {
        if (kind != null && (kind.equals("image") || kind.equals("video"))) {
            return german ? Arrays.asList("Schön! 😍", "Wow! 🤩", "Danke!", "👍") : Arrays.asList("Nice! 😍", "Wow! 🤩", "Thanks!", "👍");
        }
        if (kind != null && kind.equals("audio")) {
            return german ? Arrays.asList("Okay, alles klar 👍", "Danke!", "Ich rufe dich an 📞") : Arrays.asList("Okay, got it 👍", "Thanks!", "I'll call you 📞");
        }
        String t = " " + (body == null ? "" : body).toLowerCase(Locale.GERMAN).replaceAll("[^\\p{L}\\p{N}?! ]", " ").replaceAll("\\s+", " ").trim() + " ";
        if (any(t, "wie geht", "wie gehts", "how are you", "how r u", "alles gut bei dir", "alles klar bei dir")) {
            return german ? Arrays.asList("Gut, danke! Und dir? 😊", "Super! 😄", "Geht so 😕") : Arrays.asList("Good, thanks! And you? 😊", "Great! 😄", "So-so 😕");
        }
        if (any(t, " wo bist", " wo seid", "where are you")) {
            return german ? Arrays.asList("Bin gleich da 🏃", "Zu Hause 🏠", "Unterwegs 🚗") : Arrays.asList("Almost there 🏃", "At home 🏠", "On my way 🚗");
        }
        if (any(t, " wann ", " when ")) {
            return german ? Arrays.asList("Gleich", "In 10 Minuten", "Heute Abend", "Morgen") : Arrays.asList("Soon", "In 10 minutes", "Tonight", "Tomorrow");
        }
        if (any(t, "danke", "thank", " thx ")) {
            return german ? Arrays.asList("Gern geschehen 😊", "Kein Problem 👍", "❤️") : Arrays.asList("You're welcome 😊", "No problem 👍", "❤️");
        }
        if (any(t, "hab dich lieb", "liebe dich", "love you", "vermisse dich", "miss you")) {
            return german ? Arrays.asList("Ich dich auch ❤️", "🥰", "😘") : Arrays.asList("Me too ❤️", "🥰", "😘");
        }
        if (any(t, "gute nacht", "good night", "schlaf gut")) {
            return german ? Arrays.asList("Gute Nacht 🌙", "Schlaf gut 😴", "Bis morgen 👋") : Arrays.asList("Good night 🌙", "Sleep well 😴", "See you tomorrow 👋");
        }
        if (any(t, "tschüss", "tschüs", " ciao ", " bye ", "bis später", "bis morgen", "bis dann", "see you")) {
            return german ? Arrays.asList("Tschüss! 👋", "Bis später 😊", "Bis dann!") : Arrays.asList("Bye! 👋", "See you 😊", "Talk later!");
        }
        if (any(t, "sorry", "entschuldig", "tut mir leid")) {
            return german ? Arrays.asList("Kein Problem 😊", "Alles gut 👍", "Schon okay") : Arrays.asList("No problem 😊", "All good 👍", "It's okay");
        }
        if (any(t, "glückwunsch", "congrat", "geburtstag", "birthday")) {
            return german ? Arrays.asList("Danke dir! 🥳", "Vielen Dank ❤️", "🎉") : Arrays.asList("Thank you! 🥳", "Thanks a lot ❤️", "🎉");
        }
        if (t.contains("?")) {
            return german ? Arrays.asList("Ja 👍", "Nein", "Vielleicht 🤔", "Ich melde mich gleich") : Arrays.asList("Yes 👍", "No", "Maybe 🤔", "I'll get back to you");
        }
        if (any(t, " hallo ", " hi ", " hey ", " moin ", " servus ", " hello ", "guten morgen", "guten tag", "guten abend",
                "good morning", "good evening", "grüß dich", " na ")) {
            return german ? Arrays.asList("Hallo! 👋", "Hi, wie geht's?", "😊") : Arrays.asList("Hello! 👋", "Hi, how are you?", "😊");
        }
        return german ? Arrays.asList("👍", "Okay", "Danke!", "Bis später") : Arrays.asList("👍", "Okay", "Thanks!", "Talk later");
    }

    private static boolean any(String t, String... parts) {
        for (String p : parts) if (t.contains(p)) return true;
        return false;
    }
}
