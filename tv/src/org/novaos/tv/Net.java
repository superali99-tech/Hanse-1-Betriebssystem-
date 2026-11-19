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

    // ------------------------------------------------------------ embedding allowed?
    private static final int MAX_EMBED_ENTRIES = 3000;
    private static java.util.LinkedHashMap<String, Boolean> embed;

    /**
     * Whether a video may be played in the embedded player (Nova Filme plays inside the app).
     * Asked once per video via YouTube's oEmbed (200 = allowed, 401/403/404 = not) and
     * remembered in a small file; if the check fails (no internet), the video is shown.
     */
    static boolean embeddable(Context c, Video v) {
        synchronized (Net.class) {
            loadEmbed(c);
            Boolean known = embed.get(v.id);
            if (known != null) return known;
        }
        int code;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(v.oembedUrl()).openConnection();
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);
            con.setRequestProperty("User-Agent", "NovaOS-TV");
            try {
                code = con.getResponseCode();
            } finally {
                con.disconnect();
            }
        } catch (IOException | RuntimeException e) {
            return true;
        }
        boolean ok;
        if (code == HttpURLConnection.HTTP_OK) ok = true;
        else if (code == 401 || code == 403 || code == 404) ok = false;
        else return true;  // unknown answer: do not hide the video
        synchronized (Net.class) {
            embed.put(v.id, ok);
        }
        return ok;
    }

    private static void loadEmbed(Context c) {
        if (embed != null) return;
        embed = new java.util.LinkedHashMap<>();
        byte[] b = read(cacheFile(c, "feeds", "embeddable.txt"));
        if (b == null) return;
        for (String line : new String(b, StandardCharsets.UTF_8).split("\n")) {
            String[] p = line.trim().split(" ");
            if (p.length == 2 && Video.VIDEO_ID.matcher(p[0]).matches()) embed.put(p[0], "1".equals(p[1]));
        }
    }

    /** Saves the remembered answers (the newest MAX_EMBED_ENTRIES). */
    static synchronized void saveEmbed(Context c) {
        if (embed == null) return;
        StringBuilder sb = new StringBuilder();
        int skip = Math.max(0, embed.size() - MAX_EMBED_ENTRIES);
        for (java.util.Map.Entry<String, Boolean> e : embed.entrySet()) {
            if (skip-- > 0) continue;
            sb.append(e.getKey()).append(e.getValue() ? " 1\n" : " 0\n");
        }
        write(cacheFile(c, "feeds", "embeddable.txt"), sb.toString().getBytes(StandardCharsets.UTF_8));
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
