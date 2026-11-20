package org.novaos.mobile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Sends mails for Nova Mail (SMTP submission, RFC 6409): TLS from the start (465) or
 * STARTTLS (587), login with PLAIN or LOGIN. Never without encryption. No Android classes.
 */
final class Smtp {
    /** The server refused (code = SMTP reply code, e.g. 535 wrong password, 550 unknown address). */
    static final class SmtpException extends IOException {
        final int code;

        SmtpException(int code, String message) {
            super(message);
            this.code = code;
        }

        boolean auth() {
            return code == 535 || code == 534 || code == 530;
        }
    }

    private Smtp() {}

    /** Only checks that the login works (for setting up an account). */
    static void check(String host, int port, String security, String user, String password, int timeoutMs) throws IOException {
        send(host, port, security, user, password, null, null, null, timeoutMs);
    }

    static void send(String host, int port, String security, String user, String password, String from,
                     List<String> recipients, byte[] message, int timeoutMs) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port), timeoutMs);
        plain.setSoTimeout(timeoutMs);
        Socket s = "starttls".equals(security) ? plain : Imap.tls(plain, host, port);
        try {
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            reply(in, 220);
            String ehlo = "EHLO novaos.local";
            String caps = cmd(in, out, ehlo, 250);
            if ("starttls".equals(security)) {
                if (!caps.toUpperCase(Locale.ROOT).contains("STARTTLS")) throw new IOException("STARTTLS not offered");
                cmd(in, out, "STARTTLS", 220);
                s = Imap.tls(plain, host, port);
                in = new BufferedInputStream(s.getInputStream());
                out = s.getOutputStream();
                caps = cmd(in, out, ehlo, 250);
            }
            String up = caps.toUpperCase(Locale.ROOT);
            if (up.contains("AUTH") && up.matches("(?s).*AUTH[ =][^\\n]*PLAIN.*")) {
                byte[] p = ("\u0000" + user + "\u0000" + password).getBytes(StandardCharsets.UTF_8);
                cmd(in, out, "AUTH PLAIN " + Mime.base64(p, 0), 235);
            } else {
                cmd(in, out, "AUTH LOGIN", 334);
                cmd(in, out, Mime.base64(user.getBytes(StandardCharsets.UTF_8), 0), 334);
                cmd(in, out, Mime.base64(password.getBytes(StandardCharsets.UTF_8), 0), 235);
            }
            if (message == null) {
                cmd(in, out, "QUIT", 221);
                return;
            }
            cmd(in, out, "MAIL FROM:<" + from + ">", 250);
            for (String r : recipients) cmd(in, out, "RCPT TO:<" + r + ">", 250, 251);
            cmd(in, out, "DATA", 354);
            out.write(dotStuff(message));
            out.write(".\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            reply(in, 250);
            try {
                cmd(in, out, "QUIT", 221);
            } catch (IOException ignored) {
                // already sent
            }
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
                // closed
            }
        }
    }

    /** Lines starting with "." get a second dot; CRLF line ends; ends with CRLF. */
    static byte[] dotStuff(byte[] message) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(message.length + 64);
        boolean lineStart = true;
        for (int i = 0; i < message.length; i++) {
            byte c = message[i];
            if (c == '\n' && (i == 0 || message[i - 1] != '\r')) b.write('\r');
            if (lineStart && c == '.') b.write('.');
            b.write(c);
            lineStart = c == '\n';
        }
        if (!lineStart) {
            b.write('\r');
            b.write('\n');
        }
        return b.toByteArray();
    }

    private static String cmd(InputStream in, OutputStream out, String line, int... ok) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        return reply(in, ok);
    }

    /** Reads a (multi-line) reply; throws unless its code is one of ok. */
    private static String reply(InputStream in, int... ok) throws IOException {
        StringBuilder all = new StringBuilder();
        int code;
        while (true) {
            String l = line(in);
            all.append(l).append('\n');
            if (l.length() < 3) throw new IOException("SMTP: " + l);
            code = Integer.parseInt(l.substring(0, 3));
            if (l.length() == 3 || l.charAt(3) != '-') break;
        }
        for (int o : ok) if (code == o) return all.toString();
        throw new SmtpException(code, all.toString().trim());
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            b.write(c);
            if (b.size() > 65536) throw new IOException("line too long");
        }
        if (c == -1 && b.size() == 0) throw new IOException("connection closed");
        String s = new String(b.toByteArray(), StandardCharsets.UTF_8);
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }
}
