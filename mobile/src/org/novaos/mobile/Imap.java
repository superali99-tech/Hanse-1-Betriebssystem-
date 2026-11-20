package org.novaos.mobile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * IMAP (RFC 3501) for Nova Mail: log in, folders, message list, whole messages, flags,
 * moving to the bin, saving sent mails. Only encrypted (TLS or STARTTLS), certificate and
 * host name are checked. No Android classes (tested against a real mail server).
 */
final class Imap implements Closeable {
    /** The server said NO/BAD (e.g. wrong password: AUTHENTICATIONFAILED). */
    static final class ImapException extends IOException {
        final boolean auth;

        ImapException(String message, boolean auth) {
            super(message);
            this.auth = auth;
        }
    }

    /** Set by the app: Android's own host name check (on the JVM the TLS layer checks it). */
    static volatile HostnameVerifier verifier;

    static final class Folder {
        String name = "", role = "";   // role: inbox, sent, drafts, trash, junk, archive ("" = normal)
        boolean selectable = true;
    }

    static final class Header {
        long uid, size, date;
        boolean seen, flagged, answered, attachment;
        String headerText = "";
    }

    static final class Selected {
        long exists, uidValidity, uidNext;
    }

    private final Socket socket;
    private InputStream in;
    private OutputStream out;
    private int tag;
    private final List<String> caps = new ArrayList<>();

    private Imap(Socket s) {
        socket = s;
    }

    /** security: "ssl" or "starttls". */
    static Imap connect(String host, int port, String security, int timeoutMs) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port), timeoutMs);
        plain.setSoTimeout(timeoutMs);
        Socket s = plain;
        if (!"starttls".equals(security)) s = tls(plain, host, port);
        Imap imap = new Imap(s);
        imap.streams();
        String greeting = imap.line();
        if (!greeting.startsWith("* OK") && !greeting.startsWith("* PREAUTH")) {
            imap.close();
            throw new IOException("IMAP: " + greeting);
        }
        if ("starttls".equals(security)) {
            imap.capability();
            if (!imap.caps.contains("STARTTLS")) {
                imap.close();
                throw new IOException("STARTTLS not offered");  // never send the password in plain text
            }
            imap.command("STARTTLS");
            Imap secure = new Imap(tls(plain, host, port));
            secure.tag = imap.tag;
            secure.streams();
            return secure;
        }
        return imap;
    }

    static SSLSocket tls(Socket plain, String host, int port) throws IOException {
        SSLSocket s = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);
        SSLParameters p = s.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS");
        try {
            s.setSSLParameters(p);
        } catch (RuntimeException ignored) {
            // old platform: the verifier below checks the name
        }
        s.startHandshake();
        HostnameVerifier v = verifier;
        if (v != null && !v.verify(host, s.getSession())) {
            s.close();
            throw new javax.net.ssl.SSLPeerUnverifiedException("certificate is not for " + host);
        }
        return s;
    }

    private void streams() throws IOException {
        in = new BufferedInputStream(socket.getInputStream(), 65536);
        out = socket.getOutputStream();
    }

    @Override
    public void close() {
        try {
            if (out != null) {
                out.write(("z LOGOUT\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // closed
        }
    }

    // ------------------------------------------------------------------ protocol

    /** One line without CRLF (bytes as Latin-1). */
    private String line() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            b.write(c);
            if (b.size() > 1024 * 1024) throw new IOException("line too long");
        }
        if (c == -1 && b.size() == 0) throw new IOException("connection closed");
        String s = new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    /** A response line with its literals ({n} + n bytes) joined into one byte array. */
    private byte[] response() throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        while (true) {
            String l = line();
            byte[] lb = l.getBytes(StandardCharsets.ISO_8859_1);
            all.write(lb, 0, lb.length);
            int n = literalSize(l);
            if (n < 0) break;
            all.write('\r');
            all.write('\n');
            byte[] buf = new byte[8192];
            int left = n;
            while (left > 0) {
                int r = in.read(buf, 0, Math.min(buf.length, left));
                if (r < 0) throw new IOException("connection closed");
                all.write(buf, 0, r);
                left -= r;
            }
            if (all.size() > 60L * 1024 * 1024) throw new IOException("message too large");
        }
        return all.toByteArray();
    }

    static int literalSize(String line) {
        if (!line.endsWith("}")) return -1;
        int open = line.lastIndexOf('{');
        if (open < 0) return -1;
        String num = line.substring(open + 1, line.length() - 1).replace("+", "");
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Sends a command and collects the untagged answers until the tagged OK. */
    private List<byte[]> command(String cmd) throws IOException {
        return command(cmd, null);
    }

    private List<byte[]> command(String cmd, byte[] literal) throws IOException {
        String t = "n" + (++tag);
        if (literal == null) {
            out.write((t + " " + cmd + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } else {  // APPEND: wait for "+" before the literal
            out.write((t + " " + cmd + " {" + literal.length + "}\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (true) {
                String l = line();
                if (l.startsWith("+")) break;
                if (l.startsWith(t + " ")) throw new ImapException(l, false);
            }
            out.write(literal);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }
        List<byte[]> untagged = new ArrayList<>();
        while (true) {
            byte[] r = response();
            String head = new String(r, 0, Math.min(r.length, 200), StandardCharsets.ISO_8859_1);
            if (head.startsWith(t + " ")) {
                String status = head.substring(t.length() + 1);
                if (status.startsWith("OK")) {
                    int b = status.indexOf("[CAPABILITY ");
                    if (b >= 0) parseCaps(status.substring(b + 12, Math.max(b + 12, status.indexOf(']', b))));
                    return untagged;
                }
                boolean auth = status.contains("AUTHENTICATIONFAILED") || status.contains("AUTHORIZATIONFAILED")
                        || (cmd.startsWith("LOGIN") || cmd.startsWith("AUTHENTICATE"));
                throw new ImapException(new String(r, StandardCharsets.UTF_8), auth);
            }
            if (head.startsWith("* CAPABILITY ")) parseCaps(head.substring(13));
            untagged.add(r);
        }
    }

    private void capability() throws IOException {
        command("CAPABILITY");
    }

    private void parseCaps(String s) {
        caps.clear();
        for (String c : s.trim().split("\\s+")) caps.add(c.toUpperCase(Locale.ROOT));
    }

    boolean has(String cap) {
        return caps.contains(cap);
    }

    /** IMAP quoted string. */
    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    void login(String user, String password) throws IOException {
        if (caps.isEmpty()) capability();
        if (has("AUTH=PLAIN") || has("LOGINDISABLED") || !onlyAscii(password) || !onlyAscii(user)) {
            byte[] plain = ("\u0000" + user + "\u0000" + password).getBytes(StandardCharsets.UTF_8);
            command("AUTHENTICATE PLAIN " + Mime.base64(plain, 0));
        } else {
            command("LOGIN " + quote(user) + " " + quote(password));
        }
        if (caps.isEmpty() || !has("IMAP4REV1") && !has("IMAP4")) capability();
    }

    private static boolean onlyAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126 || c == '"' || c == '\\') return false;
        }
        return true;
    }

    /** All folders with their role (special use or usual names). */
    List<Folder> folders() throws IOException {
        List<Folder> out = new ArrayList<>();
        for (byte[] r : command("LIST \"\" \"*\"")) {
            List<Object> l = ImapParser.parse(r);
            // * LIST (\HasNoChildren \Sent) "/" "Sent"
            if (l.size() < 5 || !"LIST".equalsIgnoreCase(String.valueOf(l.get(1)))) continue;
            Folder f = new Folder();
            Object flags = l.get(2);
            String raw = ImapParser.string(l.get(4));
            f.name = raw;
            String role = "";
            if (flags instanceof List) {
                for (Object o : (List<?>) flags) {
                    String fl = String.valueOf(o).toLowerCase(Locale.ROOT);
                    if (fl.equals("\\noselect") || fl.equals("\\nonexistent")) f.selectable = false;
                    switch (fl) {
                        case "\\sent": role = "sent"; break;
                        case "\\drafts": role = "drafts"; break;
                        case "\\trash": role = "trash"; break;
                        case "\\junk": role = "junk"; break;
                        case "\\archive": role = "archive"; break;
                        case "\\all": role = role.isEmpty() ? "all" : role; break;
                        default: break;
                    }
                }
            }
            if (raw.equalsIgnoreCase("INBOX")) role = "inbox";
            if (role.isEmpty()) role = guessRole(Utf7.decode(raw));
            f.role = role;
            if (f.selectable) out.add(f);
        }
        return out;
    }

    static String guessRole(String name) {
        String n = name.toLowerCase(Locale.GERMAN);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('.'));
        if (slash >= 0 && slash < n.length() - 1) n = n.substring(slash + 1);
        switch (n) {
            case "sent": case "sent items": case "sent messages": case "gesendet": case "gesendete objekte":
            case "gesendete elemente": case "gesendete nachrichten": case "postausgang":
                return "sent";
            case "drafts": case "entwürfe": case "entwurf": case "draft":
                return "drafts";
            case "trash": case "papierkorb": case "deleted items": case "deleted messages": case "gelöscht": case "gelöschte objekte":
            case "gelöschte elemente": case "bin":
                return "trash";
            case "junk": case "spam": case "spamverdacht": case "junk-e-mail": case "junk e-mail": case "unerwünscht":
                return "junk";
            case "archive": case "archiv":
                return "archive";
            default:
                return "";
        }
    }

    Selected select(String folder, boolean readOnly) throws IOException {
        Selected s = new Selected();
        for (byte[] r : command((readOnly ? "EXAMINE " : "SELECT ") + quote(folder))) {
            String l = new String(r, StandardCharsets.ISO_8859_1);
            if (l.endsWith(" EXISTS")) s.exists = num(l.substring(2, l.length() - 7));
            int i = l.indexOf("[UIDVALIDITY ");
            if (i >= 0) s.uidValidity = num(l.substring(i + 13, l.indexOf(']', i)));
            i = l.indexOf("[UIDNEXT ");
            if (i >= 0) s.uidNext = num(l.substring(i + 9, l.indexOf(']', i)));
        }
        return s;
    }

    private static long num(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** UIDs of all messages (or those matching an IMAP search like "UNSEEN"). */
    List<Long> search(String criteria) throws IOException {
        List<Long> out = new ArrayList<>();
        for (byte[] r : command("UID SEARCH " + criteria)) {
            String l = new String(r, StandardCharsets.ISO_8859_1);
            if (!l.startsWith("* SEARCH")) continue;
            for (String p : l.substring(8).trim().split("\\s+")) if (!p.isEmpty()) out.add(num(p));
        }
        return out;
    }

    private static final String HEADER_FIELDS = "FROM TO CC REPLY-TO SUBJECT DATE MESSAGE-ID IN-REPLY-TO REFERENCES CONTENT-TYPE";

    /** Flags, size, date and the most important header lines of these messages. */
    List<Header> headers(List<Long> uids) throws IOException {
        List<Header> out = new ArrayList<>();
        if (uids.isEmpty()) return out;
        StringBuilder set = new StringBuilder();
        for (Long u : uids) {
            if (set.length() > 0) set.append(',');
            set.append(u);
        }
        for (byte[] r : command("UID FETCH " + set + " (UID FLAGS RFC822.SIZE INTERNALDATE BODY.PEEK[HEADER.FIELDS (" + HEADER_FIELDS + ")])")) {
            List<Object> l = ImapParser.parse(r);
            if (l.size() < 4 || !"FETCH".equalsIgnoreCase(String.valueOf(l.get(2))) || !(l.get(3) instanceof List)) continue;
            Map<String, Object> f = pairs((List<?>) l.get(3));
            Header h = new Header();
            h.uid = num(String.valueOf(f.get("UID")));
            h.size = num(String.valueOf(f.get("RFC822.SIZE")));
            Object flags = f.get("FLAGS");
            if (flags instanceof List) {
                for (Object o : (List<?>) flags) {
                    String fl = String.valueOf(o);
                    if (fl.equalsIgnoreCase("\\Seen")) h.seen = true;
                    if (fl.equalsIgnoreCase("\\Flagged")) h.flagged = true;
                    if (fl.equalsIgnoreCase("\\Answered")) h.answered = true;
                }
            }
            Object idate = f.get("INTERNALDATE");
            h.date = internalDate(ImapParser.string(idate));
            for (Map.Entry<String, Object> e : f.entrySet()) {
                if (e.getKey().startsWith("BODY[HEADER")) h.headerText = ImapParser.string(e.getValue());
            }
            String ct = Mime.headers(h.headerText).getOrDefault("content-type", "").toLowerCase(Locale.ROOT);
            h.attachment = ct.startsWith("multipart/mixed");
            if (h.uid > 0) out.add(h);
        }
        return out;
    }

    /** The whole message (without setting it read). */
    byte[] message(long uid) throws IOException {
        for (byte[] r : command("UID FETCH " + uid + " (UID BODY.PEEK[])")) {
            List<Object> l = ImapParser.parse(r);
            if (l.size() < 4 || !(l.get(3) instanceof List)) continue;
            Map<String, Object> f = pairs((List<?>) l.get(3));
            Object body = f.get("BODY[]");
            if (body instanceof byte[]) return (byte[]) body;
            if (body instanceof String) return ((String) body).getBytes(StandardCharsets.ISO_8859_1);
        }
        throw new IOException("message " + uid + " not found");
    }

    void flag(long uid, String flag, boolean on) throws IOException {
        command("UID STORE " + uid + (on ? " +FLAGS.SILENT (" : " -FLAGS.SILENT (") + flag + ")");
    }

    /** Moves a message into another folder (MOVE, or copy + delete). */
    void move(long uid, String target) throws IOException {
        if (has("MOVE")) {
            command("UID MOVE " + uid + " " + quote(target));
            return;
        }
        command("UID COPY " + uid + " " + quote(target));
        flag(uid, "\\Deleted", true);
        if (has("UIDPLUS")) command("UID EXPUNGE " + uid);
        else command("EXPUNGE");
    }

    /** Deletes for good (in the bin). */
    void delete(long uid) throws IOException {
        flag(uid, "\\Deleted", true);
        if (has("UIDPLUS")) command("UID EXPUNGE " + uid);
        else command("EXPUNGE");
    }

    /** Saves a message in a folder (e.g. a sent mail in "Gesendet"). */
    void append(String folder, byte[] message, boolean seen) throws IOException {
        command("APPEND " + quote(folder) + (seen ? " (\\Seen)" : ""), message);
    }

    void noop() throws IOException {
        command("NOOP");
    }

    private static Map<String, Object> pairs(List<?> l) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < l.size(); i += 2) m.put(String.valueOf(l.get(i)).toUpperCase(Locale.ROOT), l.get(i + 1));
        return m;
    }

    /** "17-Jul-1996 02:44:25 -0700" */
    static long internalDate(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d-MMM-yyyy HH:mm:ss Z", Locale.US);
            return f.parse(s.trim()).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
