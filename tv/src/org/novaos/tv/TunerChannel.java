package org.novaos.tv;

import java.util.Comparator;

/**
 * A channel the TV receives itself (antenna, cable, satellite), read from the
 * Android TV channel database. Android-independent part: sorting by number.
 */
final class TunerChannel {
    final long id;
    final String number;
    final String name;

    TunerChannel(long id, String number, String name) {
        this.id = id;
        this.number = number == null ? "" : number.trim();
        this.name = name == null ? "" : name.trim();
    }

    /** Channel numbers as the TV shows them: "1", "2", "10", "1-1", "101.2", or empty. */
    static final Comparator<TunerChannel> BY_NUMBER = (a, b) -> {
        int c = compareNumbers(a.number, b.number);
        return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
    };

    /** Natural order: digit groups compare by value ("2" < "10"); empty numbers last. */
    static int compareNumbers(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() == b.isEmpty() ? 0 : (a.isEmpty() ? 1 : -1);
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = strip(a.substring(si, i)), nb = strip(b.substring(sj, j));
                if (na.length() != nb.length()) return na.length() - nb.length();
                int c = na.compareTo(nb);
                if (c != 0) return c;
            } else {
                if (ca != cb) return ca - cb;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String strip(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') k++;
        return digits.substring(k);
    }
}
