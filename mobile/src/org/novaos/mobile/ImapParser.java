package org.novaos.mobile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an IMAP response into its parts: atoms and quoted strings (String), literals
 * (byte[]), NIL (null) and lists in brackets (List). "BODY[HEADER.FIELDS (FROM)]" stays one
 * atom. Android-independent, tested.
 */
public final class ImapParser {
    private ImapParser() {}

    private static final class Pos {
        int i;
    }

    public static List<Object> parse(byte[] r) {
        Pos p = new Pos();
        List<Object> out = new ArrayList<>();
        while (p.i < r.length) {
            skipSpace(r, p);
            if (p.i >= r.length) break;
            if (r[p.i] == ')') {  // stray closing bracket
                p.i++;
                continue;
            }
            out.add(value(r, p));
        }
        return out;
    }

    private static void skipSpace(byte[] r, Pos p) {
        while (p.i < r.length && (r[p.i] == ' ' || r[p.i] == '\r' || r[p.i] == '\n')) p.i++;
    }

    private static Object value(byte[] r, Pos p) {
        byte c = r[p.i];
        if (c == '(') {
            p.i++;
            List<Object> l = new ArrayList<>();
            while (true) {
                skipSpace(r, p);
                if (p.i >= r.length) return l;
                if (r[p.i] == ')') {
                    p.i++;
                    return l;
                }
                l.add(value(r, p));
            }
        }
        if (c == '"') {
            p.i++;
            StringBuilder b = new StringBuilder();
            while (p.i < r.length && r[p.i] != '"') {
                if (r[p.i] == '\\' && p.i + 1 < r.length) p.i++;
                b.append((char) (r[p.i] & 0xFF));
                p.i++;
            }
            p.i++;
            return b.toString();
        }
        if (c == '{') {
            int close = p.i;
            while (close < r.length && r[close] != '}') close++;
            int n = 0;
            try {
                n = Integer.parseInt(new String(r, p.i + 1, close - p.i - 1, StandardCharsets.US_ASCII).replace("+", ""));
            } catch (NumberFormatException ignored) {
                n = 0;
            }
            int start = close + 1;
            if (start < r.length && r[start] == '\r') start++;
            if (start < r.length && r[start] == '\n') start++;
            int end = Math.min(r.length, start + n);
            byte[] data = new byte[end - start];
            System.arraycopy(r, start, data, 0, data.length);
            p.i = end;
            return data;
        }
        StringBuilder b = new StringBuilder();
        int depth = 0;
        while (p.i < r.length) {
            byte x = r[p.i];
            if (depth == 0 && (x == ' ' || x == ')' || x == '(' || x == '\r' || x == '\n')) break;
            if (x == '[') depth++;
            if (x == ']') depth = Math.max(0, depth - 1);
            b.append((char) (x & 0xFF));
            p.i++;
        }
        String atom = b.toString();
        return atom.equalsIgnoreCase("NIL") ? null : atom;
    }

    /** Text of a value (literals and strings as Latin-1 bytes: Mime decodes them). */
    public static String string(Object o) {
        if (o == null) return "";
        if (o instanceof byte[]) return new String((byte[]) o, StandardCharsets.ISO_8859_1);
        return String.valueOf(o);
    }
}
