package org.novaos.mobile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E-mails for Nova Mail (RFC 5322 / MIME): reads messages (headers with =?UTF-8?..?= words,
 * multipart, base64, quoted-printable, charsets, attachments, HTML to text) and builds the
 * messages Nova Mail sends. Android-independent, fully tested.
 */
public final class Mime {
    private Mime() {}

    public static final class Address {
        public final String name, email;

        public Address(String name, String email) {
            this.name = name == null ? "" : name;
            this.email = email == null ? "" : email;
        }

        /** "Lisa Müller" or the address. */
        public String display() {
            return name.isEmpty() ? email : name;
        }

        @Override
        public String toString() {
            return name.isEmpty() ? email : name + " <" + email + ">";
        }
    }

    public static final class Attachment {
        public String name = "", type = "application/octet-stream", contentId = "";
        public byte[] data = new byte[0];
        public boolean inline;
    }

    public static final class Message {
        public final Map<String, String> headers = new LinkedHashMap<>();
        public String subject = "", messageId = "", inReplyTo = "", references = "";
        public List<Address> from = new ArrayList<>(), to = new ArrayList<>(), cc = new ArrayList<>(), replyTo = new ArrayList<>();
        public long date;
        public String text = "", html = "";
        public final List<Attachment> attachments = new ArrayList<>();

        public Address sender() {
            return from.isEmpty() ? new Address("", "") : from.get(0);
        }

        /** Plain text of the mail (from the HTML part if there is no text part). */
        public String plain() {
            return !text.trim().isEmpty() ? text : htmlToText(html);
        }
    }

    // ------------------------------------------------------------------ reading

    public static Message parse(byte[] raw) {
        String s = new String(raw, StandardCharsets.ISO_8859_1);
        Message m = new Message();
        int split = headerEnd(s);
        Map<String, String> h = headers(split < 0 ? s : s.substring(0, split));
        m.headers.putAll(h);
        m.subject = decodeWords(h.getOrDefault("subject", "")).trim();
        m.from = addresses(h.get("from"));
        m.to = addresses(h.get("to"));
        m.cc = addresses(h.get("cc"));
        m.replyTo = addresses(h.get("reply-to"));
        m.messageId = h.getOrDefault("message-id", "").trim();
        m.inReplyTo = h.getOrDefault("in-reply-to", "").trim();
        m.references = h.getOrDefault("references", "").trim();
        m.date = date(h.get("date"));
        part(m, h, split < 0 ? "" : s.substring(skipBlank(s, split)), 0);
        return m;
    }

    private static int headerEnd(String s) {
        int a = s.indexOf("\r\n\r\n"), b = s.indexOf("\n\n");
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private static int skipBlank(String s, int at) {
        return s.startsWith("\r\n\r\n", at) ? at + 4 : at + 2;
    }

    /** Header lines (unfolded, names in lower case; the first of each name wins). */
    public static Map<String, String> headers(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        String name = null;
        StringBuilder value = new StringBuilder();
        for (String line : text.split("\r?\n")) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t') && name != null) {
                value.append(' ').append(line.trim());
                continue;
            }
            if (name != null && !out.containsKey(name)) out.put(name, utf8(value.toString().trim()));
            int c = line.indexOf(':');
            if (c <= 0) {
                name = null;
                continue;
            }
            name = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
            value.setLength(0);
            value.append(line.substring(c + 1).trim());
        }
        if (name != null && !out.containsKey(name)) out.put(name, utf8(value.toString().trim()));
        return out;
    }

    /** Raw 8-bit header text (bytes as Latin-1) that is valid UTF-8 becomes UTF-8. */
    static String utf8(String latin1) {
        boolean high = false;
        for (int i = 0; i < latin1.length() && !high; i++) high = latin1.charAt(i) > 0x7F;
        if (!high) return latin1;
        try {
            CharsetDecoder d = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return d.decode(ByteBuffer.wrap(latin1.getBytes(StandardCharsets.ISO_8859_1))).toString();
        } catch (Exception e) {
            return latin1;
        }
    }

    private static void part(Message m, Map<String, String> h, String body, int depth) {
        String ct = h.getOrDefault("content-type", "text/plain");
        String type = mainValue(ct).toLowerCase(Locale.ROOT);
        if (type.isEmpty() || !type.contains("/")) type = "text/plain";
        Map<String, String> params = params(ct);
        String disposition = h.getOrDefault("content-disposition", "");
        Map<String, String> dparams = params(disposition);
        boolean attachment = mainValue(disposition).equalsIgnoreCase("attachment");
        if (type.startsWith("multipart/") && depth < 12) {
            String boundary = params.get("boundary");
            if (boundary == null || boundary.isEmpty()) return;
            List<String> parts = splitMultipart(body, boundary);
            boolean alternative = type.equals("multipart/alternative");
            for (String p : parts) {
                int split = headerEnd(p);
                Map<String, String> ph;
                String pb;
                if (p.startsWith("\r\n") || p.startsWith("\n")) {  // no headers: plain text
                    ph = new LinkedHashMap<>();
                    pb = p.substring(p.startsWith("\r\n") ? 2 : 1);
                } else {
                    ph = headers(split < 0 ? p : p.substring(0, split));
                    pb = split < 0 ? "" : p.substring(skipBlank(p, split));
                }
                if (alternative) {  // the last (richest) wins, but keep text and html both
                    part(m, ph, pb, depth + 1);
                } else {
                    part(m, ph, pb, depth + 1);
                }
            }
            return;
        }
        if (type.equals("message/rfc822") && !attachment && depth < 12) {
            Message inner = parse(decodeBody(body, h.get("content-transfer-encoding")));
            String quoted = "\n\n---------- " + inner.subject + " ----------\n" + inner.plain();
            m.text = (m.text.isEmpty() && !m.html.isEmpty() ? htmlToText(m.html) : m.text) + quoted;
            m.attachments.addAll(inner.attachments);
            return;
        }
        byte[] data = decodeBody(body, h.get("content-transfer-encoding"));
        String name = dparams.containsKey("filename") ? dparams.get("filename") : params.getOrDefault("name", "");
        name = decodeWords(name).trim();
        String cid = h.getOrDefault("content-id", "").replaceAll("[<>\\s]", "");
        boolean textPart = (type.equals("text/plain") || type.equals("text/html")) && !attachment && name.isEmpty();
        if (textPart) {
            String text = decodeText(data, params.get("charset"));
            if (type.equals("text/plain")) {
                if (m.text.isEmpty()) m.text = text;
                else m.text = m.text + "\n\n" + text;
            } else if (m.html.isEmpty()) {
                m.html = text;
            } else {
                m.html = m.html + "<hr>" + text;
            }
            return;
        }
        Attachment a = new Attachment();
        a.name = name.isEmpty() ? (type.startsWith("image/") ? "bild" : "anhang") + extension(type) : name;
        a.type = type;
        a.data = data;
        a.contentId = cid;
        a.inline = !attachment && !cid.isEmpty();
        m.attachments.add(a);
    }

    static String extension(String type) {
        switch (type) {
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "application/pdf": return ".pdf";
            case "text/calendar": return ".ics";
            case "text/plain": return ".txt";
            default: return "";
        }
    }

    static List<String> splitMultipart(String body, String boundary) {
        List<String> parts = new ArrayList<>();
        String delim = "--" + boundary;
        int pos = 0;
        int start = -1;
        while (true) {
            int i = body.indexOf(delim, pos);
            if (i < 0) break;
            boolean lineStart = i == 0 || body.charAt(i - 1) == '\n';
            if (!lineStart) {
                pos = i + delim.length();
                continue;
            }
            if (start >= 0) {
                int end = i;
                if (end > 0 && body.charAt(end - 1) == '\n') end--;
                if (end > 0 && body.charAt(end - 1) == '\r') end--;
                parts.add(body.substring(start, Math.max(start, end)));
            }
            int after = i + delim.length();
            if (body.startsWith("--", after)) return parts;  // closing delimiter
            int nl = body.indexOf('\n', after);
            if (nl < 0) break;
            start = nl + 1;
            pos = start;
        }
        if (start >= 0 && start < body.length()) parts.add(body.substring(start));  // missing closing line
        return parts;
    }

    /** "text/plain; charset=utf-8" -> "text/plain" */
    static String mainValue(String header) {
        if (header == null) return "";
        int i = header.indexOf(';');
        return (i < 0 ? header : header.substring(0, i)).trim();
    }

    /** Parameters of a header, with RFC 2231 (filename*=UTF-8''..., continuations). */
    static Map<String, String> params(String header) {
        Map<String, String> out = new LinkedHashMap<>();
        if (header == null) return out;
        Map<String, StringBuilder> cont = new LinkedHashMap<>();
        Map<String, Boolean> contEncoded = new LinkedHashMap<>();
        Matcher m = Pattern.compile(";\\s*([A-Za-z0-9_.\\-]+)(\\*[0-9]+)?(\\*)?\\s*=\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|[^;\\s]*)").matcher(header);
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            String value = m.group(5) != null ? m.group(5).replaceAll("\\\\(.)", "$1") : m.group(4);
            boolean encoded = m.group(3) != null;
            if (m.group(2) != null) {
                cont.computeIfAbsent(key, k -> new StringBuilder()).append(value);
                if (m.group(2).equals("*0")) contEncoded.put(key, encoded);
            } else if (encoded) {
                out.put(key, rfc2231(value));
            } else if (!out.containsKey(key)) {
                out.put(key, value);
            }
        }
        for (Map.Entry<String, StringBuilder> e : cont.entrySet()) {
            String v = e.getValue().toString();
            out.put(e.getKey(), Boolean.TRUE.equals(contEncoded.get(e.getKey())) ? rfc2231(v) : v);
        }
        return out;
    }

    private static String rfc2231(String v) {
        int a = v.indexOf('\''), b = a < 0 ? -1 : v.indexOf('\'', a + 1);
        String charset = a > 0 ? v.substring(0, a) : "UTF-8";
        String data = b >= 0 ? v.substring(b + 1) : v;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (c == '%' && i + 2 < data.length() && hex(data.charAt(i + 1)) >= 0 && hex(data.charAt(i + 2)) >= 0) {
                out.write(hex(data.charAt(i + 1)) * 16 + hex(data.charAt(i + 2)));
                i += 2;
            } else {
                out.write(c);
            }
        }
        return new String(out.toByteArray(), charset(charset));
    }

    static byte[] decodeBody(String body, String encoding) {
        String enc = encoding == null ? "" : encoding.trim().toLowerCase(Locale.ROOT);
        if (enc.equals("base64")) return base64Decode(body);
        if (enc.equals("quoted-printable")) return qpDecode(body);
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }

    static String decodeText(byte[] data, String charset) {
        String s = new String(data, charset(charset));
        return s.replace("\r\n", "\n");
    }

    static Charset charset(String name) {
        if (name == null || name.trim().isEmpty()) return StandardCharsets.UTF_8;
        String n = name.trim().replace("\"", "");
        if (n.equalsIgnoreCase("utf8")) n = "UTF-8";
        if (n.equalsIgnoreCase("latin1") || n.equalsIgnoreCase("iso-8859-1") || n.equalsIgnoreCase("us-ascii") || n.equalsIgnoreCase("ascii")) {
            n = "windows-1252";  // what mail programs really mean (€, quotes)
        }
        try {
            return Charset.forName(n);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static final Pattern WORD = Pattern.compile("=\\?([^?\\s]+)\\?([bBqQ])\\?([^?\\s]*)\\?=");

    /** "=?UTF-8?Q?Gr=C3=BC=C3=9Fe?=" -> "Grüße" (neighbouring encoded words join without space). */
    public static String decodeWords(String s) {
        if (s == null || !s.contains("=?")) return s == null ? "" : s;
        StringBuilder out = new StringBuilder();
        Matcher m = WORD.matcher(s);
        int last = 0;
        boolean prevWord = false;
        while (m.find()) {
            String between = s.substring(last, m.start());
            if (!(prevWord && between.trim().isEmpty())) out.append(between);
            String charset = m.group(1);
            int star = charset.indexOf('*');  // language: =?UTF-8*de?..
            if (star > 0) charset = charset.substring(0, star);
            byte[] data = m.group(2).equalsIgnoreCase("B") ? base64Decode(m.group(3)) : qpDecode(m.group(3).replace('_', ' '));
            out.append(new String(data, charset(charset)));
            last = m.end();
            prevWord = true;
        }
        out.append(s.substring(last));
        return out.toString();
    }

    /** "Lisa <lisa@x.de>, \"Müller, Max\" <max@y.de>, z@w.de" */
    public static List<Address> addresses(String header) {
        List<Address> out = new ArrayList<>();
        if (header == null || header.trim().isEmpty()) return out;
        List<String> items = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        int angle = 0;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '"' && (i == 0 || header.charAt(i - 1) != '\\')) quoted = !quoted;
            if (!quoted && c == '<') angle++;
            if (!quoted && c == '>') angle = Math.max(0, angle - 1);
            if (!quoted && angle == 0 && (c == ',' || c == ';')) {
                items.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            if (!quoted && angle == 0 && c == ':' && cur.indexOf("@") < 0) {  // group "Freunde: a@b, c@d;"
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        items.add(cur.toString());
        for (String item : items) {
            String t = item.trim();
            if (t.isEmpty()) continue;
            String name, email;
            int lt = t.lastIndexOf('<'), gt = t.lastIndexOf('>');
            if (lt >= 0 && gt > lt) {
                email = t.substring(lt + 1, gt).trim();
                name = t.substring(0, lt).trim();
            } else {
                email = t.replaceAll("\\(.*?\\)", "").trim();
                Matcher c = Pattern.compile("\\((.*?)\\)").matcher(t);
                name = c.find() ? c.group(1) : "";
            }
            if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) name = name.substring(1, name.length() - 1).replace("\\\"", "\"");
            name = decodeWords(name).trim();
            if (!email.contains("@")) continue;
            out.add(new Address(name.equalsIgnoreCase(email) ? "" : name, email));
        }
        return out;
    }

    private static final String[] DATE_FORMATS = {"EEE, d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm Z",
            "d MMM yyyy HH:mm Z", "EEE, d MMM yy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss z", "d MMM yyyy HH:mm:ss z"};

    /** RFC 5322 date in ms since 1970 (0 = unknown). */
    public static long date(String s) {
        if (s == null) return 0;
        String t = s.replaceAll("\\(.*?\\)", "").replaceAll("\\s+", " ").trim();
        t = t.replaceAll(" (UT|GMT|Z)$", " +0000");
        for (String f : DATE_FORMATS) {
            SimpleDateFormat df = new SimpleDateFormat(f, Locale.US);
            df.setLenient(true);
            ParsePosition pos = new ParsePosition(0);
            Date d = df.parse(t, pos);
            if (d != null && pos.getIndex() > 0) return d.getTime();
        }
        return 0;
    }

    private static final Map<String, String> ENTITIES = new LinkedHashMap<>();

    static {
        String[] e = {"nbsp", " ", "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "auml", "ä", "ouml", "ö",
            "uuml", "ü", "Auml", "Ä", "Ouml", "Ö", "Uuml", "Ü", "szlig", "ß", "euro", "€", "copy", "©", "reg", "®",
            "hellip", "…", "ndash", "–", "mdash", "—", "laquo", "«", "raquo", "»", "bdquo", "„", "ldquo", "“", "rdquo", "”",
            "lsquo", "‘", "rsquo", "’", "sbquo", "‚", "bull", "•", "middot", "·", "eacute", "é", "egrave", "è", "agrave", "à",
            "aacute", "á", "ccedil", "ç", "ntilde", "ñ", "trade", "™", "deg", "°", "times", "×", "shy", ""};
        for (int i = 0; i + 1 < e.length; i += 2) ENTITIES.put(e[i], e[i + 1]);
    }

    /** Readable text of an HTML mail (for the list preview and for Nova reading aloud). */
    public static String htmlToText(String html) {
        if (html == null || html.isEmpty()) return "";
        String s = html.replaceAll("(?is)<(head|style|script|title)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?is)<!--.*?-->", " ");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|tr|h[1-6]|li|table|blockquote|ul|ol)>", "\n");
        s = s.replaceAll("(?i)<li[^>]*>", "\n• ");
        s = s.replaceAll("(?i)<(p|div|tr|h[1-6]|table)[^>]*>", "\n");
        s = s.replaceAll("(?s)<[^>]*>", "");
        s = entities(s);
        s = s.replace('\u00A0', ' ');
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    static String entities(String s) {
        if (s.indexOf('&') < 0) return s;
        Matcher m = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z]{2,8});").matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String e = m.group(1), r;
            if (e.startsWith("#x") || e.startsWith("#X")) r = codePoint(Integer.parseInt(e.substring(2), 16));
            else if (e.startsWith("#")) r = codePoint(Integer.parseInt(e.substring(1)));
            else r = ENTITIES.containsKey(e) ? ENTITIES.get(e) : m.group(0);
            m.appendReplacement(b, Matcher.quoteReplacement(r));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String codePoint(int cp) {
        return cp > 0 && cp <= 0x10FFFF ? new String(Character.toChars(cp)) : "";
    }

    /** Short preview line of a mail text (without quoted lines and signatures). */
    public static String preview(String text, int max) {
        if (text == null) return "";
        StringBuilder b = new StringBuilder();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.startsWith(">")) continue;
            if (l.equals("--")) break;
            if (l.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(l);
            if (b.length() >= max) break;
        }
        String s = b.toString().replaceAll("\\s+", " ");
        return s.length() > max ? s.substring(0, max).trim() + "…" : s;
    }

    // ------------------------------------------------------------------ transfer encodings

    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    public static String base64(byte[] data, int lineLength) {
        StringBuilder b = new StringBuilder();
        int line = 0;
        for (int i = 0; i < data.length; i += 3) {
            int n = (data[i] & 0xFF) << 16 | (i + 1 < data.length ? (data[i + 1] & 0xFF) << 8 : 0) | (i + 2 < data.length ? data[i + 2] & 0xFF : 0);
            b.append(B64[n >> 18 & 63]).append(B64[n >> 12 & 63]);
            b.append(i + 1 < data.length ? B64[n >> 6 & 63] : '=');
            b.append(i + 2 < data.length ? B64[n & 63] : '=');
            line += 4;
            if (lineLength > 0 && line >= lineLength && i + 3 < data.length) {
                b.append("\r\n");
                line = 0;
            }
        }
        return b.toString();
    }

    public static byte[] base64Decode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buf = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
            else if (c >= '0' && c <= '9') v = c - '0' + 52;
            else if (c == '+' || c == '-') v = 62;
            else if (c == '/' || c == '_') v = 63;
            else if (c == '=') break;
            else continue;
            buf = buf << 6 | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write(buf >> bits & 0xFF);
            }
        }
        return out.toByteArray();
    }

    static byte[] qpDecode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i += 1;  // soft line break
                } else if (i + 2 < s.length() && s.charAt(i + 1) == '\r' && s.charAt(i + 2) == '\n') {
                    i += 2;
                } else if (i + 2 < s.length() && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                    out.write(hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2)));
                    i += 2;
                } else {
                    out.write('=');
                }
            } else {
                out.write(c & 0xFF);
            }
        }
        return out.toByteArray();
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    /** Quoted-printable with lines of at most 76 characters (CRLF line ends). */
    static String qpEncode(String text) {
        byte[] data = text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            if (b == '\n') {
                // spaces at the line end must be encoded
                int n = line.length();
                if (n > 0 && (line.charAt(n - 1) == ' ' || line.charAt(n - 1) == '\t')) {
                    char sp = line.charAt(n - 1);
                    line.setLength(n - 1);
                    line.append(String.format("=%02X", (int) sp));
                }
                out.append(line).append("\r\n");
                line.setLength(0);
                continue;
            }
            String enc = (b >= 33 && b <= 126 && b != '=') || b == ' ' || b == '\t' ? String.valueOf((char) b) : String.format("=%02X", b);
            // a dot at the line start is safe in QP, but some servers mangle it: encode
            if (line.length() == 0 && b == '.') enc = "=2E";
            if (line.length() + enc.length() > 75) {
                out.append(line).append("=\r\n");
                line.setLength(0);
            }
            line.append(enc);
        }
        int n = line.length();
        if (n > 0 && (line.charAt(n - 1) == ' ' || line.charAt(n - 1) == '\t')) {
            char sp = line.charAt(n - 1);
            line.setLength(n - 1);
            line.append(String.format("=%02X", (int) sp));
        }
        out.append(line);
        return out.toString();
    }

    /** Header word: plain ASCII stays, everything else as =?UTF-8?B?..?= (split for long text). */
    public static String encodeWord(String s) {
        boolean ascii = true;
        for (int i = 0; i < s.length() && ascii; i++) {
            char c = s.charAt(i);
            ascii = c >= 32 && c < 127;
        }
        if (ascii) return s;
        StringBuilder out = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if ((chunk.toString() + ch).getBytes(StandardCharsets.UTF_8).length > 45) {
                out.append(out.length() > 0 ? "\r\n " : "").append("=?UTF-8?B?").append(base64(chunk.toString().getBytes(StandardCharsets.UTF_8), 0)).append("?=");
                chunk.setLength(0);
            }
            chunk.append(ch);
            i += Character.charCount(cp);
        }
        if (chunk.length() > 0) {
            out.append(out.length() > 0 ? "\r\n " : "").append("=?UTF-8?B?").append(base64(chunk.toString().getBytes(StandardCharsets.UTF_8), 0)).append("?=");
        }
        return out.toString();
    }

    /** "Lisa Müller <lisa@x.de>" for a header. */
    public static String addressHeader(List<Address> list) {
        StringBuilder b = new StringBuilder();
        for (Address a : list) {
            if (b.length() > 0) b.append(", ");
            if (a.name.isEmpty()) {
                b.append(a.email);
            } else {
                String n = encodeWord(a.name);
                if (n.equals(a.name) && n.matches(".*[(),.:;<>@\\[\\]\"\\\\].*")) n = "\"" + n.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                b.append(n).append(" <").append(a.email).append('>');
            }
        }
        return b.toString();
    }

    /** Valid enough e-mail address for sending. */
    public static boolean validEmail(String s) {
        return s != null && s.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~\\-]+@[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?)+");
    }

    // ------------------------------------------------------------------ writing

    public static final class Outgoing {
        public Address from;
        public List<Address> to = new ArrayList<>(), cc = new ArrayList<>(), bcc = new ArrayList<>();
        public String subject = "", text = "", inReplyTo = "", references = "";
        public final List<Attachment> attachments = new ArrayList<>();
        public long date = System.currentTimeMillis();
        public String messageId;
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    static String randomId() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    /** The complete message as it goes to the SMTP server (Bcc is not written into it). */
    public static byte[] build(Outgoing o) {
        String domain = o.from.email.contains("@") ? o.from.email.substring(o.from.email.indexOf('@') + 1) : "novaos.local";
        if (o.messageId == null) o.messageId = "<" + randomId() + "@" + domain + ">";
        StringBuilder b = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        df.setTimeZone(TimeZone.getDefault());
        b.append("Date: ").append(df.format(new Date(o.date))).append("\r\n");
        b.append("From: ").append(addressHeader(java.util.Collections.singletonList(o.from))).append("\r\n");
        if (!o.to.isEmpty()) b.append("To: ").append(addressHeader(o.to)).append("\r\n");
        if (!o.cc.isEmpty()) b.append("Cc: ").append(addressHeader(o.cc)).append("\r\n");
        b.append("Subject: ").append(encodeWord(o.subject)).append("\r\n");
        b.append("Message-ID: ").append(o.messageId).append("\r\n");
        if (!o.inReplyTo.isEmpty()) b.append("In-Reply-To: ").append(o.inReplyTo).append("\r\n");
        if (!o.references.isEmpty()) b.append("References: ").append(o.references).append("\r\n");
        b.append("MIME-Version: 1.0\r\n");
        b.append("User-Agent: Nova Mail (NovaOS)\r\n");
        String textPart = "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n"
                + qpEncode(o.text) + "\r\n";
        if (o.attachments.isEmpty()) {
            b.append(textPart);
        } else {
            String boundary = "nova-" + randomId();
            b.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
            b.append("This is a multi-part message in MIME format.\r\n");
            b.append("--").append(boundary).append("\r\n").append(textPart);
            for (Attachment a : o.attachments) {
                String ascii = a.name.replaceAll("[^A-Za-z0-9._\\- ]", "_");
                b.append("--").append(boundary).append("\r\n");
                b.append("Content-Type: ").append(a.type).append("; name=\"").append(ascii).append("\"\r\n");
                b.append("Content-Transfer-Encoding: base64\r\n");
                b.append("Content-Disposition: attachment; filename=\"").append(ascii).append("\"");
                if (!ascii.equals(a.name)) b.append("; filename*=UTF-8''").append(pct(a.name));
                b.append("\r\n\r\n").append(base64(a.data, 76)).append("\r\n");
            }
            b.append("--").append(boundary).append("--\r\n");
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String pct(String s) {
        StringBuilder b = new StringBuilder();
        for (byte x : s.getBytes(StandardCharsets.UTF_8)) {
            int c = x & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') b.append((char) c);
            else b.append(String.format("%%%02X", c));
        }
        return b.toString();
    }

    /** "Re: " once (also for "AW:", "Aw:", "RE:"). */
    public static String replySubject(String subject) {
        String s = subject == null ? "" : subject.trim();
        if (s.matches("(?i)^(re|aw|antw)\\s*:.*")) return s;
        return "Re: " + s;
    }

    public static String forwardSubject(String subject) {
        String s = subject == null ? "" : subject.trim();
        if (s.matches("(?i)^(fwd?|wg)\\s*:.*")) return s;
        return "Fwd: " + s;
    }

    /** The quoted original under an answer ("Am ... schrieb Lisa:" + "> " lines). */
    public static String quote(Message m, String intro) {
        StringBuilder b = new StringBuilder("\n\n").append(intro).append("\n");
        for (String line : m.plain().split("\n")) b.append("> ").append(line).append("\n");
        return b.toString();
    }
}
