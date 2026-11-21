package org.novaos.mobile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nova Store: the Nova apps (part of NovaOS, "Aktualisieren" when the waiting software
 * update changes them) and real apps - free and open-source apps installed directly from
 * F-Droid (Android's installer asks for confirmation), popular apps open in the Google Play
 * Store. Tapping an app opens its details page (StoreDetailActivity).
 */
public class StoreActivity extends Activity implements StoreInstall.Listener {
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final Map<String, Long> fdroidCode = new HashMap<>();
    private final Map<String, String> fdroidName = new HashMap<>();
    private final Map<String, List<Button>> buttons = new HashMap<>();
    private final Map<String, List<RingView>> rings = new HashMap<>();
    private final Map<String, StoreApp> apps = new HashMap<>();
    private final List<Object[]> rows = new ArrayList<>();      // {row, searchable text, header}
    private final List<View> headers = new ArrayList<>();
    private final Map<String, Button> novaButtons = new HashMap<>();
    private final Map<String, TextView> novaLines = new HashMap<>();
    private final Map<String, View> novaRows = new HashMap<>();
    private final Map<String, RingView> novaRings = new HashMap<>();
    private Button novaAll;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    // the Nova app update runs here in the store: progress every half second while it runs
    private final UpdateEngine.Listener engineListener = () -> runOnUiThread(() -> {
        if (!isDestroyed()) showNovaProgress();
    });
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showNovaProgress();
            if (UpdateEngine.get(StoreActivity.this).running()) ui.postDelayed(this, 500);
        }
    };
    private TextView none;
    private LinearLayout novaBox;
    private BroadcastReceiver packages;
    private String query = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.app_store, getString(R.string.store_sub));
        List<StoreApp> list = StoreCatalog.apps(this);
        for (StoreApp a : list) apps.put(a.pkg, a);
        none = Ui.text(this, getString(R.string.search_none), 15, getColor(R.color.text_muted), false);
        none.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, 0);
        none.setVisibility(View.GONE);
        content.addView(none);

        // the Nova apps: at the top when an update changes some of them, otherwise at the end
        novaBox = new LinearLayout(this);
        novaBox.setOrientation(LinearLayout.VERTICAL);
        View novaHeader = Ui.section(this, R.string.store_nova_apps);
        novaBox.addView(novaHeader);
        headers.add(novaHeader);
        LinearLayout subRow = new LinearLayout(this);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView novaSub = Ui.text(this, getString(R.string.store_nova_sub), 13, getColor(R.color.text_muted), false);
        novaSub.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 8), Ui.dp(this, 2));
        subRow.addView(novaSub, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        novaAll = actionButton();
        novaAll.setText(R.string.store_update_all);
        novaAll.setOnClickListener(v -> {
            NovaUpdate.start(this, null);
            showNovaProgress();
        });
        subRow.addView(novaAll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        novaBox.addView(subRow);
        rows.add(new Object[] {subRow, "", novaHeader});
        for (NovaApps.App n : NovaApps.ALL) {
            View r = novaRow(n);
            novaBox.addView(r);
            novaRows.put(n.key, r);
            rows.add(new Object[] {r, getString(n.name) + " " + getString(n.text), novaHeader});
        }
        content.addView(novaBox);

        // F-Droid and Play apps
        for (Map.Entry<String, List<StoreApp>> sec : StoreApp.sections(list).entrySet()) {
            View header = Ui.section(this, StoreCatalog.category(sec.getKey()));
            header.setTag("empfohlen".equals(sec.getKey()) ? "rec" : null);
            content.addView(header);
            headers.add(header);
            for (StoreApp a : sec.getValue()) {
                View r = row(a);
                content.addView(r);
                rows.add(new Object[] {r, a.name + " " + a.text(german) + " " + getString(StoreCatalog.category(a.category)), header});
            }
        }
        // search: the "recommended" apps also appear in their category, so that section is hidden while searching
        NovaAppDetailActivity.addMenu(this, () -> {  // "Neuerungen anzeigen" on/off
            refreshNova();
            for (String pkg : apps.keySet()) refresh(pkg);
        });
        Page.search(this, R.string.store_search, q -> {
            query = q;
            filter();
        });

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
        // is there a newer NovaOS (and which Nova apps does it change)?
        worker.execute(() -> {
            Updates.check(getApplicationContext());
            runOnUiThread(this::refreshNova);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        StoreInstall.addListener(this);
        StoreInstall.cleanup(this);
        for (String pkg : apps.keySet()) refresh(pkg);
        refreshNova();
        UpdateEngine.get(this).addListener(engineListener);
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        StoreInstall.removeListener(this);
        UpdateEngine.get(this).removeListener(engineListener);
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void changed(String pkg, String message) {
        if (isDestroyed()) return;
        if (message != null && !message.isEmpty()) Toast.makeText(this, getString(R.string.store_failed, message), Toast.LENGTH_LONG).show();
        refresh(pkg);
    }

    private void filter() {
        boolean searching = !query.trim().isEmpty();
        Set<View> used = new HashSet<>();
        int found = 0;
        for (Object[] x : rows) {
            View header = (View) x[2];
            String text = (String) x[1];
            boolean show = !searching || (!"rec".equals(header.getTag()) && !text.isEmpty() && Search.matches(query, text));
            ((View) x[0]).setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                used.add(header);
                found++;
            }
        }
        for (View h : headers) h.setVisibility(used.contains(h) ? View.VISIBLE : View.GONE);
        none.setVisibility(found == 0 ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ Nova apps

    private View novaRow(NovaApps.App n) {
        LinearLayout r = card();
        // icon with a progress ring around it while the update runs
        FrameLayout box = new FrameLayout(this);
        RingView ring = new RingView(this);
        ring.setStroke(3);
        ring.setVisibility(View.INVISIBLE);
        box.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView icon = new ImageView(this);
        icon.setImageResource(n.icon);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        il.gravity = Gravity.CENTER;
        box.addView(icon, il);
        r.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
        novaRings.put(n.key, ring);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(Ui.text(this, getString(n.name), 16, Color.WHITE, true));
        View dot = newsDot();
        dot.setTag("dot:" + n.key);
        nameRow.addView(dot);
        texts.addView(nameRow);
        TextView d = Ui.text(this, getString(n.text), 13, getColor(R.color.text_muted), false);
        d.setMaxLines(2);
        d.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(d);
        TextView line = Ui.text(this, "", 11, 0xFFB9A8FF, false);
        texts.addView(line);
        novaLines.put(n.key, line);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 12);
        tl.rightMargin = Ui.dp(this, 8);
        r.addView(texts, tl);
        Button b = actionButton();
        r.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42)));
        novaButtons.put(n.key, b);
        // tap: the app's page with all information (the button opens the app itself)
        r.setOnClickListener(v -> startActivity(new Intent(this, NovaAppDetailActivity.class).putExtra(NovaAppDetailActivity.EXTRA_KEY, n.key)));
        return r;
    }

    /** "Aktualisieren" for the Nova apps that the waiting NovaOS update changes, otherwise "Öffnen". */
    /** The blue dot of "Neuerungen" next to a name. */
    private View newsDot() {
        View d = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(0xFF8AB4F8);
        d.setBackground(g);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
        dl.leftMargin = Ui.dp(this, 8);
        d.setLayoutParams(dl);
        d.setVisibility(View.GONE);
        return d;
    }

    private void refreshNova() {
        UpdateInfo pending = Updates.pending(this);
        for (NovaApps.App n : NovaApps.ALL) {
            for (View d : findAll(findViewById(R.id.content), "dot:" + n.key)) d.setVisibility(AppNews.nova(this, n.key) != null ? View.VISIBLE : View.GONE);
        }
        boolean any = false;
        for (NovaApps.App n : NovaApps.ALL) any |= pending != null && pending.apps.contains(n.key);
        ViewGroup content = (ViewGroup) novaBox.getParent();
        int want = any ? content.indexOfChild(none) + 1 : content.getChildCount() - 1;
        if (content.indexOfChild(novaBox) != want) {
            content.removeView(novaBox);
            content.addView(novaBox, any ? content.indexOfChild(none) + 1 : content.getChildCount());
        }
        // the apps with an update first (like "Updates available" in other stores)
        int at = 2;  // after the title and the explanation with "Alle aktualisieren"
        for (int pass = 0; pass < 2; pass++) {
            for (NovaApps.App n : NovaApps.ALL) {
                boolean update = pending != null && pending.apps.contains(n.key);
                if (update != (pass == 0)) continue;
                View r = novaRows.get(n.key);
                if (r == null) continue;
                novaBox.removeView(r);
                novaBox.addView(r, at++);
            }
        }
        showNovaProgress();
    }

    /** Buttons, lines and rings of the Nova apps: "Aktualisieren", the running update, or "Öffnen". */
    private void showNovaProgress() {
        UpdateInfo pending = Updates.pending(this);
        UpdateEngine e = UpdateEngine.get(this);
        int changed = 0;
        for (NovaApps.App n : NovaApps.ALL) {
            Button b = novaButtons.get(n.key);
            TextView line = novaLines.get(n.key);
            RingView ring = novaRings.get(n.key);
            if (b == null || line == null) continue;
            boolean update = pending != null && pending.apps.contains(n.key);
            boolean busy = update && NovaUpdate.busy(this, n.key);
            if (update) changed++;
            if (ring != null) {
                ring.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
                if (busy) ring.set(NovaUpdate.fraction(e));
            }
            b.setEnabled(!(busy && e.stage() == UpdateEngine.INSTALL));
            b.setText(busy ? (e.paused() ? R.string.update_button_resume : R.string.update_button_pause)
                    : update ? R.string.store_update : R.string.store_open);
            b.setOnClickListener(v -> {
                if (NovaUpdate.busy(this, n.key)) {
                    if (e.paused()) e.resume();
                    else e.pause();
                } else if (update) {
                    NovaUpdate.start(this, n.key);  // right here, with progress at this app
                    ui.removeCallbacks(tick);
                    ui.post(tick);
                } else {
                    startActivity(new Intent(this, n.activity));
                }
                showNovaProgress();
            });
            String error = e.error();
            line.setText(busy ? NovaUpdate.status(this, e)
                    : update && !error.isEmpty() ? error
                    : update ? getString(R.string.store_nova_new, pending.versionName)
                    : getString(R.string.store_nova_included, Updates.installedName(this)));
        }
        if (novaAll != null) novaAll.setVisibility(changed > 1 && !e.running() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ F-Droid and Play apps

    private LinearLayout card() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 12);
        r.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);
        return r;
    }

    private Button actionButton() {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinWidth(Ui.dp(this, 96));
        b.setBackgroundResource(R.drawable.accent_card_bg);
        return b;
    }

    private View row(StoreApp a) {
        LinearLayout r = card();
        // icon with a progress ring around it while downloading
        FrameLayout box = new FrameLayout(this);
        RingView ring = new RingView(this);
        ring.setStroke(3);
        ring.setVisibility(View.INVISIBLE);
        box.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        il.gravity = Gravity.CENTER;
        box.addView(StoreCatalog.icon(this, a, 44), il);
        r.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
        List<RingView> rl = rings.get(a.pkg);
        if (rl == null) rings.put(a.pkg, rl = new ArrayList<>());
        rl.add(ring);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(Ui.text(this, a.name, 16, Color.WHITE, true));
        View dot = newsDot();
        dot.setTag("dot:" + a.pkg);
        nameRow.addView(dot);
        texts.addView(nameRow);
        TextView d = Ui.text(this, a.text(german), 13, getColor(R.color.text_muted), false);
        d.setMaxLines(2);
        d.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(d);
        StoreMeta m = StoreCatalog.meta(this, a.pkg);
        String src = getString(a.fromFdroid() ? R.string.store_source_fdroid : R.string.store_source_play)
                + (m != null ? " · " + UpdateInfo.size(m.size, german) : "");
        TextView source = Ui.text(this, src, 11, 0xFFB9A8FF, false);
        source.setTag("source:" + a.pkg);
        texts.addView(source);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 10);
        tl.rightMargin = Ui.dp(this, 8);
        r.addView(texts, tl);

        Button b = actionButton();
        b.setOnClickListener(v -> act(a));
        r.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42)));
        List<Button> l = buttons.get(a.pkg);
        if (l == null) buttons.put(a.pkg, l = new ArrayList<>());
        l.add(b);
        r.setOnClickListener(v -> startActivity(new Intent(this, StoreDetailActivity.class).putExtra(StoreDetailActivity.EXTRA_PKG, a.pkg)));
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

    /** Button text: install / update / open / Play Store / progress, and the ring. */
    private void refresh(String pkg) {
        StoreApp a = apps.get(pkg);
        List<Button> l = buttons.get(pkg);
        if (a == null || l == null) return;
        long installed = installedCode(pkg);
        Long latest = fdroidCode.get(pkg);
        StoreInstall.State s = StoreInstall.state(pkg);
        String text;
        if (s != null) text = s.installing || s.checking ? "…" : getString(R.string.store_progress, s.percent());
        else if (installed >= 0 && a.fromFdroid() && latest != null && latest > installed) text = getString(R.string.store_update);
        else if (installed >= 0) text = getString(R.string.store_open);
        else text = getString(a.fromFdroid() ? R.string.store_install : R.string.store_play);
        for (Button b : l) {
            b.setText(text);
            b.setEnabled(s == null);
        }
        List<RingView> rl = rings.get(pkg);
        if (rl != null) for (RingView ring : rl) {
            ring.setVisibility(s == null ? View.INVISIBLE : View.VISIBLE);
            if (s != null) ring.set(s.installing || s.checking || s.total <= 0 ? -1 : s.done / (float) s.total);
        }
        for (View d : findAll(findViewById(R.id.content), "dot:" + pkg)) {
            d.setVisibility(a.fromFdroid() && AppNews.dot(this, a, latest == null ? -1 : latest) ? View.VISIBLE : View.GONE);
        }
        String name = fdroidName.get(pkg);
        StoreMeta m = StoreCatalog.meta(this, pkg);
        if (name != null && !name.isEmpty()) {
            for (View v : findAll(findViewById(R.id.content), "source:" + pkg)) {
                ((TextView) v).setText(getString(R.string.store_source_fdroid) + " · " + name
                        + (m != null ? " · " + UpdateInfo.size(m.size, german) : ""));
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
        StoreInstall.start(this, a, latest);
    }

    private void openPlay(StoreApp a) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + a.pkg)).setPackage("com.android.vending"));
        } catch (android.content.ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.playUrl())));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        }
    }
}
