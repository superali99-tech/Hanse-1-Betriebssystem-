package org.novaos.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Hands files of Nova Dateien to other apps (open, share): content://org.novaos.mobile.files/<path>.
 * Only files inside the phone's storage, read-only, and only for the app that got
 * the permission with the intent (grantUriPermissions, not exported).
 */
public class NovaFileProvider extends ContentProvider {
    static final String AUTHORITY = "org.novaos.mobile.files";

    static Uri uri(File f) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String path = f.getAbsolutePath();
        String rel = path.startsWith(root) ? path.substring(root.length()) : path;
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(rel).build();
    }

    static String mime(File f) {
        String t = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileNames.extension(f.getName()));
        return t != null ? t : "application/octet-stream";
    }

    private static File file(Uri uri) throws FileNotFoundException {
        try {
            File root = Environment.getExternalStorageDirectory().getCanonicalFile();
            File f = new File(root, uri.getPath() == null ? "" : uri.getPath()).getCanonicalFile();
            if (!FileNames.inside(root.getPath(), f.getPath()) || !f.isFile()) throw new FileNotFoundException(uri.toString());
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException(uri.toString());
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String order) {
        File f;
        try {
            f = file(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        try {
            return mime(file(uri));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String sel, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String sel, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }
}
