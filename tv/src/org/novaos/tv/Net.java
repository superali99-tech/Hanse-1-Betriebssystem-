package org.novaos.tv;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Downloads for Nova Filme (YouTube feeds and preview pictures) with a small
 * file cache, so the lists also appear without internet. No cookies, no IDs:
 * only the public feed/picture address is requested.
 */
final class Net {
    private Net() {}

    static final int TIMEOUT_MS = 12000;
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    static byte[] get(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(TIMEOUT_MS);
        con.setReadTimeout(TIMEOUT_MS);
        con.setUseCaches(false);
        con.setRequestProperty("User-Agent", "NovaOS-TV");
        con.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.5");
        try {
            int code = con.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code + " " + url);
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) throw new IOException("too large: " + url);
                }
                return out.toByteArray();
            }
        } finally {
            con.disconnect();
        }
    }

    static File cacheFile(Context c, String dir, String name) {
        File d = new File(c.getCacheDir(), dir);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, name);
    }

    static byte[] read(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    static void write(File f, byte[] data) {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /** Result of loading a feed: the text and whether it came fresh from the internet. */
    static final class Feed {
        final String xml;
        final boolean fresh;

        Feed(String xml, boolean fresh) {
            this.xml = xml;
            this.fresh = fresh;
        }
    }

    /**
     * Feed text: from the cache if it is younger than maxAgeMs, otherwise from
     * the internet; if that fails, the older cached copy (or null).
     */
    static Feed feed(Context c, Catalog.Source s, long maxAgeMs) {
        File f = cacheFile(c, "feeds", s.channelId + ".xml");
        long age = System.currentTimeMillis() - f.lastModified();
        if (f.isFile() && age >= 0 && age < maxAgeMs) {
            byte[] b = read(f);
            if (b != null) return new Feed(new String(b, StandardCharsets.UTF_8), true);
        }
        try {
            byte[] b = get(s.feedUrl());
            write(f, b);
            return new Feed(new String(b, StandardCharsets.UTF_8), true);
        } catch (IOException | RuntimeException e) {
            byte[] b = read(f);
            return b == null ? null : new Feed(new String(b, StandardCharsets.UTF_8), false);
        }
    }
}
