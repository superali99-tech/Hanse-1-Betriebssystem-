package org.novaos.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Photos, voice messages and files of Nova Chat on the phone: downloaded once from the
 * Matrix server into the cache (at most about 300 MB, oldest go first).
 */
final class ChatMedia {
    interface Done {
        void done(File file);  // null = not possible
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final Map<String, List<Done>> LOADING = new HashMap<>();
    private static final LruCache<String, Bitmap> BITMAPS = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    private ChatMedia() {}

    static File dir(Context c) {
        File d = new File(c.getCacheDir(), "chat-media");
        if (!d.isDirectory() && !d.mkdirs()) return c.getCacheDir();
        return d;
    }

    static File file(Context c, String mxc, int thumbPx, String mime) {
        return new File(dir(c), hash(mxc + "|" + thumbPx) + extension(mime));
    }

    static String extension(String mime) {
        if (mime == null) return "";
        switch (mime) {
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "audio/mp4": case "audio/x-m4a": case "audio/aac": return ".m4a";
            case "audio/ogg": return ".ogg";
            case "audio/mpeg": return ".mp3";
            case "audio/webm": return ".webm";
            case "video/mp4": return ".mp4";
            case "application/pdf": return ".pdf";
            default: return "";
        }
    }

    /** The file of mxc (download first if needed); done runs on the main thread. */
    static void get(Context c, String mxc, int thumbPx, String mime, Done done) {
        Context app = c.getApplicationContext();
        File f = file(app, mxc, thumbPx, mime);
        if (f.isFile() && f.length() > 0) {
            done.done(f);
            return;
        }
        String key = f.getPath();
        synchronized (LOADING) {
            List<Done> waiting = LOADING.get(key);
            if (waiting != null) {
                waiting.add(done);
                return;
            }
            LOADING.put(key, new ArrayList<>(Arrays.asList(done)));
        }
        POOL.execute(() -> {
            File result = null;
            try {
                ChatDb.api(app).download(mxc, f, thumbPx);
                result = f;
            } catch (IOException | RuntimeException e) {
                if (thumbPx > 0) {  // no preview on this server: the photo itself
                    try {
                        File full = file(app, mxc, 0, mime);
                        if (!full.isFile()) ChatDb.api(app).download(mxc, full, 0);
                        result = full;
                    } catch (IOException | RuntimeException ignored) {
                        result = null;
                    }
                }
            }
            trim(dir(app), 300L * 1024 * 1024);
            File r = result;
            List<Done> all;
            synchronized (LOADING) {
                all = LOADING.remove(key);
            }
            UI.post(() -> {
                if (all != null) for (Done d : all) d.done(r);
            });
        });
    }

    /** Photo scaled to at most maxPx (memory cache). */
    static Bitmap bitmap(File f, int maxPx) {
        String key = f.getPath() + "|" + maxPx;
        Bitmap b = BITMAPS.get(key);
        if (b != null) return b;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxPx || o.outHeight / (sample * 2) >= maxPx) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        b = BitmapFactory.decodeFile(f.getPath(), d);
        if (b != null) BITMAPS.put(key, b);
        return b;
    }

    static void trim(File dir, long max) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= max) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= max * 8 / 10) break;
            total -= f.length();
            if (!f.delete()) f.deleteOnExit();
        }
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
