package org.novaos.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads preview pictures in the background; memory + file cache. */
public final class ImageLoader {
    private ImageLoader() {}

    private static final int MAX_FILES = 400;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> MEMORY =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L << 20)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static boolean pruned = false;

    public static void load(ImageView view, String url) {
        load(view, url, null, null);
    }

    /**
     * Like load(view, url), but tries a second address if the first one has no picture,
     * and runs onLoaded (on the UI thread) when a picture is shown.
     */
    public static void load(ImageView view, String url, String fallback, Runnable onLoaded) {
        view.setTag(url);  // plain tag: this class is shared with NovaOS Mobile
        Bitmap cached = MEMORY.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            if (onLoaded != null) onLoaded.run();
            return;
        }
        view.setImageDrawable(null);
        Context app = view.getContext().getApplicationContext();
        POOL.execute(() -> {
            Bitmap bmp = fetch(app, url);
            if (bmp == null && fallback != null) bmp = fetch(app, fallback);
            if (bmp == null) return;
            MEMORY.put(url, bmp);
            Bitmap shown = bmp;
            UI.post(() -> {
                if (!url.equals(view.getTag())) return;
                view.setImageBitmap(shown);
                if (onLoaded != null) onLoaded.run();
            });
        });
    }

    private static Bitmap fetch(Context c, String url) {
        File f = Net.cacheFile(c, "thumbs", Integer.toHexString(url.hashCode()) + ".jpg");
        byte[] data = Net.read(f);
        if (data == null) {
            try {
                data = Net.get(url);
            } catch (IOException | RuntimeException e) {
                return null;
            }
            Net.write(f, data);
            prune(f.getParentFile());
        }
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    /** Keeps the picture cache small: deletes the oldest files above MAX_FILES (once per start). */
    private static synchronized void prune(File dir) {
        if (pruned || dir == null) return;
        pruned = true;
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_FILES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - MAX_FILES; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
