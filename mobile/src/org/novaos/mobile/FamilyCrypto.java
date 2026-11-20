package org.novaos.mobile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Nova Family's secret: the family code (16 characters, about 80 bits, typed once on each phone)
 * gives the channel name on the relay and the key. Every message is encrypted and signed
 * (AES-256-GCM): the relay only sees random-looking text. Android-independent, tested.
 */
public final class FamilyCrypto {
    private FamilyCrypto() {}

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // no 0/O, 1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    /** New family code, e.g. "K7QM-2XRA-9PLD-W4EH". */
    public static String newCode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return b.toString();
    }

    /** What the user typed ("k7qm 2xra-9pld w4eh") as code, or null if it is no code. */
    public static String normalize(String typed) {
        if (typed == null) return null;
        String s = typed.toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s\\-_.]", "");
        if (s.length() != 16) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            char c = s.charAt(i);
            if (ALPHABET.indexOf(c) < 0) return null;  // codes never contain 0, O, 1 or I
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(c);
        }
        return b.toString();
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n && i < b.length; i++) s.append(String.format("%02x", b[i]));
        return s.toString();
    }

    /** Channel name on the relay (public, but not guessable). */
    public static String topic(String code) {
        return "novaos-" + hex(sha256("novaos-family-topic|" + code), 16);
    }

    /** 256-bit key (stretched, so trying codes is slow). */
    public static byte[] key(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] k = ("novaos-family-key|" + code).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 20000; i++) {
                md.update(k);
                md.update(code.getBytes(StandardCharsets.UTF_8));
                k = md.digest();
            }
            return k;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** "n1." + base64(iv + ciphertext with tag); the topic is authenticated too. */
    public static String seal(byte[] key, String topic, String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            c.updateAAD(topic.getBytes(StandardCharsets.UTF_8));
            byte[] data = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[12 + data.length];
            System.arraycopy(iv, 0, all, 0, 12);
            System.arraycopy(data, 0, all, 12, data.length);
            return "n1." + Mime.base64(all, 0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The plain text, or null if the message is not from this family or was changed. */
    public static String open(byte[] key, String topic, String sealed) {
        if (sealed == null || !sealed.startsWith("n1.")) return null;
        try {
            byte[] all = Mime.base64Decode(sealed.substring(3).trim());
            if (all.length < 12 + 16) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, all, 0, 12));
            c.updateAAD(topic.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Stored form of the parents' PIN (salted, stretched). */
    public static String pinHash(String pin, String salt) {
        byte[] h = sha256(salt + "|" + pin);
        for (int i = 0; i < 5000; i++) h = sha256(hex(h, h.length) + salt);
        return hex(h, h.length);
    }

    public static String randomId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return hex(b, 8);
    }
}
