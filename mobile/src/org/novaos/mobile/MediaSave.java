package org.novaos.mobile;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Saving an edited photo or a shortened video as a copy next to the camera's photos (DCIM/NovaOS). */
final class MediaSave {
    interface Writer {
        void write(OutputStream out) throws IOException;
    }

    private MediaSave() {}

    /** "IMG_123.jpg" -> "IMG_123_bearbeitet.jpg" (keeps the extension given). */
    static String copyName(String original, String suffix, String ext) {
        String base = original == null ? "NovaOS" : original;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base + "_" + suffix + "." + ext;
    }

    static void save(Context c, String name, boolean video, Writer w) throws IOException {
        String mime = video ? "video/mp4" : "image/jpeg";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/NovaOS");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri table = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = c.getContentResolver().insert(table, v);
            if (uri == null) throw new IOException("MediaStore");
            try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("MediaStore");
                w.write(out);
            } catch (IOException | RuntimeException e) {
                c.getContentResolver().delete(uri, null, null);
                throw e;
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(uri, v, null, null);
            return;
        }
        if (c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw new IOException(c.getString(R.string.files_permission));
        }
        @SuppressWarnings("deprecation")
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "NovaOS");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(dir.getPath());
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            w.write(out);
        }
        MediaScannerConnection.scanFile(c.getApplicationContext(), new String[] {f.getPath()}, new String[] {mime}, null);
    }
}
