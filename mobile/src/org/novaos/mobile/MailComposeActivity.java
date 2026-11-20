package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Writing a mail in Nova Mail: to (with suggestions), copy, subject, text, attachments; answer
 * and forward. Nova writes the text when you speak.
 */
public class MailComposeActivity extends Activity implements NovaWriter.Target {
    static final String EXTRA_TO = "to", EXTRA_CC = "cc", EXTRA_SUBJECT = "subject", EXTRA_BODY = "body", EXTRA_QUOTE = "quote",
            EXTRA_IN_REPLY_TO = "in_reply_to", EXTRA_REFERENCES = "references", EXTRA_ACCOUNT = "account",
            EXTRA_FWD_FOLDER = "fwd_folder", EXTRA_FWD_UID = "fwd_uid", EXTRA_NOVA = "nova";
    private static final int PICK_FILE = 51;
    private static final long MAX_ATTACHMENTS = 20L * 1024 * 1024;

    private MailStore.Account account;
    private MultiAutoCompleteTextView to, cc;
    private EditText subject, body;
    private LinearLayout files;
    private final List<Mime.Attachment> attachments = new ArrayList<>();
    private String inReplyTo = "", references = "", quote = "";
    private NovaWriter nova;

    /** Answer to a mail (all = also everybody in To/Cc except me). */
    static Intent reply(Context c, MailStore.Account a, Mime.Message m, boolean all, String text) {
        List<Mime.Address> to = new ArrayList<>(m.replyTo.isEmpty() ? m.from : m.replyTo);
        List<Mime.Address> cc = new ArrayList<>();
        if (all) {
            for (Mime.Address x : m.to) if (!x.email.equalsIgnoreCase(a.email) && !contains(to, x)) to.add(x);
            for (Mime.Address x : m.cc) if (!x.email.equalsIgnoreCase(a.email) && !contains(to, x)) cc.add(x);
        }
        String when = DateUtils.formatDateTime(c, m.date, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR);
        String refs = (m.references + " " + m.messageId).trim();
        return new Intent(c, MailComposeActivity.class).putExtra(EXTRA_ACCOUNT, a.id).putExtra(EXTRA_TO, Mime.addressHeader(to))
                .putExtra(EXTRA_CC, Mime.addressHeader(cc)).putExtra(EXTRA_SUBJECT, Mime.replySubject(m.subject))
                .putExtra(EXTRA_QUOTE, Mime.quote(m, c.getString(R.string.mail_wrote, when, m.sender().display())))
                .putExtra(EXTRA_IN_REPLY_TO, m.messageId).putExtra(EXTRA_REFERENCES, refs)
                .putExtra(EXTRA_BODY, text == null ? "" : text);
    }

    static Intent forward(Context c, MailStore.Account a, String folder, long uid, Mime.Message m) {
        String when = DateUtils.formatDateTime(c, m.date, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR);
        String q = "\n\n---------- " + c.getString(R.string.mail_forwarded) + " ----------\n"
                + c.getString(R.string.mail_fwd_header, m.sender().toString(), when, m.subject, Mime.addressHeader(m.to)) + "\n\n" + m.plain();
        return new Intent(c, MailComposeActivity.class).putExtra(EXTRA_ACCOUNT, a.id).putExtra(EXTRA_SUBJECT, Mime.forwardSubject(m.subject))
                .putExtra(EXTRA_QUOTE, q).putExtra(EXTRA_FWD_FOLDER, folder).putExtra(EXTRA_FWD_UID, uid);
    }

    private static boolean contains(List<Mime.Address> l, Mime.Address a) {
        for (Mime.Address x : l) if (x.email.equalsIgnoreCase(a.email)) return true;
        return false;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(EXTRA_ACCOUNT);
        account = id != null ? MailStore.account(this, id) : MailStore.current(this);
        if (account == null) {
            Toast.makeText(this, R.string.mail_need_account, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MailActivity.class));
            finish();
            return;
        }
        LinearLayout content = Page.setup(this, R.string.mail_compose, getString(R.string.mail_from_line, account.email));
        // send button in the header
        ViewGroup header = (ViewGroup) findViewById(R.id.page_title).getParent().getParent();
        ImageButton send = new ImageButton(this);
        send.setImageResource(R.drawable.ic_send);
        send.setBackgroundResource(R.drawable.accent_round_bg);
        send.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 12);
        send.setPadding(p, p, p, p);
        send.setContentDescription(getString(R.string.mail_send));
        send.setOnClickListener(v -> send());
        header.addView(send, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        List<String> suggestions = new ArrayList<>();
        for (Mime.Address a : MailStore.get(this).addresses()) suggestions.add(a.toString());
        to = addressField(content, getString(R.string.mail_to), suggestions);
        cc = addressField(content, getString(R.string.mail_cc), suggestions);
        subject = new EditText(this);
        style(subject, getString(R.string.mail_subject));
        subject.setSingleLine(true);
        subject.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        add(content, subject);
        body = new EditText(this);
        style(body, getString(R.string.mail_text));
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(8);
        body.setGravity(Gravity.TOP | Gravity.START);
        add(content, body);
        files = new LinearLayout(this);
        files.setOrientation(LinearLayout.VERTICAL);
        content.addView(files);
        LinearLayout tools = new LinearLayout(this);
        tools.addView(tool("📎 " + getString(R.string.mail_attach), v -> pickFile()));
        tools.addView(tool("✦ " + getString(R.string.mail_dictate), v -> nova.open()));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(this, 12);
        content.addView(tools, tl);

        ViewGroup parent = (ViewGroup) findViewById(R.id.scroller).getParent();
        int side = getResources().getDimensionPixelSize(R.dimen.side);
        nova = new NovaWriter(this, this, false, R.string.mail_insert);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.setMargins(side, Ui.dp(this, 6), side, Ui.dp(this, 10));
        parent.addView(nova, nl);

        Intent i = getIntent();
        String t = i.getStringExtra(EXTRA_TO);
        if (t == null && i.getData() != null && "mailto".equals(i.getData().getScheme())) t = i.getData().getSchemeSpecificPart();
        if (t != null) to.setText(t.isEmpty() ? "" : t + ", ");
        String c = i.getStringExtra(EXTRA_CC);
        if (c != null && !c.isEmpty()) cc.setText(c + ", ");
        if (i.getStringExtra(EXTRA_SUBJECT) != null) subject.setText(i.getStringExtra(EXTRA_SUBJECT));
        if (i.getStringExtra(EXTRA_BODY) != null) body.setText(i.getStringExtra(EXTRA_BODY));
        quote = i.getStringExtra(EXTRA_QUOTE) == null ? "" : i.getStringExtra(EXTRA_QUOTE);
        if (!quote.isEmpty()) body.append(quote);
        body.setSelection(Math.max(0, body.getText().length() - quote.length()));
        inReplyTo = i.getStringExtra(EXTRA_IN_REPLY_TO) == null ? "" : i.getStringExtra(EXTRA_IN_REPLY_TO);
        references = i.getStringExtra(EXTRA_REFERENCES) == null ? "" : i.getStringExtra(EXTRA_REFERENCES);
        if (i.hasExtra(EXTRA_FWD_UID)) loadForwardedFiles(i.getStringExtra(EXTRA_FWD_FOLDER), i.getLongExtra(EXTRA_FWD_UID, -1));
        if (to.getText().length() == 0) to.requestFocus();
        else body.requestFocus();
        if (i.getBooleanExtra(EXTRA_NOVA, false)) body.post(() -> nova.open());
    }

    @Override
    protected void onDestroy() {
        if (nova != null) nova.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (nova != null && nova.isOpen()) {
            nova.close();
            return;
        }
        boolean changed = body != null && (body.getText().length() > quote.length() || to.getText().length() > 0 && body.getText().length() > 0);
        if (!changed) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this).setMessage(R.string.mail_discard).setPositiveButton(R.string.mail_discard_yes, (d, w) -> finish())
                .setNegativeButton(R.string.cancel, null).show();
    }

    private MultiAutoCompleteTextView addressField(LinearLayout content, String hint, List<String> suggestions) {
        MultiAutoCompleteTextView f = new MultiAutoCompleteTextView(this);
        style(f, hint);
        f.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        f.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, suggestions));
        f.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        f.setThreshold(1);
        add(content, f);
        return f;
    }

    private void style(EditText e, String hint) {
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(getColor(R.color.text_muted));
        e.setBackgroundResource(R.drawable.card_bg);
        e.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
    }

    private void add(LinearLayout content, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        content.addView(v, lp);
    }

    private TextView tool(String text, View.OnClickListener click) {
        TextView t = Ui.text(this, text, 14, Color.WHITE, true);
        t.setBackgroundResource(R.drawable.pill_bg);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------ attachments

    private void pickFile() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), PICK_FILE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.nova_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FILE || result != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        for (Uri u : uris) addFile(u);
    }

    private void addFile(Uri uri) {
        String name = "anhang";
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0);
        } catch (RuntimeException ignored) {
            // keep "anhang"
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("cannot read");
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            long total = 0;
            for (Mime.Attachment a : attachments) total += a.data.length;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (total + b.size() > MAX_ATTACHMENTS) throw new IOException(getString(R.string.mail_too_big));
            }
            Mime.Attachment a = new Mime.Attachment();
            a.name = name;
            String type = getContentResolver().getType(uri);
            a.type = type == null ? "application/octet-stream" : type;
            a.data = b.toByteArray();
            attachments.add(a);
            showFiles();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadForwardedFiles(String folder, long uid) {
        if (folder == null || uid < 0) return;
        new Thread(() -> {
            try {
                Mime.Message m = MailSync.message(getApplicationContext(), account, folder, uid);
                runOnUiThread(() -> {
                    for (Mime.Attachment a : m.attachments) if (!a.inline) attachments.add(a);
                    showFiles();
                });
            } catch (IOException ignored) {
                // forwarded without the files
            }
        }).start();
    }

    private void showFiles() {
        files.removeAllViews();
        for (Mime.Attachment a : new ArrayList<>(attachments)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.card_bg);
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
            TextView t = Ui.text(this, "📎 " + a.name + " · " + Formatter.formatShortFileSize(this, a.data.length), 14, Color.WHITE, false);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ImageButton x = new ImageButton(this);
            x.setImageResource(R.drawable.ic_close);
            x.setBackground(null);
            x.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(this, 10);
            x.setPadding(p, p, p, p);
            x.setContentDescription(getString(R.string.delete));
            x.setOnClickListener(v -> {
                attachments.remove(a);
                showFiles();
            });
            row.addView(x, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            files.addView(row, lp);
        }
    }

    // ------------------------------------------------------------ sending

    private void send() {
        List<Mime.Address> rcpt = Mime.addresses(to.getText().toString());
        List<Mime.Address> copy = Mime.addresses(cc.getText().toString());
        List<String> bad = new ArrayList<>();
        for (Mime.Address a : rcpt) if (!Mime.validEmail(a.email)) bad.add(a.email);
        for (Mime.Address a : copy) if (!Mime.validEmail(a.email)) bad.add(a.email);
        if (rcpt.isEmpty() || !bad.isEmpty()) {
            Toast.makeText(this, bad.isEmpty() ? getString(R.string.mail_need_to) : getString(R.string.mail_bad_address, String.join(", ", bad)),
                    Toast.LENGTH_LONG).show();
            to.requestFocus();
            return;
        }
        if (subject.getText().toString().trim().isEmpty()) {
            new AlertDialog.Builder(this).setMessage(R.string.mail_no_subject_ask).setPositiveButton(R.string.mail_send, (d, w) -> doSend(rcpt, copy))
                    .setNegativeButton(R.string.cancel, null).show();
            return;
        }
        doSend(rcpt, copy);
    }

    @SuppressWarnings("deprecation")
    private void doSend(List<Mime.Address> rcpt, List<Mime.Address> copy) {
        Mime.Outgoing o = new Mime.Outgoing();
        o.from = account.address();
        o.to = rcpt;
        o.cc = copy;
        o.subject = subject.getText().toString().trim();
        o.text = body.getText().toString();
        o.inReplyTo = inReplyTo;
        o.references = references;
        o.attachments.addAll(attachments);
        ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.mail_sending), true, false);
        new Thread(() -> {
            String error = null;
            try {
                MailSync.send(getApplicationContext(), account, o);
            } catch (Smtp.SmtpException e) {
                error = e.auth() ? getString(R.string.mail_login_failed) : e.getMessage();
            } catch (IOException e) {
                error = getString(R.string.mail_no_connection) + " (" + e.getMessage() + ")";
            }
            String err = error;
            runOnUiThread(() -> {
                pd.dismiss();
                if (isDestroyed()) return;
                if (err != null) {
                    new AlertDialog.Builder(this).setMessage(getString(R.string.mail_send_failed, err)).setPositiveButton(R.string.ok, null).show();
                    return;
                }
                Toast.makeText(this, R.string.mail_sent_ok, Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    // ------------------------------------------------------------ Nova writes

    @Override
    public void use(String text) {
        int at = Math.max(0, body.getText().length() - quote.length());
        if (!body.getText().toString().startsWith(quote) && body.getText().toString().endsWith(quote)) at = body.getText().length() - quote.length();
        String before = body.getText().subSequence(0, at).toString();
        String insert = (before.trim().isEmpty() ? "" : (before.endsWith("\n") || before.endsWith(" ") ? "" : " ")) + text;
        body.getText().insert(at, insert);
        body.setSelection(at + insert.length());
    }

    @Override
    public String readAloud() {
        String t = body.getText().toString();
        if (!quote.isEmpty() && t.endsWith(quote)) t = t.substring(0, t.length() - quote.length());
        return t.trim().isEmpty() ? getString(R.string.mail_spoken_empty) : getString(R.string.mail_spoken_draft, t.trim());
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
