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
 * Software update (in the NovaOS settings): checks the NovaOS website for a newer
 * version, downloads it, checks SHA-256 and signing key, and installs it. After the
 * installation Android restarts NovaOS; the start animation then shows "what's new".
 */
public class UpdateActivity extends Activity {
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
            progress.setVisibility(View.GONE);
            if (!ok) status.setText(getString(R.string.update_failed, message == null ? "?" : message));
        }));
        UpdateInfo known = Updates.pending(this);
        if (known != null) show(known); else check();
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
        StringBuilder sb = new StringBuilder(getString(R.string.update_available)).append('\n')
                .append(getString(R.string.update_version, u.versionName, UpdateInfo.size(u.size, german)));
        if (!u.notes(german).isEmpty()) {
            sb.append("\n\n").append(getString(R.string.update_notes)).append(':');
            for (String n : u.notes(german)) sb.append("\n•  ").append(n);
        }
        status.setText(sb);
        button(R.string.update_install, v -> install());
    }

    private void button(int text, View.OnClickListener click) {
        action.setText(text);
        action.setOnClickListener(click);
        action.setVisibility(View.VISIBLE);
    }

    private void install() {
        if (busy || update == null) return;
        if (!Installer.allowedOrAsk(this)) {
            status.setText(R.string.update_allow);
            return;
        }
        busy = true;
        action.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        UpdateInfo u = update;
        new Thread(() -> {
            File dir = new File(getCacheDir(), "apk");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File apk = new File(dir, "novaos-update.apk");
            try {
                String sha = Updates.download(this, u, apk, (done, total) -> runOnUiThread(() -> {
                    int pct = total > 0 ? (int) (done * 100 / total) : 0;
                    progress.setProgress(pct);
                    status.setText(getString(R.string.update_downloading, pct));
                }));
                runOnUiThread(() -> status.setText(R.string.update_verifying));
                if (!sha.equals(u.sha256)) {
                    fail(getString(R.string.update_bad_hash), apk);
                    return;
                }
                if (!Installer.sameSigner(this, apk)) {
                    fail(getString(R.string.update_other_key), apk);
                    return;
                }
                runOnUiThread(() -> status.setText(R.string.update_installing));
                Installer.install(getApplicationContext(), apk, getPackageName());
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
            progress.setVisibility(View.GONE);
            status.setText(text);
            button(R.string.update_check, v -> check());
        });
    }
}
