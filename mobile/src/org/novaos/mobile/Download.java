package org.novaos.mobile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Downloads over https (update, store apps) with progress and SHA-256. */
final class Download {
    interface Progress {
        void onProgress(long done, long total);
    }

    /** The server answered, but not with 200 OK (e.g. 403 = package not public). */
    static final class HttpException extends IOException {
        final int code;

        HttpException(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    private Download() {}

    static final int TIMEOUT_MS = 20000;
    private static final long MAX_BYTES = 300L * 1024 * 1024;

    /** Small text answer (update description, F-Droid API). */
    static String text(String url) throws IOException {
        return text(url, null, null);
    }

    /** Small text answer with an "Authorization" value and an "Accept" type (both may be null). */
    static String text(String url, String auth, String accept) throws IOException {
        HttpURLConnection con = connect(url, auth, accept);
        try (InputStream in = con.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > 2 * 1024 * 1024) throw new IOException("too large");
            }
            return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    /** Saves the file and returns its SHA-256 (hex). */
    static String file(String url, File target, Progress progress) throws IOException {
        return file(url, null, target, progress);
    }

    static String file(String url, String auth, File target, Progress progress) throws IOException {
        HttpURLConnection con = connect(url, auth, null);
        File part = new File(target.getPath() + ".part");
        try {
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

    /**
     * Download that can be paused: the part already loaded stays in target + ".part" when the
     * progress callback throws (pause) or the connection breaks, and the next call continues
     * from there (HTTP Range; if the server does not support it, from the beginning).
     * Returns the SHA-256 (hex) of the whole file.
     */
    static String resumable(String url, String auth, File target, Progress progress) throws IOException {
        File part = new File(target.getPath() + ".part");
        long have = part.isFile() ? part.length() : 0;
        HttpURLConnection con = connect(url, auth, null, have);
        try {
            boolean append = have > 0 && con.getResponseCode() == 206;
            if (!append) have = 0;
            long length = con.getContentLengthLong();
            long total = length < 0 ? -1 : have + length;
            try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(part, append)) {
                byte[] buf = new byte[65536];
                long done = have;
                int n;
                if (progress != null) progress.onProgress(done, total);
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (done > MAX_BYTES) throw new IOException("too large");
                    if (progress != null) progress.onProgress(done, total);
                }
            }
        } finally {
            con.disconnect();
        }
        if (!part.renameTo(target)) throw new IOException("cannot save " + target);
        return sha256(target);
    }

    /** SHA-256 (hex) of a file. */
    static String sha256(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) sha.update(buf, 0, n);
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /**
     * Opens the URL and follows redirects itself: only https, and the Authorization value is
     * sent only to the first server (host and port: the registry), never to the storage
     * server it redirects to.
     */
    private static HttpURLConnection connect(String url, String auth, String accept) throws IOException {
        return connect(url, auth, accept, 0);
    }

    /** from > 0: ask for the rest of the file (HTTP Range); the answer is then 206. */
    private static HttpURLConnection connect(String url, String auth, String accept, long from) throws IOException {
        String first = null;
        for (int hops = 0; hops < 6; hops++) {
            if (!url.startsWith("https://")) throw new IOException("only https");
            URL u = new URL(url);
            if (first == null) first = u.getAuthority();
            HttpURLConnection con = (HttpURLConnection) u.openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);
            con.setRequestProperty("User-Agent", "NovaOS-Mobile (https://github.com/superali99-tech/Hanse-1-Betriebssystem-)");  // Wikimedia asks for a contact
            if (auth != null && u.getAuthority().equals(first)) con.setRequestProperty("Authorization", auth);
            if (accept != null) con.setRequestProperty("Accept", accept);
            if (from > 0) con.setRequestProperty("Range", "bytes=" + from + "-");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = con.getHeaderField("Location");
                con.disconnect();
                if (location == null) throw new IOException("redirect without target");
                url = new URL(u, location).toString();
                continue;
            }
            if (code != 200 && !(code == 206 && from > 0)) {
                con.disconnect();
                throw new HttpException(code);
            }
            return con;
        }
        throw new IOException("too many redirects");
    }
}
