package org.novaos.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/**
 * Software update (in the NovaOS settings): shows a newer version, and on "Aktualisieren"
 * runs it like a system update - download, verify (SHA-256, signing key), prepare,
 * install - in about 2-3 minutes. Android then restarts NovaOS; the start animation shows
 * "what's new".
 */
public class UpdateActivity extends Activity {
    static final String EXTRA_INSTALL = "install";  // "Aktualisieren" in the notification
    private LinearLayout content;
    private TextView status;
    private Button action;
    private ProgressBar progress;
    private UpdateInfo update;
    private boolean busy;
    private final boolean german = Locale.getDefault().getLanguage().equals("de");

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.update_title, getString(R.string.update_current, Updates.installedName(this)));
        status = Ui.text(this, "", 16, getColor(R.color.text), false);
        status.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 10));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        action = new Button(this);
        action.setAllCaps(false);
        action.setTextColor(getColor(R.color.text));
        action.setBackgroundResource(R.drawable.accent_card_bg);
        action.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        content.addView(status);
        content.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 14);
        content.addView(action, bl);
        addMenu();
        Installer.setListener((pkg, ok, message) -> runOnUiThread(() -> {
            busy = false;
            stage = 0;
            if (panel != null) panel.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
            if (!ok) {
                status.setText(getString(R.string.update_failed, message == null ? "?" : message));
                button(R.string.update_install, v -> install());
            }
        }));
        UpdateInfo known = Updates.pending(this);
        if (known != null) {
            show(known);
            if (getIntent().getBooleanExtra(EXTRA_INSTALL, false)) install();
        } else {
            check();
        }
    }

    @Override
    protected void onDestroy() {
        Installer.setListener(null);
        super.onDestroy();
    }

    // ---- ⋮ at the top right: what's new, version history, check again

    private void addMenu() {
        android.view.ViewGroup header = (android.view.ViewGroup) findViewById(R.id.page_title).getParent().getParent();
        android.widget.ImageButton more = new android.widget.ImageButton(this);
        more.setImageResource(R.drawable.ic_more);
        more.setBackgroundResource(R.drawable.round_bg);
        more.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 11);
        more.setPadding(p, p, p, p);
        more.setContentDescription(getString(R.string.more_options));
        more.setOnClickListener(v -> {
            android.widget.PopupMenu m = new android.widget.PopupMenu(this, v);
            m.getMenu().add(0, 1, 0, R.string.update_menu_whatsnew);
            m.getMenu().add(0, 2, 1, R.string.update_menu_history);
            m.getMenu().add(0, 3, 2, R.string.update_check);
            m.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) whatsNew();
                else if (item.getItemId() == 2) history();
                else if (!busy) check();
                return true;
            });
            m.show();
        });
        header.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
    }

    private String raw(int id) {
        try (java.io.InputStream in = getResources().openRawResource(id)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** Dialog with titled blocks of bullet points. */
    private void notesDialog(int title, java.util.List<String[]> blocks) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        list.setPadding(p, Ui.dp(this, 8), p, p);
        for (String[] b : blocks) {
            if (!b[0].isEmpty()) {
                TextView h = Ui.text(this, b[0], 16, getColor(R.color.text), true);
                h.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
                list.addView(h);
            }
            TextView t = Ui.text(this, b[1], 15, getColor(R.color.text), false);
            t.setLineSpacing(0, 1.15f);
            list.addView(t);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(list);
        new android.app.AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton(R.string.ok, null).show();
    }

    private static String bullets(java.util.List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(b.length() == 0 ? "" : "\n").append("•  ").append(l);
        return b.toString();
    }

    private void whatsNew() {
        java.util.List<String[]> blocks = new java.util.ArrayList<>();
        UpdateInfo pending = Updates.pending(this);
        if (pending != null && !pending.notes(german).isEmpty()) {
            blocks.add(new String[] {getString(R.string.update_menu_coming, pending.versionName), bullets(pending.notes(german))});
        }
        blocks.add(new String[] {getString(R.string.update_menu_installed, Updates.installedName(this)),
                bullets(UpdateInfo.whatsNew(raw(R.raw.whatsnew), german))});
        notesDialog(R.string.update_menu_whatsnew, blocks);
    }

    private void history() {
        java.util.List<String[]> blocks = new java.util.ArrayList<>();
        blocks.add(new String[] {getString(R.string.update_menu_installed, Updates.installedName(this)),
                bullets(UpdateInfo.whatsNew(raw(R.raw.whatsnew), german))});
        for (History.Entry e : History.parse(raw(R.raw.history))) {
            if (e.title.contains(Updates.installedName(this))) continue;
            blocks.add(new String[] {e.title, bullets(e.lines(german))});
        }
        notesDialog(R.string.update_menu_history, blocks);
    }

    private void check() {
        busy = true;
        status.setText(R.string.update_checking);
        action.setVisibility(View.GONE);
        new Thread(() -> {
            UpdateInfo u = Updates.check(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false;
                if (u == null) {
                    int st = Updates.status(this);
                    status.setText(st == Updates.NOT_PUBLIC ? R.string.update_not_public
                            : st == Updates.INVALID ? R.string.update_invalid : R.string.update_unreachable);
                    button(R.string.update_check, v -> check());
                } else if (!u.newerThan(Updates.installedCode(this))) {
                    status.setText(R.string.update_none);
                    button(R.string.update_check, v -> check());
                } else {
                    show(u);
                }
            });
        }).start();
    }

    private void show(UpdateInfo u) {
        update = u;
        boolean ready = Updates.readyApk(this, u) != null;  // downloaded in the background
        StringBuilder sb = new StringBuilder(getString(ready ? R.string.update_ready : R.string.update_available)).append('\n')
                .append(getString(R.string.update_version, u.versionName, UpdateInfo.size(u.size, german)));
        if (!u.notes(german).isEmpty()) {
            sb.append("\n\n").append(getString(R.string.update_menu_coming, u.versionName)).append(':');
            for (String n : u.notes(german)) sb.append("\n•  ").append(n);
        }
        status.setText(sb);
        button(ready ? R.string.update_install_now : R.string.update_install, v -> install());
    }

    private void button(int text, View.OnClickListener click) {
        action.setText(text);
        action.setOnClickListener(click);
        action.setVisibility(View.VISIBLE);
    }

    // ---- the update itself, like a system update on Samsung phones: four stages, about
    // 2-3 minutes; the real work (download, SHA-256, signing key) happens in these stages
    // The download shows the real bytes and speed (at least a few seconds, so it can be seen);
    // the other stages take their time like a system update on Samsung phones.
    private static final long[] STAGE_MS = {0, 8_000, 30_000, 70_000, 4_000};
    private static final int[] STAGE_START = {0, 0, 40, 55, 95};
    private static final int[] STAGE_SPAN = {0, 40, 15, 40, 5};
    private static final int[] STAGE_TEXT = {0, R.string.update_stage_download, R.string.update_stage_verify,
            R.string.update_stage_prepare, R.string.update_stage_install};
    private static final int[] STEP_TEXT = {0, R.string.update_step_download, R.string.update_step_verify,
            R.string.update_step_prepare, R.string.update_step_install};
    private volatile int stage;
    private volatile long stageStart;
    private volatile float realDownload;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private LinearLayout panel;
    private TextView percent, stageText;
    private final TextView[] steps = new TextView[5];
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            if (busy && !isDestroyed()) ui.postDelayed(this, 100);
        }
    };

    private RingView ring;
    private TextView detail;
    private long lastBytes, lastBytesAt;
    private volatile long realBytes;
    private volatile boolean preloaded;  // already downloaded in the background
    private double bytesPerSecond;

    private void buildPanel() {
        if (panel != null) return;
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        panel.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 20);
        panel.setPadding(p, Ui.dp(this, 24), p, p);
        android.widget.FrameLayout circle = new android.widget.FrameLayout(this);
        ring = new RingView(this);
        circle.addView(ring, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        percent = Ui.text(this, "0 %", 44, getColor(R.color.text), true);
        percent.setGravity(android.view.Gravity.CENTER);
        circle.addView(percent, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(circle, new LinearLayout.LayoutParams(Ui.dp(this, 190), Ui.dp(this, 190)));
        stageText = Ui.text(this, "", 18, getColor(R.color.text), true);
        stageText.setGravity(android.view.Gravity.CENTER);
        stageText.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
        panel.addView(stageText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detail = Ui.text(this, "", 14, getColor(R.color.text_muted), false);
        detail.setGravity(android.view.Gravity.CENTER);
        panel.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 1; i <= 4; i++) {
            steps[i] = Ui.text(this, "", 15, getColor(R.color.text_muted), false);
            steps[i].setPadding(0, Ui.dp(this, i == 1 ? 18 : 6), 0, 0);
            list.addView(steps[i]);
        }
        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView hint = Ui.text(this, getString(R.string.update_duration_hint), 13, getColor(R.color.text_muted), false);
        hint.setPadding(0, Ui.dp(this, 16), 0, 0);
        panel.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 12);
        content.addView(panel, 1, lp);
    }

    private void render() {
        int st = stage;
        if (st == 0 || panel == null) return;
        long now = System.currentTimeMillis();
        long minMs = st == 1 && preloaded ? 3_000 : STAGE_MS[st];
        float f = (now - stageStart) / (float) minMs;
        if (st == 1) f = Math.min(f, realDownload);  // never ahead of the real download
        f = Math.max(0f, Math.min(1f, f));
        float exact = STAGE_START[st] + STAGE_SPAN[st] * f;
        int pct = Math.min(99, (int) exact);
        percent.setText(pct + " %");
        progress.setProgress(pct);
        ring.set(exact / 100f);
        stageText.setText(STAGE_TEXT[st]);
        long left;  // the rest of this stage and the following ones
        if (st == 1 && update != null && update.size > 0) {
            long real = realBytes;
            if (!preloaded && realDownload < 1f && now - lastBytesAt >= 1000) {  // real speed, once a second
                if (lastBytesAt > 0) bytesPerSecond = Math.max(0, real - lastBytes) * 1000.0 / (now - lastBytesAt);
                lastBytes = real;
                lastBytesAt = now;
            }
            long downloadLeft = realDownload >= 1f || bytesPerSecond <= 0 ? 0 : (long) ((update.size - real) * 1000 / bytesPerSecond);
            left = Math.max(downloadLeft, minMs - (now - stageStart));
            String line;
            if (preloaded) {
                line = UpdateInfo.size(update.size, german) + "  ·  " + getString(R.string.update_preloaded);
            } else {  // the real bytes, and the real speed
                String state = realDownload >= 1f ? "✓ " + getString(R.string.update_downloaded)
                        : bytesPerSecond > 0 ? UpdateInfo.size((long) bytesPerSecond, german) + "/s" : "";
                line = getString(R.string.update_bytes, UpdateInfo.size(realDownload >= 1f ? update.size : real, german),
                        UpdateInfo.size(update.size, german)) + (state.isEmpty() ? "" : "  ·  " + state);
            }
            detail.setText(line + "\n" + rest(left, st));
        } else {
            left = Math.max(0, STAGE_MS[st] - (now - stageStart));
            detail.setText(rest(left, st));
        }
        for (int i = 1; i <= 4; i++) {
            String mark = i < st ? "✓  " : i == st ? "●  " : "○  ";
            steps[i].setText(mark + getString(STEP_TEXT[i]));
            steps[i].setTextColor(getColor(i <= st ? R.color.text : R.color.text_muted));
        }
    }

    private String rest(long thisStage, int st) {
        long left = thisStage;
        for (int i = st + 1; i < STAGE_MS.length; i++) left += STAGE_MS[i];
        long sec = (left + 999) / 1000;
        return getString(R.string.update_left, sec / 60, String.format(Locale.ROOT, "%02d", sec % 60));
    }

    private void setStage(int s) {
        stageStart = System.currentTimeMillis();
        stage = s;
    }

    /** Waits until the current stage has taken its time (the progress keeps moving). */
    private void finishStage() throws InterruptedException {
        long ms = stage == 1 && preloaded ? 3_000 : STAGE_MS[stage];
        long rest = ms - (System.currentTimeMillis() - stageStart);
        if (rest > 0) Thread.sleep(rest);
    }

    private void install() {
        if (busy || update == null) return;
        if (!Installer.allowedOrAsk(this)) {
            status.setText(R.string.update_allow);
            return;
        }
        busy = true;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        action.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);  // the ring in the panel shows the progress
        buildPanel();
        panel.setVisibility(View.VISIBLE);
        status.setText(getString(R.string.update_version, update.versionName, UpdateInfo.size(update.size, german)));
        realDownload = 0f;
        realBytes = 0;
        preloaded = false;
        lastBytesAt = 0;
        bytesPerSecond = 0;
        setStage(1);
        ui.post(tick);
        UpdateInfo u = update;
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            File apk = Updates.apkFile(app, u);
            //noinspection ResultOfMethodCallIgnored
            apk.getParentFile().mkdirs();
            try {
                // 1. download (or the copy already downloaded in the background)
                File ready = Updates.readyApk(app, u);
                String sha;
                if (ready != null) {
                    preloaded = true;
                    sha = Download.sha256(ready);
                    realDownload = 1f;
                } else {
                    sha = Updates.download(app, u, apk, (done, total) -> {
                        realBytes = done;
                        realDownload = total > 0 ? done / (float) total : 0f;
                    });
                    realDownload = 1f;
                }
                finishStage();
                // 2. verify: the file must be exactly the described update, signed with the NovaOS key
                setStage(2);
                if (!sha.equals(u.sha256)) {
                    fail(getString(R.string.update_bad_hash), apk);
                    return;
                }
                if (!Installer.sameSigner(app, apk)) {
                    fail(getString(R.string.update_other_key), apk);
                    return;
                }
                finishStage();
                // 3. prepare: tidy up old preview pictures, prepare the app list for the restart
                setStage(3);
                File[] thumbs = new File(getCacheDir(), "thumbs").listFiles();
                long old = System.currentTimeMillis() - 14L * 24 * 3600 * 1000;
                if (thumbs != null) for (File t : thumbs) if (t.lastModified() < old) //noinspection ResultOfMethodCallIgnored
                    t.delete();
                Apps.load(app);
                finishStage();
                // 4. install: Android asks once, then NovaOS restarts
                setStage(4);
                runOnUiThread(() -> stageText.setText(R.string.update_installing));
                Installer.install(app, apk, getPackageName());
            } catch (Exception e) {
                fail(getString(R.string.update_failed, String.valueOf(e.getMessage())), apk);
            }
        }).start();
    }

    private void fail(String text, File apk) {
        //noinspection ResultOfMethodCallIgnored
        apk.delete();
        runOnUiThread(() -> {
            busy = false;
            stage = 0;
            if (panel != null) panel.setVisibility(View.GONE);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            progress.setVisibility(View.GONE);
            status.setText(text);
            button(R.string.update_check, v -> check());
        });
    }
}
