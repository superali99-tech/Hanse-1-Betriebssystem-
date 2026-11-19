package org.novaos.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.novaos.tv.ImageLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nova Store: real apps. Free and open-source apps are installed directly from
 * F-Droid (current version from the F-Droid API, APK from f-droid.org, Android's
 * installer asks for confirmation); popular apps open in the Google Play Store.
 */
public class StoreActivity extends Activity {
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final Map<String, Long> fdroidCode = new HashMap<>();
    private final Map<String, String> fdroidName = new HashMap<>();
    private final Map<String, List<Button>> buttons = new HashMap<>();
    private final Map<String, StoreApp> apps = new HashMap<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private BroadcastReceiver packages;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.app_store, getString(R.string.store_sub));
        List<StoreApp> list = StoreApp.parse(raw(R.raw.store));
        for (StoreApp a : list) apps.put(a.pkg, a);
        for (Map.Entry<String, List<StoreApp>> sec : StoreApp.sections(list).entrySet()) {
            content.addView(Ui.section(this, category(sec.getKey())));
            for (StoreApp a : sec.getValue()) content.addView(row(a));
        }
        Installer.setListener((pkg, ok, message) -> runOnUiThread(() -> {
            progress.remove(pkg);
            if (!ok && message != null) Toast.makeText(this, getString(R.string.store_failed, message), Toast.LENGTH_LONG).show();
            refresh(pkg);
        }));
        packages = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                Uri d = i.getData();
                if (d != null) refresh(d.getSchemeSpecificPart());
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        // current F-Droid versions (small JSON answer per app)
        for (StoreApp a : list) {
            if (!a.fromFdroid()) continue;
            worker.execute(() -> {
                try {
                    String json = Download.text(a.fdroidApi());
                    long code = StoreApp.suggestedCode(json);
                    if (code > 0) {
                        String name = StoreApp.versionName(json, code);
                        runOnUiThread(() -> {
                            fdroidCode.put(a.pkg, code);
                            fdroidName.put(a.pkg, name);
                            refresh(a.pkg);
                        });
                    }
                } catch (IOException ignored) {
                    // offline: the app can still be opened if installed
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (String pkg : apps.keySet()) refresh(pkg);
    }

    @Override
    protected void onDestroy() {
        Installer.setListener(null);
        unregisterReceiver(packages);
        worker.shutdownNow();
        super.onDestroy();
    }

    private int category(String id) {
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

    private View row(StoreApp a) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 12);
        r.setPadding(p, p, p, p);

        // icon: F-Droid picture, or a coloured letter (Play apps, while loading)
        FrameLayout icon = new FrameLayout(this);
        TextView letter = Ui.text(this, a.name.substring(0, 1), 22, Color.WHITE, true);
        letter.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.HSVToColor(new float[] {Math.abs(a.pkg.hashCode()) % 360, 0.55f, 0.75f}));
        letter.setBackground(bg);
        icon.addView(letter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (a.fromFdroid()) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ImageLoader.load(iv, a.fdroidIcon(), null, () -> letter.setVisibility(View.GONE));
        }
        r.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, a.name, 16, Color.WHITE, true));
        TextView d = Ui.text(this, a.text(german), 13, getColor(R.color.text_muted), false);
        d.setMaxLines(2);
        d.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(d);
        TextView src = Ui.text(this, getString(a.fromFdroid() ? R.string.store_source_fdroid : R.string.store_source_play),
                11, 0xFFB9A8FF, false);
        src.setTag("source:" + a.pkg);
        texts.addView(src);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 12);
        tl.rightMargin = Ui.dp(this, 8);
        r.addView(texts, tl);

        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinWidth(Ui.dp(this, 96));
        b.setBackgroundResource(R.drawable.accent_card_bg);
        b.setOnClickListener(v -> act(a));
        r.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42)));
        List<Button> l = buttons.get(a.pkg);
        if (l == null) buttons.put(a.pkg, l = new ArrayList<>());
        l.add(b);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);
        return r;
    }

    @SuppressWarnings("deprecation")
    private long installedCode(String pkg) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /** Button text: install / update / open / Play Store / progress. */
    private void refresh(String pkg) {
        StoreApp a = apps.get(pkg);
        List<Button> l = buttons.get(pkg);
        if (a == null || l == null) return;
        long installed = installedCode(pkg);
        Long latest = fdroidCode.get(pkg);
        Integer pct = progress.get(pkg);
        String text;
        if (pct != null) text = getString(R.string.store_progress, pct);
        else if (installed >= 0 && a.fromFdroid() && latest != null && latest > installed) text = getString(R.string.store_update);
        else if (installed >= 0) text = getString(R.string.store_open);
        else text = getString(a.fromFdroid() ? R.string.store_install : R.string.store_play);
        for (Button b : l) {
            b.setText(text);
            b.setEnabled(pct == null);
        }
        View root = findViewById(R.id.content);
        String name = fdroidName.get(pkg);
        if (name != null && !name.isEmpty()) {
            for (View v : findAll(root, "source:" + pkg)) {
                ((TextView) v).setText(getString(R.string.store_source_fdroid) + " · " + name);
            }
        }
    }

    private static List<View> findAll(View root, Object tag) {
        List<View> out = new ArrayList<>();
        if (tag.equals(root.getTag())) out.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) out.addAll(findAll(g.getChildAt(i), tag));
        }
        return out;
    }

    private void act(StoreApp a) {
        long installed = installedCode(a.pkg);
        Long latest = fdroidCode.get(a.pkg);
        boolean update = installed >= 0 && a.fromFdroid() && latest != null && latest > installed;
        if (installed >= 0 && !update) {
            Intent open = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (open != null) startActivity(open);
            return;
        }
        if (!a.fromFdroid()) {
            openPlay(a);
            return;
        }
        if (latest == null) {
            Toast.makeText(this, R.string.store_offline, Toast.LENGTH_LONG).show();
            return;
        }
        if (!Installer.allowedOrAsk(this)) {
            Toast.makeText(this, R.string.update_allow, Toast.LENGTH_LONG).show();
            return;
        }
        progress.put(a.pkg, 0);
        refresh(a.pkg);
        worker.execute(() -> {
            File dir = new File(getCacheDir(), "apk");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File apk = new File(dir, a.pkg + ".apk");
            try {
                Download.file(a.fdroidApk(latest), apk, (done, total) -> {
                    int pct = total > 0 ? (int) (done * 100 / total) : 0;
                    runOnUiThread(() -> {
                        progress.put(a.pkg, pct);
                        refresh(a.pkg);
                    });
                });
                PackageInfo info = Installer.info(this, apk);
                if (info == null || !a.pkg.equals(info.packageName)) throw new IOException(getString(R.string.store_wrong_app));
                Installer.install(getApplicationContext(), apk, a.pkg);
            } catch (IOException | RuntimeException e) {
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                runOnUiThread(() -> {
                    progress.remove(a.pkg);
                    refresh(a.pkg);
                    Toast.makeText(this, getString(R.string.store_failed, String.valueOf(e.getMessage())),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openPlay(StoreApp a) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + a.pkg))
                    .setPackage("com.android.vending"));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.playUrl())));
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        }
    }

    private String raw(int res) {
        try (InputStream in = getResources().openRawResource(res)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
