package org.novaos.mobile;

import java.util.Locale;

/**
 * What Nova says and shows while navigating: "In 200 Metern rechts abbiegen auf die
 * Hauptstraße", "Im Kreisverkehr die zweite Ausfahrt nehmen", "Du hast dein Ziel erreicht".
 * Android-independent, tested.
 */
public final class NavText {
    private NavText() {}

    private static final String[] ORDINAL_DE = {"", "erste", "zweite", "dritte", "vierte", "fünfte", "sechste", "siebte", "achte"};
    private static final String[] ORDINAL_EN = {"", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth"};

    /** "rechts", "leicht links", ... (OSRM modifier). */
    static String side(String modifier, boolean german) {
        switch (modifier) {
            case "left": return german ? "links" : "left";
            case "right": return german ? "rechts" : "right";
            case "slight left": return german ? "leicht links" : "slightly left";
            case "slight right": return german ? "leicht rechts" : "slightly right";
            case "sharp left": return german ? "scharf links" : "sharp left";
            case "sharp right": return german ? "scharf rechts" : "sharp right";
            case "uturn": return german ? "wenden" : "make a U-turn";
            default: return german ? "geradeaus" : "straight";
        }
    }

    /** The road: name, else number ("B 75"), else nothing. */
    static String road(NavRoute.Step s) {
        if (!s.name.isEmpty() && !s.ref.isEmpty()) return s.name + " (" + s.ref + ")";
        return !s.name.isEmpty() ? s.name : s.ref;
    }

    /** The instruction without distance: "Rechts abbiegen auf Gertrudenstraße". */
    public static String instruction(NavRoute.Step s, String mode, boolean german) {
        String road = road(s);
        String onto = road.isEmpty() ? "" : (german ? " auf " : " onto ") + road;
        String side = side(s.modifier, german);
        boolean walk = NavRoute.FOOT.equals(mode);
        switch (s.type) {
            case "depart":
                return german ? (walk ? "Gehe" : "Fahre") + " Richtung " + Geo.compass(s.bearingAfter, true) + (road.isEmpty() ? "" : " auf " + road)
                        : (walk ? "Walk" : "Head") + " " + Geo.compass(s.bearingAfter, false) + (road.isEmpty() ? "" : " on " + road);
            case "arrive":
                if (s.modifier.contains("left")) return german ? "Du hast dein Ziel erreicht – es ist links" : "You have arrived – your destination is on the left";
                if (s.modifier.contains("right")) return german ? "Du hast dein Ziel erreicht – es ist rechts" : "You have arrived – your destination is on the right";
                return german ? "Du hast dein Ziel erreicht" : "You have arrived";
            case "roundabout":
            case "rotary":
            case "roundabout turn": {
                int n = s.exit;
                if (n >= 1 && n < ORDINAL_DE.length) {
                    return german ? "Im Kreisverkehr die " + ORDINAL_DE[n] + " Ausfahrt nehmen" + onto
                            : "At the roundabout take the " + ORDINAL_EN[n] + " exit" + onto;
                }
                return german ? "In den Kreisverkehr fahren" + onto : "Enter the roundabout" + onto;
            }
            case "exit roundabout":
            case "exit rotary":
                return german ? "Kreisverkehr verlassen" + onto : "Exit the roundabout" + onto;
            case "end of road":
                return german ? "Am Ende der Straße " + side + " abbiegen" + onto : "At the end of the road turn " + side + onto;
            case "fork":
                return german ? "An der Gabelung " + side + " halten" + onto : "At the fork keep " + side + onto;
            case "merge":
                return german ? "Einfädeln" + onto : "Merge" + onto;
            case "on ramp":
                return german ? "Auffahrt " + side + " nehmen" + onto : "Take the ramp on the " + side + onto;
            case "off ramp":
                return german ? "Ausfahrt " + side + " nehmen" + onto : "Take the exit on the " + side + onto;
            case "new name":
            case "continue":
                if (s.modifier.equals("uturn")) return german ? "Wenden" + onto : "Make a U-turn" + onto;
                if (s.modifier.isEmpty() || s.modifier.equals("straight")) return german ? "Geradeaus weiter" + onto : "Continue straight" + onto;
                return german ? side.substring(0, 1).toUpperCase(Locale.GERMAN) + side.substring(1) + " halten" + onto : "Keep " + side + onto;
            default:  // turn, notification, ...
                if (s.modifier.equals("uturn")) return german ? "Wenden" + onto : "Make a U-turn" + onto;
                if (s.modifier.isEmpty() || s.modifier.equals("straight")) return german ? "Geradeaus weiter" + onto : "Continue straight" + onto;
                return german ? side.substring(0, 1).toUpperCase(Locale.GERMAN) + side.substring(1) + " abbiegen" + onto : "Turn " + side + onto;
        }
    }

    /** Spoken distance: "In 200 Metern", "In 1,5 Kilometern", "Jetzt". */
    public static String inDistance(double m, boolean german) {
        if (m < 40) return german ? "Jetzt" : "Now";
        if (m < 1000) {
            long r = m < 100 ? Math.round(m / 10) * 10 : Math.round(m / 50) * 50;
            return german ? "In " + r + " Metern" : "In " + r + " metres";
        }
        String km = String.format(Locale.ROOT, m < 10_000 ? "%.1f" : "%.0f", m / 1000);
        if (km.endsWith(".0")) km = km.substring(0, km.length() - 2);
        if (km.equals("1")) return german ? "In einem Kilometer" : "In one kilometre";
        return german ? "In " + km.replace('.', ',') + " Kilometern" : "In " + km + " kilometres";
    }

    /** "In 200 Metern rechts abbiegen auf Gertrudenstraße" (first letter of the instruction small). */
    public static String announcement(NavRoute.Step s, double metres, String mode, boolean german) {
        String i = instruction(s, mode, german);
        if ("arrive".equals(s.type)) {
            return metres < 40 ? i : (german ? inDistance(metres, true) + " erreichst du dein Ziel" : inDistance(metres, false) + " you will arrive");
        }
        String head = inDistance(metres, german);
        return head + " " + lowerFirst(i);
    }

    /** Only the first word is lowered ("Im Kreisverkehr" -> "im Kreisverkehr"); nouns keep their capital. */
    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.GERMAN) + s.substring(1);
    }
}
