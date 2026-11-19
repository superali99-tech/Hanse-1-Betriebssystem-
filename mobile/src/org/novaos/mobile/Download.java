package org.novaos.mobile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Downloads a file (APK of an update or of a store app) with progress and SHA-256. */
final class Download {
    interface Progress {
        void onProgress(long done, long total);
    }

    private Download() {}

    static final int TIMEOUT_MS = 20000;
    private static final long MAX_BYTES = 300L * 1024 * 1024;

    /** Small text answer (update description, F-Droid API). */
    static String text(String url) throws IOException {
        HttpURLConnection con = open(url);
        try {
            if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
            try (InputStream in = con.getInputStream()) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (b.size() > 2 * 1024 * 1024) throw new IOException("too large");
                }
                return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally {
            con.disconnect();
        }
    }

    /** Saves the file and returns its SHA-256 (hex). */
    static String file(String url, File target, Progress progress) throws IOException {
        HttpURLConnection con = open(url);
        File part = new File(target.getPath() + ".part");
        try {
            if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
            long total = con.getContentLengthLong();
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[65536];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    done += n;
                    if (done > MAX_BYTES) throw new IOException("too large");
                    if (progress != null) progress.onProgress(done, total);
                }
            }
            if (!part.renameTo(target)) throw new IOException("cannot save " + target);
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            con.disconnect();
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("only https");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(TIMEOUT_MS);
        con.setReadTimeout(TIMEOUT_MS);
        con.setRequestProperty("User-Agent", "NovaOS-Mobile");
        return con;
    }
}
