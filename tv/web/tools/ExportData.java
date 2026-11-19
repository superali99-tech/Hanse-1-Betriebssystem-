package org.novaos.tv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Builds the data of NovaOS TV Web (runs in GitHub Actions, not on the TV): the channel
 * list from res/raw/sender.m3u and the Nova Filme rows from the YouTube channel feeds -
 * with exactly the logic of the Android app (Catalog, Video). TV browsers may not read
 * the YouTube feeds themselves (no CORS), so the website gets them as JSON files.
 *
 *   java org.novaos.tv.ExportData tv/res/raw/sender.m3u OUTDIR
 */
public final class ExportData {
    private ExportData() {}

    public static void main(String[] args) throws Exception {
        Path m3u = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);

        List<Channel> channels = Channel.parse(new String(Files.readAllBytes(m3u), StandardCharsets.UTF_8));
        StringBuilder sj = new StringBuilder("[\n");
        for (int i = 0; i < channels.size(); i++) {
            Channel c = channels.get(i);
            sj.append("  {\"number\": ").append(c.number)
                    .append(", \"name\": ").append(json(c.name))
                    .append(", \"group\": ").append(json(c.group))
                    .append(", \"color\": ").append(json(String.format("#%06X", c.color & 0xFFFFFF)))
                    .append(", \"url\": ").append(json(c.url)).append('}')
                    .append(i + 1 < channels.size() ? ",\n" : "\n");
        }
        Files.write(out.resolve("sender.json"), sj.append("]\n").toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("sender.json: " + channels.size() + " channels");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<List<Video>>> jobs = new ArrayList<>();
        for (Catalog.Source s : Catalog.SOURCES) {
            jobs.add(pool.submit(() -> Video.parseFeed(new String(get(s.feedUrl()), StandardCharsets.UTF_8), s)));
        }
        List<List<Video>> feeds = new ArrayList<>();
        int failed = 0;
        for (int i = 0; i < jobs.size(); i++) {
            try {
                feeds.add(jobs.get(i).get());
            } catch (Exception e) {
                failed++;
                System.out.println("feed failed: " + Catalog.SOURCES.get(i).name + " (" + e.getCause() + ")");
            }
        }
        if (failed == jobs.size()) throw new IOException("no YouTube feed could be loaded");

        Map<Catalog.Category, List<Video>> rows = Catalog.rows(feeds);
        // only videos whose owners allow the embedded player (the website plays them inline)
        List<Video> all = new ArrayList<>();
        for (List<Video> l : rows.values()) all.addAll(l);
        List<Future<Boolean>> checks = new ArrayList<>();
        for (Video v : all) checks.add(pool.submit(() -> embeddable(v)));
        java.util.Set<String> blocked = new java.util.HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            if (!checks.get(i).get()) blocked.add(all.get(i).id);
        }
        pool.shutdown();

        StringBuilder fj = new StringBuilder("{\n  \"generated\": ").append(json(Instant.now().toString()))
                .append(",\n  \"rows\": [\n");
        int total = 0;
        boolean firstRow = true;
        for (Map.Entry<Catalog.Category, List<Video>> e : rows.entrySet()) {
            List<Video> keep = new ArrayList<>();
            for (Video v : e.getValue()) if (!blocked.contains(v.id)) keep.add(v);
            if (keep.isEmpty()) continue;
            if (!firstRow) fj.append(",\n");
            firstRow = false;
            fj.append("    {\"category\": ").append(json(e.getKey().name())).append(", \"videos\": [\n");
            for (int i = 0; i < keep.size(); i++) {
                Video v = keep.get(i);
                fj.append("      {\"id\": ").append(json(v.id))
                        .append(", \"title\": ").append(json(Catalog.cleanTitle(v.title, v.source.name)))
                        .append(", \"source\": ").append(json(v.source.name))
                        .append(", \"published\": ").append(json(v.published)).append('}')
                        .append(i + 1 < keep.size() ? ",\n" : "\n");
            }
            fj.append("    ]}");
            total += keep.size();
        }
        fj.append("\n  ]\n}\n");
        Files.write(out.resolve("filme.json"), fj.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("filme.json: " + total + " videos (" + blocked.size() + " not embeddable, "
                + failed + " feeds failed)");
    }

    private static boolean embeddable(Video v) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(v.oembedUrl()).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "NovaOS-TV");
            int code = con.getResponseCode();
            con.disconnect();
            return !(code == 401 || code == 403 || code == 404);
        } catch (IOException e) {
            return true;  // unknown: keep the video
        }
    }

    private static byte[] get(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", "NovaOS-TV");
        con.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
        int code = con.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " " + url);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally {
            con.disconnect();
        }
    }

    /** JSON string literal (escapes quotes, backslashes, control characters and "<"). */
    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '<': b.append("\\u003c"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }
}
