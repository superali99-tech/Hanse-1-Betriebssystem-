package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * One mail in Nova Mail: sender, subject, text (HTML mails safely: no scripts, pictures from
 * the internet only on request), attachments; answer, forward, delete - and Nova reads the
 * mail aloud or writes the answer.
 */
public class MailReadActivity extends Activity implements NovaWriter.Target {
    static final String EXTRA_ACCOUNT = "account", EXTRA_FOLDER = "folder", EXTRA_UID = "uid";
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private MailStore.Account account;
    private String folder;
    private long uid;
    private Mime.Message message;
    private LinearLayout content;
    private NovaWriter nova;
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        account = MailStore.account(this, getIntent().getStringExtra(EXTRA_ACCOUNT));
        folder = getIntent().getStringExtra(EXTRA_FOLDER);
        uid = getIntent().getLongExtra(EXTRA_UID, -1);
        if (account == null || folder == null || uid < 0) {
            finish();
            return;
        }
        content = Page.setup(this, R.string.app_mail, account.email);
        MailSync.cancelNotification(this, account.id);
        ViewGroup parent = (ViewGroup) findViewById(R.id.scroller).getParent();
        int side = getResources().getDimensionPixelSize(R.dimen.side);
        nova = new NovaWriter(this, this, false, R.string.mail_answer_with);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.setMargins(side, Ui.dp(this, 6), side, 0);
        parent.addView(nova, nl);
        // actions at the bottom
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setPadding(side, Ui.dp(this, 8), side, Ui.dp(this, 10));
        hs.setClipToPadding(false);
        LinearLayout bar = new LinearLayout(this);
        bar.addView(action("↩ " + getString(R.string.mail_reply), v -> answer(false)));
        bar.addView(action("↩↩ " + getString(R.string.mail_reply_all), v -> answer(true)));
        bar.addView(action("↪ " + getString(R.string.mail_forward), v -> forward()));
        bar.addView(action("🔊 " + getString(R.string.mail_read_aloud), v -> {
            nova.setVisibility(View.VISIBLE);
            nova.say(readAloud());
        }));
        bar.addView(action("✦ " + getString(R.string.mail_answer_nova), v -> nova.open()));
        bar.addView(action("🗑 " + getString(R.string.delete), v -> delete()));
        hs.addView(bar);
        parent.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        load();
    }

    @Override
    protected void onDestroy() {
        if (nova != null) nova.destroy();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (nova.isOpen()) nova.close();
        else super.onBackPressed();
    }

    private TextView action(String text, View.OnClickListener click) {
        TextView t = Ui.text(this, text, 14, Color.WHITE, true);
        t.setBackgroundResource(R.drawable.pill_bg);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    private void load() {
        Page.status(this, getString(R.string.loading));
        MailStore.Mail row = MailStore.get(this).mail(account.id, folder, uid);
        new Thread(() -> {
            Mime.Message m = null;
            String error = null;
            try {
                m = MailSync.message(getApplicationContext(), account, folder, uid);
                if (row == null || !row.seen) MailSync.setSeen(getApplicationContext(), account, folder, uid, true);
                MailStore.get(this).setPreview(account.id, folder, uid, Mime.preview(m.plain(), 160), MailSync.hasFiles(m));
            } catch (IOException e) {
                error = getString(R.string.mail_no_connection);
            }
            Mime.Message got = m;
            String err = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                Page.status(this, err);
                if (got != null) show(got);
            });
        }).start();
    }

    private void show(Mime.Message m) {
        message = m;
        content.removeAllViews();
        ((TextView) findViewById(R.id.page_title)).setText(R.string.app_mail);
        TextView subject = Ui.text(this, m.subject.isEmpty() ? getString(R.string.mail_no_subject) : m.subject, 21, Color.WHITE, true);
        subject.setTextIsSelectable(true);
        subject.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        content.addView(subject);
        LinearLayout from = new LinearLayout(this);
        from.setGravity(Gravity.CENTER_VERTICAL);
        Mime.Address s = m.sender();
        from.addView(ChatActivity.avatar(this, s.display().isEmpty() ? "?" : s.display(), 44));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, s.display(), 16, Color.WHITE, true));
        TextView addr = Ui.text(this, s.email, 13, getColor(R.color.text_muted), false);
        col.addView(addr);
        String to = Mime.addressHeader(m.to);
        TextView meta = Ui.text(this, getString(R.string.mail_to_line, to) + " · " + DateUtils.formatDateTime(this, m.date,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR), 12, getColor(R.color.text_muted), false);
        col.addView(meta);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cl.leftMargin = Ui.dp(this, 12);
        from.addView(col, cl);
        content.addView(from);

        // attachments
        List<Mime.Attachment> files = new java.util.ArrayList<>();
        for (Mime.Attachment a : m.attachments) if (!a.inline) files.add(a);
        if (!files.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(this);
            for (Mime.Attachment a : files) {
                TextView chip = Ui.text(this, "📎 " + a.name + " · " + Formatter.formatShortFileSize(this, a.data.length), 13, Color.WHITE, false);
                chip.setBackgroundResource(R.drawable.card_bg);
                chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
                chip.setOnClickListener(v -> openAttachment(a));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Ui.dp(this, 8);
                chips.addView(chip, lp);
            }
            hs.addView(chips);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.topMargin = Ui.dp(this, 12);
            content.addView(hs, hl);
        }

        if (!m.html.trim().isEmpty()) {
            showHtml(m.html, false);
        } else {
            TextView body = Ui.text(this, m.text.trim(), 16, Color.WHITE, false);
            body.setTextIsSelectable(true);
            body.setLineSpacing(0, 1.15f);
            body.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
            body.setLinkTextColor(0xFF9DB8FF);
            body.setBackgroundResource(R.drawable.card_bg);
            body.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.topMargin = Ui.dp(this, 14);
            content.addView(body, bl);
        }
    }

    /** HTML mail on a white page: no JavaScript, pictures from the internet only after a tap (privacy). */
    private void showHtml(String html, boolean images) {
        if (web != null) {
            content.removeView(web);
            web.destroy();
        }
        View old = content.findViewWithTag("images");
        if (old != null) content.removeView(old);
        if (!images && html.matches("(?is).*<img[^>]+src=[\"']?https?:.*")) {
            TextView load = Ui.text(this, "🖼 " + getString(R.string.mail_load_images), 14, 0xFF9DB8FF, true);
            load.setTag("images");
            load.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, 0);
            load.setOnClickListener(v -> showHtml(html, true));
            content.addView(load);
        }
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setBlockNetworkLoads(!images);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                openLink(request.getUrl());
                return true;
            }
        });
        String page = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>body{font-family:sans-serif;margin:12px;word-wrap:break-word}img{max-width:100%;height:auto}</style></head><body>"
                + html + "</body></html>";
        web.loadDataWithBaseURL("about:blank", page, "text/html", "UTF-8", null);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.topMargin = Ui.dp(this, 14);
        content.addView(web, wl);
    }

    private void openLink(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        if (scheme.equals("mailto")) {
            startActivity(new Intent(this, MailComposeActivity.class).putExtra(MailComposeActivity.EXTRA_TO, uri.getSchemeSpecificPart()));
            return;
        }
        if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("tel")) return;
        new AlertDialog.Builder(this).setMessage(getString(R.string.mail_open_link, uri.toString()))
                .setPositiveButton(R.string.mail_open, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.nova_no_app, Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void openAttachment(Mime.Attachment a) {
        try {
            File dir = new File(getCacheDir(), "mail-files");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no folder");
            String safe = a.name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
            if (safe.startsWith(".")) safe = "_" + safe;
            File f = new File(dir, safe);
            try (OutputStream out = new FileOutputStream(f)) {
                out.write(a.data);
            }
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(NovaFileProvider.uri(f), a.type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_for_file, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void answer(boolean all) {
        if (message == null) return;
        startActivity(MailComposeActivity.reply(this, account, message, all, null));
    }

    private void forward() {
        if (message == null) return;
        startActivity(MailComposeActivity.forward(this, account, folder, uid, message));
    }

    private void delete() {
        new AlertDialog.Builder(this).setMessage(R.string.mail_delete_confirm).setPositiveButton(R.string.delete, (d, w) ->
                new Thread(() -> {
                    String err = null;
                    try {
                        MailSync.delete(getApplicationContext(), account, folder, uid);
                    } catch (IOException e) {
                        err = e.getMessage();
                    }
                    String e2 = err;
                    runOnUiThread(() -> {
                        if (e2 != null) Toast.makeText(this, e2, Toast.LENGTH_LONG).show();
                        else {
                            Toast.makeText(this, R.string.mail_deleted, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }).start()).setNegativeButton(R.string.cancel, null).show();
    }

    // ------------------------------------------------------------ Nova

    /** Nova's spoken answer becomes the answer mail (it opens to check and send). */
    @Override
    public void use(String text) {
        if (message != null) startActivity(MailComposeActivity.reply(this, account, message, false, text));
    }

    @Override
    public String readAloud() {
        if (message == null) return "";
        String text = message.plain();
        // without the quoted earlier mails
        StringBuilder b = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.trim().startsWith(">")) continue;
            b.append(line).append('\n');
            if (b.length() > 3000) break;
        }
        return getString(R.string.mail_spoken_mail, message.sender().display(),
                message.subject.isEmpty() ? getString(R.string.mail_no_subject) : message.subject) + " " + b.toString().trim();
    }

    @Override
    public void edit(String text) {
        use(text);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == NovaWriter.MIC) nova.permissionResult(results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED);
    }
}
