package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Photos and videos of the phone (MediaStore) for Nova Galerie and Nova Kamera. */
final class Media {
    static final class Item {
        final long id;
        final boolean video;
        final String name;
        final long date;

        Item(long id, boolean video, String name, long date) {
            this.id = id;
            this.video = video;
            this.name = name;
            this.date = date;
        }

        Uri uri() {
            return ContentUris.withAppendedId(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        }
    }

    private Media() {}

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final android.os.Handler UI = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<String, Bitmap>(24 << 20) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    static String[] permissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[] {"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"};
        }
        if (Build.VERSION.SDK_INT >= 30) return new String[] {Manifest.permission.READ_EXTERNAL_STORAGE};
        // Android 7 - 10: deleting a photo needs the write permission (same question for the user)
        return new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }

    static boolean allowed(Context c) {
        for (String p : permissions()) {
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    static void ask(Activity a, int code) {
        a.requestPermissions(permissions(), code);
    }

    /** All photos and videos, newest first. */
    static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        Uri files = MediaStore.Files.getContentUri("external");
        String[] cols = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATE_ADDED};
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN ("
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        try (Cursor cur = c.getContentResolver().query(files, cols, sel, null,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " + MediaStore.Files.FileColumns._ID + " DESC")) {
            while (cur != null && cur.moveToNext()) {
                out.add(new Item(cur.getLong(0), cur.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                        cur.getString(2), cur.getLong(3)));
            }
        } catch (RuntimeException e) {
            // no permission or no media provider
        }
        return out;
    }

    /** Small preview picture (grid, camera button). */
    @SuppressWarnings("deprecation")
    static void thumbnail(ImageView view, Item item) {
        String key = (item.video ? "v" : "i") + item.id;
        view.setTag(key);
        Bitmap cached = THUMBS.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        ContentResolver cr = view.getContext().getContentResolver();
        POOL.execute(() -> {
            Bitmap b = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    b = cr.loadThumbnail(item.uri(), new Size(320, 320), null);
                } else if (item.video) {
                    b = MediaStore.Video.Thumbnails.getThumbnail(cr, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                } else {
                    b = MediaStore.Images.Thumbnails.getThumbnail(cr, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                }
            } catch (IOException | RuntimeException ignored) {
                // no preview
            }
            if (b == null) return;
            THUMBS.put(key, b);
            Bitmap shown = b;
            UI.post(() -> {
                if (key.equals(view.getTag())) view.setImageBitmap(shown);
            });
        });
    }

    /** The picture in screen size (sampled down, rotated as taken). */
    static Bitmap picture(Context c, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (o.outWidth / sample > maxSide * 2 || o.outHeight / sample > maxSide * 2) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, d);
            }
            if (b == null) return null;
            int rot = orientation(c, uri);
            if (rot == 0) return b;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(rot);
            return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static int orientation(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            android.media.ExifInterface exif = new android.media.ExifInterface(in);
            switch (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
