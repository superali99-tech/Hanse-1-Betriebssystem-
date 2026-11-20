package org.novaos.mobile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Folder names of IMAP servers in "modified UTF-7" (RFC 3501): "Entw&APw-rfe" = "Entwürfe".
 * Android-independent, tested.
 */
public final class Utf7 {
    private Utf7() {}

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+,";

    public static String decode(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '&') {
                out.append(c);
                continue;
            }
            int end = s.indexOf('-', i);
            if (end < 0) {
                out.append(s.substring(i));
                break;
            }
            if (end == i + 1) {
                out.append('&');
            } else {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                int buf = 0, bits = 0;
                for (int k = i + 1; k < end; k++) {
                    int v = ALPHABET.indexOf(s.charAt(k));
                    if (v < 0) continue;
                    buf = buf << 6 | v;
                    bits += 6;
                    if (bits >= 8) {
                        bits -= 8;
                        b.write(buf >> bits & 0xFF);
                    }
                }
                out.append(new String(b.toByteArray(), StandardCharsets.UTF_16BE));
            }
            i = end;
        }
        return out.toString();
    }

    public static String encode(String s) {
        StringBuilder out = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c <= 0x7E) {
                flush(out, pending);
                out.append(c == '&' ? "&-" : String.valueOf(c));
            } else {
                pending.append(c);
            }
        }
        flush(out, pending);
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder pending) {
        if (pending.length() == 0) return;
        byte[] b = pending.toString().getBytes(StandardCharsets.UTF_16BE);
        out.append('&');
        int buf = 0, bits = 0;
        for (byte x : b) {
            buf = buf << 8 | (x & 0xFF);
            bits += 8;
            while (bits >= 6) {
                bits -= 6;
                out.append(ALPHABET.charAt(buf >> bits & 63));
            }
        }
        if (bits > 0) out.append(ALPHABET.charAt(buf << (6 - bits) & 63));
        out.append('-');
        pending.setLength(0);
    }
}
