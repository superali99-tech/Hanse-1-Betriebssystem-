package org.novaos.mobile;

import java.security.SecureRandom;

/**
 * Rules of the account protection, without Android (tested): when the Nova apps ask for the
 * screen lock, and the 6-digit confirmation codes sent by e-mail.
 */
public final class LockRule {
    public static final long IDLE_MS = 5 * 60 * 1000, CODE_MS = 10 * 60 * 1000;
    public static final int CODE_TRIES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LockRule() {}

    /** Ask for PIN/pattern/password/fingerprint now? */
    public static boolean needed(boolean enabled, boolean secure, boolean isProtected, boolean locked, long now, long lastUsed) {
        return enabled && secure && isProtected && (locked || now - lastUsed > IDLE_MS || now < lastUsed);
    }

    /** A fresh 6-digit code ("042517"). */
    public static String newCode() {
        return String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    /** Is the typed code right, still valid and not tried too often? */
    public static boolean codeOk(String expected, long sentAt, int tries, String typed, long now) {
        if (expected == null || typed == null || tries >= CODE_TRIES || now - sentAt > CODE_MS || now < sentAt) return false;
        String t = typed.replaceAll("\\s", "");
        if (t.length() != expected.length()) return false;
        int diff = 0;
        for (int i = 0; i < t.length(); i++) diff |= t.charAt(i) ^ expected.charAt(i);  // same time for every guess
        return diff == 0;
    }
}
