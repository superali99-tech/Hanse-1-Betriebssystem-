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
            sb.append("\n\n").append(getString(R.string.update_notes)).append(':');
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
    private static final long[] STAGE_MS = {0, 70_000, 20_000, 45_000, 4_000};
    private static final int[] STAGE_START = {0, 0, 60, 75, 95};
    private static final int[] STAGE_SPAN = {0, 60, 15, 20, 5};
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
            if (busy && !isDestroyed()) ui.postDelayed(this, 200);
        }
    };

    private void buildPanel() {
        if (panel != null) return;
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 20);
        panel.setPadding(p, p, p, p);
        percent = Ui.text(this, "0 %", 52, getColor(R.color.text), true);
        panel.addView(percent);
        stageText = Ui.text(this, "", 17, getColor(R.color.text), false);
        panel.addView(stageText);
        for (int i = 1; i <= 4; i++) {
            steps[i] = Ui.text(this, "", 15, getColor(R.color.text_muted), false);
            steps[i].setPadding(0, Ui.dp(this, i == 1 ? 16 : 6), 0, 0);
            panel.addView(steps[i]);
        }
        TextView hint = Ui.text(this, getString(R.string.update_duration_hint), 13, getColor(R.color.text_muted), false);
        hint.setPadding(0, Ui.dp(this, 16), 0, 0);
        panel.addView(hint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 12);
        content.addView(panel, 1, lp);
    }

    private void render() {
        int st = stage;
        if (st == 0 || panel == null) return;
        float f = (System.currentTimeMillis() - stageStart) / (float) STAGE_MS[st];
        if (st == 1) f = Math.min(f, realDownload);  // never ahead of the real download
        f = Math.max(0f, Math.min(1f, f));
        int pct = Math.min(99, Math.round(STAGE_START[st] + STAGE_SPAN[st] * f));
        percent.setText(pct + " %");
        progress.setProgress(pct);
        stageText.setText(STAGE_TEXT[st]);
        for (int i = 1; i <= 4; i++) {
            String mark = i < st ? "✓  " : i == st ? "●  " : "○  ";
            steps[i].setText(mark + getString(STEP_TEXT[i]));
            steps[i].setTextColor(getColor(i <= st ? R.color.text : R.color.text_muted));
        }
    }

    private void setStage(int s) {
        stageStart = System.currentTimeMillis();
        stage = s;
    }

    /** Waits until the current stage has taken its time (the progress keeps moving). */
    private void finishStage() throws InterruptedException {
        long rest = STAGE_MS[stage] - (System.currentTimeMillis() - stageStart);
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
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        buildPanel();
        panel.setVisibility(View.VISIBLE);
        status.setText(getString(R.string.update_version, update.versionName, UpdateInfo.size(update.size, german)));
        realDownload = 0f;
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
                    sha = Download.sha256(ready);
                    realDownload = 1f;
                } else {
                    sha = Updates.download(app, u, apk, (done, total) -> realDownload = total > 0 ? done / (float) total : 0f);
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
