package org.novaos.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.novaos.tv.ImageLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Details of a Nova Store app, like in the Play Store: icon, developer, size, version,
 * screenshots, "About this app", and installing with a progress ring around the icon
 * ("42 % of 6.9 MB") that can be cancelled. Details and pictures of F-Droid apps come
 * from F-Droid (res/raw/store_meta.json, made by tools/store_meta.py).
 */
public class StoreDetailActivity extends Activity implements StoreInstall.Listener {
    static final String EXTRA_PKG = "pkg";
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private StoreApp app;
    private StoreMeta meta;
    private long latest = -1;
    private String latestName = "";
    private RingView ring;
    private TextView progressText, sizeValue, versionValue;
    private LinearLayout buttons;
    private BroadcastReceiver packages;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String pkg = getIntent().getStringExtra(EXTRA_PKG);
        app = pkg == null ? null : StoreCatalog.app(this, pkg);
        if (app == null) {
            finish();
            return;
        }
        meta = StoreCatalog.meta(this, pkg);
        LinearLayout content = Page.setup(this, R.string.app_store, "");
        build(content);
        NovaAppDetailActivity.addMenu(this, () -> {
            newsShown = null;
            showNews();
        });
        packages = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                Uri d = i.getData();
                if (d != null && app.pkg.equals(d.getSchemeSpecificPart())) refresh();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        if (app.fromFdroid()) {  // the current version (the details may be a few days older)
            worker.execute(() -> {
                try {
                    String json = Download.text(app.fdroidApi());
                    long code = StoreApp.suggestedCode(json);
                    String name = StoreApp.versionName(json, code);
                    runOnUiThread(() -> {
                        latest = code;
                        latestName = name;
                        refresh();
                    });
                } catch (IOException ignored) {
                    // offline: what store_meta.json knows
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        StoreInstall.addListener(this);
        StoreInstall.cleanup(this);
        refresh();
    }

    @Override
    protected void onPause() {
        StoreInstall.removeListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (packages != null) unregisterReceiver(packages);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void changed(String pkg, String message) {
        if (!app.pkg.equals(pkg) || isDestroyed()) return;
        if (message != null && !message.isEmpty()) Toast.makeText(this, getString(R.string.store_failed, message), Toast.LENGTH_LONG).show();
        refresh();
    }

    // ------------------------------------------------------------ building the page

    private void build(LinearLayout content) {
        // icon (with the progress ring around it), name, developer
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(this, 8), 0, 0);
        FrameLayout iconBox = new FrameLayout(this);
        ring = new RingView(this);
        ring.setStroke(4);
        ring.setVisibility(View.INVISIBLE);
        iconBox.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View icon = StoreCatalog.icon(this, app, 64);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64));
        il.gravity = Gravity.CENTER;
        iconBox.addView(icon, il);
        head.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 88)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, app.name, 24, Color.WHITE, true);
        names.addView(name);
        String author = meta != null && !meta.author.isEmpty() ? meta.author : getString(app.fromFdroid() ? R.string.store_source_fdroid : R.string.store_source_play);
        names.addView(Ui.text(this, author, 15, 0xFFB9A8FF, true));
        progressText = Ui.text(this, getString(app.fromFdroid() ? R.string.store_detail_free : R.string.store_detail_play), 13,
                getColor(R.color.text_muted), false);
        names.addView(progressText);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nl.leftMargin = Ui.dp(this, 14);
        head.addView(names, nl);
        content.addView(head);

        // facts in one row, like the Play Store (no ratings: F-Droid has none, none are made up)
        LinearLayout facts = new LinearLayout(this);
        facts.setGravity(Gravity.CENTER_VERTICAL);
        facts.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
        if (meta != null) {
            sizeValue = fact(facts, UpdateInfo.size(meta.size, german), getString(R.string.store_detail_size), false);
            versionValue = fact(facts, meta.version, getString(R.string.store_detail_version), true);
            if (meta.minSdk > 0) fact(facts, androidName(meta.minSdk) + "+", getString(R.string.store_detail_android_short), true);
            if (!meta.license.isEmpty()) fact(facts, shortLicense(meta.license), getString(R.string.store_detail_license), true);
        } else {
            fact(facts, app.fromFdroid() ? "F-Droid" : "Google Play", getString(R.string.store_detail_source), false);
            fact(facts, getString(category(app.category)), getString(R.string.store_detail_category), true);
        }
        HorizontalScrollView fs = new HorizontalScrollView(this);
        fs.setHorizontalScrollBarEnabled(false);
        fs.addView(facts);
        content.addView(fs);

        buttons = new LinearLayout(this);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 12);
        content.addView(buttons, bl);
        // "Neuerungen •" (3 hours after an update, or when a newer version waits)
        newsBox = new LinearLayout(this);
        newsBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(newsBox);

        // screenshots
        if (meta != null && !meta.shots.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = new LinearLayout(this);
            for (String shot : meta.shots) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundResource(R.drawable.card_bg);
                rounded(iv, 14);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 132), Ui.dp(this, 264));
                sl.rightMargin = Ui.dp(this, 10);
                strip.addView(iv, sl);
                String url = "https://f-droid.org/repo" + shot;
                loadShot(iv, url, Ui.dp(this, 264));
                iv.setOnClickListener(v -> showShot(url));
            }
            hs.addView(strip);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.topMargin = Ui.dp(this, 20);
            content.addView(hs, hl);
        }

        // about this app
        LinearLayout aboutHead = new LinearLayout(this);
        aboutHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView aboutTitle = Ui.section(this, R.string.store_detail_about);
        aboutHead.addView(aboutTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = Ui.text(this, "→", 22, Color.WHITE, false);
        arrow.setPadding(0, Ui.dp(this, 14), Ui.dp(this, 6), 0);
        aboutHead.addView(arrow);
        content.addView(aboutHead);
        String summary = meta != null ? (german && !meta.summaryDe.isEmpty() ? meta.summaryDe : meta.summaryEn) : "";
        TextView sum = Ui.text(this, summary.isEmpty() ? app.text(german) : summary, 16, Color.WHITE, true);
        content.addView(sum);
        String desc = meta != null ? (german && !meta.descDe.isEmpty() ? meta.descDe : meta.descEn) : "";
        if (desc.isEmpty() && !summary.isEmpty()) desc = app.text(german);
        if (!desc.isEmpty()) {
            TextView d = Ui.text(this, desc, 15, 0xE6FFFFFF, false);
            d.setLineSpacing(0, 1.15f);
            d.setMaxLines(5);
            d.setEllipsize(TextUtils.TruncateAt.END);
            d.setPadding(0, Ui.dp(this, 6), 0, 0);
            content.addView(d);
            View.OnClickListener toggle = v -> {
                boolean open = d.getMaxLines() > 5;
                d.setMaxLines(open ? 5 : Integer.MAX_VALUE);
                arrow.setText(open ? "→" : "↓");
            };
            aboutHead.setOnClickListener(toggle);
            d.setOnClickListener(toggle);
        }
        // category chip
        TextView chip = Ui.text(this, getString(category(app.category)), 13, Color.WHITE, false);
        GradientDrawable cb = new GradientDrawable();
        cb.setCornerRadius(Ui.dp(this, 16));
        cb.setStroke(Ui.dp(this, 1), 0x66FFFFFF);
        chip.setBackground(cb);
        chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(this, 14);
        content.addView(chip, cl);

        if (meta != null) {
            // notes of F-Droid ("anti-features"), e.g. non-free data sources
            for (String anti : meta.anti) {
                TextView a = Ui.text(this, "⚠  " + anti, 14, 0xFFFFE2A8, false);
                a.setBackgroundResource(R.drawable.card_bg);
                a.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                al.topMargin = Ui.dp(this, 12);
                content.addView(a, al);
            }
            // app info
            content.addView(Ui.section(this, R.string.store_detail_info));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setBackgroundResource(R.drawable.card_bg);
            info.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
            infoRow(info, R.string.store_detail_version, meta.version);
            if (meta.updated > 0) infoRow(info, R.string.store_detail_updated, date(meta.updated));
            if (meta.added > 0) infoRow(info, R.string.store_detail_added, date(meta.added));
            infoRow(info, R.string.store_detail_size, UpdateInfo.size(meta.size, german));
            if (meta.minSdk > 0) infoRow(info, R.string.store_detail_android, getString(R.string.store_detail_android_value, androidName(meta.minSdk)));
            infoRow(info, R.string.store_detail_permissions, String.valueOf(meta.permissions));
            if (!meta.license.isEmpty()) infoRow(info, R.string.store_detail_license, meta.license);
            infoRow(info, R.string.store_detail_developer, meta.author.isEmpty() ? "—" : meta.author);
            content.addView(info);
            if (!meta.web.isEmpty()) content.addView(link(R.string.store_detail_website, meta.web));
            if (!meta.source.isEmpty() && !meta.source.equals(meta.web)) content.addView(link(R.string.store_detail_code, meta.source));
            TextView credit = Ui.text(this, getString(R.string.store_detail_credit), 12, getColor(R.color.text_muted), false);
            credit.setPadding(Ui.dp(this, 2), Ui.dp(this, 18), 0, Ui.dp(this, 8));
            content.addView(credit);
        }
    }

    private TextView fact(LinearLayout row, String value, String label, boolean divider) {
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(0x33FFFFFF);
            row.addView(line, new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 30)));
        }
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        TextView v = Ui.text(this, value, 15, Color.WHITE, true);
        v.setSingleLine(true);
        cell.addView(v);
        cell.addView(Ui.text(this, label, 12, getColor(R.color.text_muted), false));
        row.addView(cell);
        return v;
    }

    private void infoRow(LinearLayout box, int label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        r.addView(Ui.text(this, getString(label), 14, getColor(R.color.text_muted), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = Ui.text(this, value, 14, Color.WHITE, false);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        box.addView(r);
    }

    private TextView link(int text, String url) {
        TextView t = Ui.text(this, "↗  " + getString(text), 15, 0xFF9DB8FF, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, 0);
        t.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        });
        return t;
    }

    private String date(long ms) {
        return DateUtils.formatDateTime(this, ms, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR);
    }

    static String androidName(int sdk) {
        String[] names = {"", "1.0", "1.1", "1.5", "1.6", "2.0", "2.0.1", "2.1", "2.2", "2.3", "2.3.3", "3.0", "3.1", "3.2", "4.0",
                "4.0.3", "4.1", "4.2", "4.3", "4.4", "4.4W", "5.0", "5.1", "6.0", "7.0", "7.1", "8.0", "8.1", "9", "10", "11", "12",
                "12L", "13", "14", "15", "16"};
        return sdk > 0 && sdk < names.length ? names[sdk] : "API " + sdk;
    }

    private static String shortLicense(String l) {
        return l.replace("-only", "").replace("-or-later", "+");
    }

    private int category(String id) {
        return StoreCatalog.category(id);
    }

    // ------------------------------------------------------------ state: install, update, open, progress

    @SuppressWarnings("deprecation")
    private long installedCode() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(app.pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private String mode = "";  // the buttons are only rebuilt when this changes (a tap must not hit a replaced button)

    private LinearLayout newsBox;

    private String newsShown = null;

    private void showNews() {
        if (newsBox == null) return;
        AppNews.Item news = AppNews.fdroid(this, app, latest, latestName);
        String sig = news == null ? "" : news.upcoming + "|" + news.version + "|" + news.lines.size();
        if (sig.equals(newsShown)) return;  // unchanged (refresh runs often while downloading)
        newsShown = sig;
        newsBox.removeAllViews();
        if (news != null) newsBox.addView(NovaAppDetailActivity.newsBlock(this, news, () -> startActivity(new Intent(this, NewsActivity.class)
                .putExtra(NewsActivity.EXTRA_PKG, app.pkg).putExtra(NewsActivity.EXTRA_LATEST, latest).putExtra(NewsActivity.EXTRA_LATEST_NAME, latestName))));
    }

    private void refresh() {
        if (buttons == null) return;
        showNews();
        long installed = installedCode();
        StoreInstall.State s = StoreInstall.state(app.pkg);
        if (latest > 0 && versionValue != null && !latestName.isEmpty()) versionValue.setText(latestName);
        if (s != null) {
            // downloading: ring around the icon, "42 % of 6.9 MB", cancel
            ring.setVisibility(View.VISIBLE);
            if (s.cancelled) {
                progressText.setText(R.string.store_detail_cancelling);
            } else if (s.installing || s.checking) {
                ring.set(-1);
                progressText.setText(s.installing ? R.string.store_detail_installing : R.string.store_detail_checking);
            } else if (s.total > 0) {
                ring.set(s.done / (float) s.total);
                progressText.setText(getString(R.string.store_detail_progress, s.percent(), UpdateInfo.size(s.total, german)));
                if (sizeValue != null) sizeValue.setText(UpdateInfo.size(s.total, german));
            } else {
                ring.set(-1);
                progressText.setText(R.string.store_detail_waiting);
            }
            String m = s.installing ? "installing" : "loading";
            if (!m.equals(mode)) {
                mode = m;
                buttons.removeAllViews();
                Button cancel = button(R.string.cancel, false, v -> {
                    StoreInstall.cancel(app.pkg);
                    progressText.setText(R.string.store_detail_cancelling);
                });
                cancel.setEnabled(!s.installing);
                cancel.setAlpha(s.installing ? 0.4f : 1f);
                buttons.addView(cancel, weight(true));
                Button open = button(R.string.store_open, true, null);
                open.setEnabled(false);
                open.setAlpha(0.4f);
                buttons.addView(open, weight(false));
            }
            return;
        }
        ring.setVisibility(View.INVISIBLE);
        progressText.setText(getString(app.fromFdroid() ? R.string.store_detail_free : R.string.store_detail_play));
        boolean update = installed >= 0 && app.fromFdroid() && latest > installed;
        if (update) progressText.setText(getString(R.string.store_detail_update_ready, latestName));
        String m = installed >= 0 ? (update ? "update" : "installed") : app.fromFdroid() ? "install" : "play";
        if (m.equals(mode)) return;
        mode = m;
        buttons.removeAllViews();
        if (installed >= 0) {
            buttons.addView(button(R.string.store_uninstall, false, v -> uninstall()), weight(true));
            buttons.addView(button(update ? R.string.store_update : R.string.store_open, true, v -> {
                if (update) install();
                else open();
            }), weight(false));
        } else if (app.fromFdroid()) {
            buttons.addView(button(R.string.store_install, true, v -> install()), weight(false));
        } else {
            buttons.addView(button(R.string.store_play, true, v -> openPlay()), weight(false));
        }
    }

    private LinearLayout.LayoutParams weight(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1);
        if (left) lp.rightMargin = Ui.dp(this, 10);
        return lp;
    }

    private Button button(int text, boolean accent, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        if (accent) {
            b.setBackgroundResource(R.drawable.accent_card_bg);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 23));
            bg.setStroke(Ui.dp(this, 1), 0x88FFFFFF);
            b.setBackground(bg);
        }
        if (click != null) b.setOnClickListener(click);
        return b;
    }

    private void install() {
        if (latest <= 0) {
            Toast.makeText(this, R.string.store_offline, Toast.LENGTH_LONG).show();
            return;
        }
        if (!Installer.allowedOrAsk(this)) {
            Toast.makeText(this, R.string.update_allow, Toast.LENGTH_LONG).show();
            return;
        }
        StoreInstall.start(this, app, latest);
    }

    private void open() {
        Intent open = getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (open != null) startActivity(open);
    }

    private void uninstall() {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private void openPlay() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app.pkg)).setPackage("com.android.vending"));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(app.playUrl())));
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------ screenshots (scaled down, cached)

    private static void rounded(View v, int dp) {
        float r = dp * v.getResources().getDisplayMetrics().density;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    private void loadShot(ImageView iv, String url, int heightPx) {
        iv.setTag(url);
        worker.execute(() -> {
            Bitmap b = shot(url, heightPx);
            if (b != null) runOnUiThread(() -> {
                if (url.equals(iv.getTag())) iv.setImageBitmap(b);
            });
        });
    }

    private Bitmap shot(String url, int heightPx) {
        File dir = new File(getCacheDir(), "store-shots");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (!f.exists()) {
            File part = new File(f.getPath() + ".part");
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(20000);
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[65536];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > 8L << 20) throw new IOException("too large");
                        out.write(buf, 0, n);
                    }
                } finally {
                    con.disconnect();
                }
                if (!part.renameTo(f)) return null;
            } catch (IOException | RuntimeException e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                return null;
            }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outHeight <= 0) return null;
        o.inSampleSize = 1;
        while (o.outHeight / (o.inSampleSize * 2) >= heightPx) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private void showShot(String url) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(Color.BLACK);
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        d.show();
        loadShot(iv, url, getResources().getDisplayMetrics().heightPixels);
    }

    /** Loads the app's F-Droid icon (used by StoreCatalog.icon). */
    static void loadIcon(ImageView iv, StoreApp a, Runnable loaded) {
        ImageLoader.load(iv, a.fdroidIcon(), null, loaded);
    }
}
