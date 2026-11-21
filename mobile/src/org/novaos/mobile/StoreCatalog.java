package org.novaos.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.novaos.tv.ImageLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** The Nova Store catalogue (res/raw/store.txt) and the F-Droid details (res/raw/store_meta.json), loaded once. */
final class StoreCatalog {
    private static List<StoreApp> apps;
    private static Map<String, StoreMeta> meta;

    private StoreCatalog() {}

    static synchronized List<StoreApp> apps(Context c) {
        if (apps == null) apps = StoreApp.parse(raw(c, R.raw.store));
        return apps;
    }

    static StoreApp app(Context c, String pkg) {
        for (StoreApp a : apps(c)) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    static synchronized StoreMeta meta(Context c, String pkg) {
        if (meta == null) meta = StoreMeta.parseAll(raw(c, R.raw.store_meta));
        return meta.get(pkg);
    }

    static int category(String id) {
        switch (id) {
            case "empfohlen": return R.string.cat_empfohlen;
            case "kommunikation": return R.string.cat_kommunikation;
            case "unterwegs": return R.string.cat_unterwegs;
            case "multimedia": return R.string.cat_multimedia;
            case "sicherheit": return R.string.cat_sicherheit;
            case "unterhaltung": return R.string.cat_unterhaltung;
            default: return R.string.cat_werkzeuge;
        }
    }

    /** Icon: the F-Droid picture, or a coloured letter (Play apps, while loading, offline). */
    static View icon(Context c, StoreApp a, int sizeDp) {
        FrameLayout icon = new FrameLayout(c);
        TextView letter = Ui.text(c, a.name.substring(0, 1), sizeDp * 0.45f, Color.WHITE, true);
        letter.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.HSVToColor(new float[] {Math.abs(a.pkg.hashCode()) % 360, 0.55f, 0.75f}));
        letter.setBackground(bg);
        icon.addView(letter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (a.fromFdroid()) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            StoreMeta m = meta(c, a.pkg);
            // the icon path with the version number exists for every app (the plain one not always)
            String versioned = m != null && m.code > 0 ? a.fdroidIconOld(m.code) : null;
            ImageLoader.load(iv, a.fdroidIcon(), versioned, () -> letter.setVisibility(View.GONE));
        }
        return icon;
    }

    static String raw(Context c, int res) {
        try (InputStream in = c.getResources().openRawResource(res)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
