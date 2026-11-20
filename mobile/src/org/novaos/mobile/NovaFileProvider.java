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
 * Hands files of Nova Dateien to other apps (open, share): content://org.novaos.mobile.files/<path>;
 * files of Nova Chat and Nova Mail (in NovaOS's cache): content://org.novaos.mobile.files/_cache/<folder>/<name>.
 * Only files inside the phone's storage, read-only, and only for the app that got
 * the permission with the intent (grantUriPermissions, not exported).
 */
public class NovaFileProvider extends ContentProvider {
    static final String AUTHORITY = "org.novaos.mobile.files";

    /** Cache folders other apps may read files from (one file per intent). */
    static final String[] CACHE_FOLDERS = {"chat-media", "mail-files"};

    static Uri uri(File f) {
        File parent = f.getParentFile();
        if (parent != null && java.util.Arrays.asList(CACHE_FOLDERS).contains(parent.getName())
                && parent.getParentFile() != null && parent.getParentFile().getName().equals("cache")) {
            return new Uri.Builder().scheme("content").authority(AUTHORITY).path("/_cache/" + parent.getName() + "/" + f.getName()).build();
        }
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String path = f.getAbsolutePath();
        String rel = path.startsWith(root) ? path.substring(root.length()) : path;
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(rel).build();
    }

    static String mime(File f) {
        String t = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileNames.extension(f.getName()));
        return t != null ? t : "application/octet-stream";
    }

    private File file(Uri uri) throws FileNotFoundException {
        String p = uri.getPath() == null ? "" : uri.getPath();
        if (p.startsWith("/_cache/")) {
            String[] parts = p.substring(8).split("/");
            if (parts.length != 2 || !java.util.Arrays.asList(CACHE_FOLDERS).contains(parts[0]) || parts[1].startsWith(".")
                    || getContext() == null) {
                throw new FileNotFoundException(uri.toString());
            }
            try {
                File dir = new File(getContext().getCacheDir(), parts[0]).getCanonicalFile();
                File f = new File(dir, parts[1]).getCanonicalFile();
                if (!FileNames.inside(dir.getPath(), f.getPath()) || !f.isFile()) throw new FileNotFoundException(uri.toString());
                return f;
            } catch (IOException e) {
                throw new FileNotFoundException(uri.toString());
            }
        }
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
