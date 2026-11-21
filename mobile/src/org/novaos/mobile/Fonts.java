package org.novaos.mobile;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;

/**
 * The NovaOS type: Inter (SIL Open Font License 1.1, res/raw/inter_license.txt), regular and
 * semibold. Android 8+ reads it from res/font; Android 7 gets a copy in the cache folder.
 */
final class Fonts {
    private static Typeface regular, bold;

    private Fonts() {}

    static Typeface regular(Context c) {
        if (regular == null) regular = load(c, R.font.inter_regular, "inter_regular.ttf", Typeface.DEFAULT);
        return regular;
    }

    static Typeface bold(Context c) {
        if (bold == null) bold = load(c, R.font.inter_semibold, "inter_semibold.ttf", Typeface.DEFAULT_BOLD);
        return bold;
    }

    private static Typeface load(Context c, int res, String file, Typeface fallback) {
        try {
            if (Build.VERSION.SDK_INT >= 26) return c.getResources().getFont(res);
            java.io.File f = new java.io.File(c.getCacheDir(), file);
            if (!f.exists()) {
                try (java.io.InputStream in = c.getResources().openRawResource(res);
                     java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            return Typeface.createFromFile(f);
        } catch (Exception | Error e) {
            return fallback;  // the phone's own type
        }
    }
}
