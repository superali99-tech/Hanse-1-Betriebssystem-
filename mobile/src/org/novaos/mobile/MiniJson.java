package org.novaos.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader (objects -> Map, arrays -> List, strings, numbers as Double,
 * true/false/null). Android-independent so it is covered by the tests; used for the
 * answers of the update registry.
 */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    /** Parses a JSON text; throws IllegalArgumentException if it is not valid JSON. */
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("no text");
        MiniJson p = new MiniJson(text);
        Object v = p.value();
        p.space();
        if (p.i != p.s.length()) throw p.error("trailing characters");
        return v;
    }

    /** Value at a path of object keys, e.g. get(json, "token"); null if missing. */
    public static Object get(Object json, String... keys) {
        Object cur = json;
        for (String k : keys) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(k);
        }
        return cur;
    }

    /** JSON text of a value (Map, List/array, String, Number, Boolean, null). */
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v);
        return b.toString();
    }

    /** Small object builder: obj("a", 1, "b", "x") -> {"a":1,"b":"x"} */
    public static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k + 1 < keyValues.length; k += 2) m.put((String) keyValues[k], keyValues[k + 1]);
        return m;
    }

    private static void write(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String) {
            quote(b, (String) v);
        } else if (v instanceof Boolean) {
            b.append(v);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (v instanceof Number) {
            b.append(v);
        } else if (v instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof Iterable || v instanceof Object[]) {
            b.append('[');
            boolean first = true;
            for (Object o : v instanceof Object[] ? java.util.Arrays.asList((Object[]) v) : (Iterable<?>) v) {
                if (!first) b.append(',');
                first = false;
                write(b, o);
            }
            b.append(']');
        } else {
            quote(b, v.toString());
        }
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException(what + " at " + i);
    }

    private void space() {
        while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
    }

    private Object value() {
        space();
        if (i >= s.length()) throw error("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return word("true", Boolean.TRUE);
            case 'f': return word("false", Boolean.FALSE);
            case 'n': return word("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw error("unexpected '" + c + "'");
        }
    }

    private Object word(String w, Object v) {
        if (!s.startsWith(w, i)) throw error("expected " + w);
        i += w.length();
        return v;
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;  // {
        space();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (true) {
            space();
            if (i >= s.length() || s.charAt(i) != '"') throw error("expected key");
            String k = string();
            space();
            if (i >= s.length() || s.charAt(i) != ':') throw error("expected ':'");
            i++;
            m.put(k, value());
            space();
            if (i >= s.length()) throw error("unexpected end");
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw error("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;  // [
        space();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return l;
        }
        while (true) {
            l.add(value());
            space();
            if (i >= s.length()) throw error("unexpected end");
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw error("expected ',' or ']'");
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;  // "
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length()) break;
            char e = s.charAt(i++);
            switch (e) {
                case '"': case '\\': case '/': b.append(e); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw error("bad \\u escape");
                    try {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad \\u escape");
                    }
                    i += 4;
                    break;
                default: throw error("bad escape");
            }
        }
        throw error("unterminated string");
    }

    private Double number() {
        int start = i;
        if (s.charAt(i) == '-') i++;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        try {
            return Double.valueOf(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }
}
