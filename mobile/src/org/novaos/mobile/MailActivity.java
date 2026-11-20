package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nova Mail: real e-mail with the user's own accounts (GMX, WEB.DE, Gmail, T-Online, Yahoo,
 * iCloud, ... over IMAP/SMTP). Setting up an account, the folders and the mail list; Nova
 * reads new mails aloud.
 */
public class MailActivity extends Activity implements NovaWriter.Target {
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private LinearLayout content;
    private MailStore.Account account;
    private String folder = "INBOX";
    private boolean loading;
    private NovaWriter nova;
    private boolean adding;  // setup of another account

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.app_mail, "");
        MailSync.MailJob.schedule(this);
        nova = new NovaWriter(this, this, false, R.string.mail_insert);
        ViewGroup parent = (ViewGroup) findViewById(R.id.scroller).getParent();
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int side = getResources().getDimensionPixelSize(R.dimen.side);
        nl.setMargins(side, Ui.dp(this, 6), side, Ui.dp(this, 10));
        parent.addView(nova, nl);
    }

    @Override
    protected void onResume() {
        super.onResume();
        account = MailStore.current(this);
        if (account == null || adding) {
            showSetup(null);
        } else {
            MailSync.cancelNotification(this, account.id);
            showList();
            refresh();
        }
    }

    @Override
    protected void onDestroy() {
        if (nova != null) nova.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (nova.isOpen()) nova.close();
        else if (adding && MailStore.current(this) != null) {
            adding = false;
            onResume();
        } else super.onBackPressed();
    }

    // ------------------------------------------------------------ mail list

    private void refresh() {
        if (loading || account == null) return;
        loading = true;
        Page.status(this, getString(R.string.mail_loading));
        MailStore.Account a = account;
        String f = folder;
        new Thread(() -> {
            String error = null;
            try {
                MailSync.refresh(getApplicationContext(), a, f);
            } catch (Imap.ImapException e) {
                error = e.auth ? getString(R.string.mail_login_failed) : e.getMessage();
            } catch (IOException e) {
                error = getString(R.string.mail_no_connection);
            }
            String err = error;
            runOnUiThread(() -> {
                loading = false;
                if (isDestroyed() || account == null) return;
                showList();
                Page.status(this, err);
            });
        }).start();
    }

    private void showList() {
        content.removeAllViews();
        ((TextView) findViewById(R.id.page_title)).setText(R.string.app_mail);
        ((TextView) findViewById(R.id.page_subtitle)).setText(account.email);

        // pills: account, refresh, Nova reads aloud
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pills = new LinearLayout(this);
        pills.addView(pill("👤 " + getString(R.string.mail_accounts), v -> accounts()));
        pills.addView(pill("⟳ " + getString(R.string.mail_refresh), v -> refresh()));
        pills.addView(pill("🔊 " + getString(R.string.mail_read_new), v -> {
            nova.setVisibility(View.VISIBLE);
            nova.readAloud();
        }));
        hs.addView(pills);
        content.addView(hs);

        // folders
        HorizontalScrollView fs = new HorizontalScrollView(this);
        fs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        MailStore db = MailStore.get(this);
        String[][] roles = {{"inbox", getString(R.string.mail_inbox)}, {"sent", getString(R.string.mail_sent)},
                {"drafts", getString(R.string.mail_drafts)}, {"junk", getString(R.string.mail_junk)}, {"trash", getString(R.string.mail_trash)},
                {"archive", getString(R.string.mail_archive)}};
        List<String> shownFolders = new ArrayList<>();
        for (String[] r : roles) {
            String name = db.folder(account.id, r[0]);
            if (name == null) continue;
            shownFolders.add(name);
            int unread = r[0].equals("inbox") ? db.unreadCount(account.id, name) : 0;
            chips.addView(folderChip(r[1] + (unread > 0 ? " · " + unread : ""), name));
        }
        for (MailStore.Folder f : db.folders(account.id)) {  // the user's own folders
            if (!shownFolders.contains(f.name) && f.role.isEmpty()) chips.addView(folderChip(Utf7.decode(f.name), f.name));
        }
        fs.addView(chips);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.topMargin = Ui.dp(this, 10);
        content.addView(fs, fl);

        List<MailStore.Mail> mails = db.mails(account.id, folder, MailSync.PAGE);
        if (mails.isEmpty() && !loading) {
            TextView t = Ui.text(this, getString(R.string.mail_empty), 15, getColor(R.color.text_muted), false);
            t.setPadding(Ui.dp(this, 6), Ui.dp(this, 30), Ui.dp(this, 6), 0);
            t.setGravity(Gravity.CENTER);
            content.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        for (MailStore.Mail m : mails) content.addView(row(m));
        if (findViewById(R.id.mail_fab) == null) {
            ImageButton fab = Ui.fab(this, R.drawable.ic_edit, R.string.mail_compose,
                    v -> startActivity(new Intent(this, MailComposeActivity.class)));
            fab.setId(R.id.mail_fab);
        }
    }

    private TextView pill(String text, View.OnClickListener click) {
        TextView t = Ui.text(this, text, 14, Color.WHITE, true);
        t.setBackgroundResource(R.drawable.pill_bg);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        lp.topMargin = Ui.dp(this, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView folderChip(String label, String name) {
        TextView t = Ui.text(this, label, 14, Color.WHITE, name.equals(folder));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 18));
        if (name.equals(folder)) bg.setColors(new int[] {0xFF7C5CFF, 0xFF3D7BFF});
        else bg.setColor(0x26FFFFFF);
        bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        t.setBackground(bg);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        t.setOnClickListener(v -> {
            folder = name;
            showList();
            refresh();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    private View row(MailStore.Mail m) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 12);
        row.setPadding(p, p, p, p);
        String who = "sent".equals(roleOf(folder)) || "drafts".equals(roleOf(folder)) ? firstName(m.to) : m.sender();
        row.addView(ChatActivity.avatar(this, who.isEmpty() ? "?" : who, 46));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(this, who, 15.5f, Color.WHITE, !m.seen);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView date = Ui.text(this, when(m.date), 12, !m.seen ? 0xFFB9A8FF : getColor(R.color.text_muted), !m.seen);
        top.addView(date);
        col.addView(top);
        TextView subj = Ui.text(this, (m.attachment ? "📎 " : "") + (m.subject.isEmpty() ? getString(R.string.mail_no_subject) : m.subject),
                14, Color.WHITE, !m.seen);
        subj.setSingleLine(true);
        subj.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(subj);
        if (!m.preview.isEmpty()) {
            TextView prev = Ui.text(this, m.preview, 13, getColor(R.color.text_muted), false);
            prev.setMaxLines(2);
            prev.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(prev);
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cl.leftMargin = Ui.dp(this, 12);
        row.addView(col, cl);
        if (!m.seen) {
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0xFF7C5CFF);
            dot.setBackground(d);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10));
            dl.leftMargin = Ui.dp(this, 8);
            row.addView(dot, dl);
        }
        row.setClickable(true);
        row.setOnClickListener(v -> startActivity(new Intent(this, MailReadActivity.class)
                .putExtra(MailReadActivity.EXTRA_ACCOUNT, account.id).putExtra(MailReadActivity.EXTRA_FOLDER, folder)
                .putExtra(MailReadActivity.EXTRA_UID, m.uid)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);
        return row;
    }

    private String roleOf(String name) {
        for (MailStore.Folder f : MailStore.get(this).folders(account.id)) if (f.name.equals(name)) return f.role;
        return name.equals("INBOX") ? "inbox" : "";
    }

    private static String firstName(String header) {
        List<Mime.Address> l = Mime.addresses(header);
        return l.isEmpty() ? "" : l.get(0).display();
    }

    private String when(long ms) {
        if (ms <= 0) return "";
        if (DateUtils.isToday(ms)) return android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(ms));
        return DateUtils.formatDateTime(this, ms, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH
                | (System.currentTimeMillis() - ms > 300L * 86400000 ? DateUtils.FORMAT_SHOW_YEAR : DateUtils.FORMAT_NO_YEAR));
    }

    private void accounts() {
        List<MailStore.Account> all = MailStore.accounts(this);
        List<String> items = new ArrayList<>();
        for (MailStore.Account a : all) items.add((a.id.equals(account.id) ? "✓ " : "") + a.email);
        items.add("➕ " + getString(R.string.mail_add_account));
        items.add("🗑 " + getString(R.string.mail_remove_account, account.email));
        new AlertDialog.Builder(this).setTitle(R.string.mail_accounts).setItems(items.toArray(new String[0]), (d, w) -> {
            if (w < all.size()) {
                MailStore.setCurrent(this, all.get(w).id);
                folder = "INBOX";
                onResume();
            } else if (w == all.size()) {
                adding = true;
                showSetup(null);
            } else {
                new AlertDialog.Builder(this).setMessage(getString(R.string.mail_remove_confirm, account.email))
                        .setPositiveButton(R.string.delete, (dd, ww) -> {
                            MailStore.get(this).removeAccount(this, account.id);
                            folder = "INBOX";
                            onResume();
                        }).setNegativeButton(R.string.cancel, null).show();
            }
        }).show();
    }

    // ------------------------------------------------------------ setting up an account

    private void showSetup(MailServers.Provider chosen) {
        content.removeAllViews();
        View fab = findViewById(R.id.mail_fab);
        if (fab != null) ((ViewGroup) fab.getParent()).removeView(fab);
        ((TextView) findViewById(R.id.page_title)).setText(R.string.app_mail);
        ((TextView) findViewById(R.id.page_subtitle)).setText(R.string.mail_sub);
        Page.status(this, null);
        if (chosen == null) {
            TextView intro = Ui.text(this, getString(R.string.mail_intro), 15, Color.WHITE, false);
            intro.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
            content.addView(intro);
            String[] keys = {"gmx", "webde", "gmail", "tonline", "outlook", "yahoo", "icloud", "freenet"};
            LinearLayout row = null;
            for (int i = 0; i <= keys.length; i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(this);
                    LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rl.topMargin = Ui.dp(this, 8);
                    content.addView(row, rl);
                }
                MailServers.Provider p = i < keys.length ? MailServers.provider(keys[i]) : null;
                String label = p != null ? p.name : getString(R.string.mail_other);
                TextView b = Ui.text(this, label, 16, Color.WHITE, true);
                b.setGravity(Gravity.CENTER);
                b.setBackgroundResource(p == null ? R.drawable.accent_card_bg : R.drawable.card_bg);
                b.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 18));
                b.setOnClickListener(v -> showSetup(p != null ? p : new MailServers.Provider("", getString(R.string.mail_other), "", 993, "", 465, false, "")));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                bl.rightMargin = i % 2 == 0 ? Ui.dp(this, 6) : 0;
                bl.leftMargin = i % 2 == 1 ? Ui.dp(this, 6) : 0;
                row.addView(b, bl);
            }
            if (keys.length % 2 == 1) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            TextView privacy = Ui.text(this, getString(R.string.mail_privacy), 12, getColor(R.color.text_muted), false);
            privacy.setPadding(Ui.dp(this, 2), Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 10));
            content.addView(privacy);
            return;
        }
        boolean other = chosen.key.isEmpty();
        TextView title = Ui.text(this, other ? getString(R.string.mail_setup_other) : getString(R.string.mail_setup_with, chosen.name), 19, Color.WHITE, true);
        title.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        content.addView(title);
        String hint = hint(chosen.key);
        if (!hint.isEmpty()) {
            TextView h = Ui.text(this, hint, 14, 0xFFE6DEFF, false);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 14));
            bg.setColor(0x337C5CFF);
            h.setBackground(bg);
            h.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            content.addView(h);
            if (!chosen.help.isEmpty()) {
                TextView link = Ui.text(this, "↗ " + getString(R.string.mail_open_help), 14, 0xFF9DB8FF, true);
                link.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, Ui.dp(this, 4));
                link.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(chosen.help)));
                    } catch (RuntimeException e) {
                        Toast.makeText(this, R.string.nova_no_app, Toast.LENGTH_SHORT).show();
                    }
                });
                content.addView(link);
            }
        }
        EditText name = field(getString(R.string.mail_your_name), InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email = field(getString(R.string.mail_address), InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (!other && chosen.domains.length > 0) email.setHint("name@" + chosen.domains[0]);
        EditText pass = field(getString(chosen.key.equals("gmail") || chosen.key.equals("yahoo") || chosen.key.equals("icloud")
                || chosen.key.equals("aol") ? R.string.mail_app_password : R.string.mail_password), InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // manual server settings ("Andere")
        LinearLayout manual = new LinearLayout(this);
        manual.setOrientation(LinearLayout.VERTICAL);
        label(manual, R.string.mail_label_imap);
        EditText imapHost = field(getString(R.string.mail_imap_server), InputType.TYPE_TEXT_VARIATION_URI, manual);
        label(manual, R.string.mail_label_imap_port);
        EditText imapPort = field("993", InputType.TYPE_CLASS_NUMBER, manual);
        imapPort.setText("993");
        label(manual, R.string.mail_label_smtp);
        EditText smtpHost = field(getString(R.string.mail_smtp_server), InputType.TYPE_TEXT_VARIATION_URI, manual);
        label(manual, R.string.mail_label_smtp_port);
        EditText smtpPort = field("465", InputType.TYPE_CLASS_NUMBER, manual);
        smtpPort.setText("465");
        label(manual, R.string.mail_label_user);
        EditText user = field(getString(R.string.mail_user_optional), InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, manual);
        TextView manualHint = Ui.text(this, getString(R.string.mail_manual_hint), 12, getColor(R.color.text_muted), false);
        manualHint.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), 0, 0);
        manual.addView(manualHint);
        manual.setVisibility(View.GONE);
        content.addView(manual);
        TextView more = Ui.text(this, "⚙ " + getString(R.string.mail_manual), 14, 0xFF9DB8FF, true);
        more.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, Ui.dp(this, 4));
        more.setOnClickListener(v -> manual.setVisibility(manual.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        content.addView(more);
        TextView status = Ui.text(this, "", 14, getColor(R.color.text_muted), false);
        status.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), 0, 0);
        content.addView(status);
        View login = Ui.button(this, R.string.mail_sign_in, null);
        login.setOnClickListener(v -> {
            String e = email.getText().toString().trim(), pw = pass.getText().toString();
            if (!Mime.validEmail(e) || pw.isEmpty()) {
                status.setText(R.string.mail_fill_in);
                return;
            }
            login.setEnabled(false);
            status.setText(R.string.mail_finding);
            String n = name.getText().toString().trim();
            String ih = imapHost.getText().toString().trim(), sh = smtpHost.getText().toString().trim();
            int ip = parse(imapPort.getText().toString(), 993), sp = parse(smtpPort.getText().toString(), 465);
            String u = user.getText().toString().trim();
            new Thread(() -> {
                MailStore.Account a = new MailStore.Account();
                a.email = e;
                a.name = n;
                MailServers.Settings s;
                if (!ih.isEmpty() && !sh.isEmpty()) {  // typed by hand
                    s = new MailServers.Settings();
                    s.imapHost = ih;
                    s.imapPort = ip;
                    s.imapSecurity = ip == 143 ? "starttls" : "ssl";
                    s.smtpHost = sh;
                    s.smtpPort = sp;
                    s.smtpSecurity = sp == 587 || sp == 25 ? "starttls" : "ssl";
                    s.user = u.isEmpty() ? e : u;
                    s.source = "manual";
                } else if (!chosen.key.isEmpty() && MailServers.providerFor(e) == null) {
                    s = MailServers.of(chosen, e);  // e.g. own domain at a known provider
                } else {
                    s = MailSync.find(e);
                }
                if (s == null) {
                    runOnUiThread(() -> {
                        login.setEnabled(true);
                        status.setText(R.string.mail_no_server);
                        manual.setVisibility(View.VISIBLE);
                    });
                    return;
                }
                if (!u.isEmpty()) s.user = u;
                a.s = s;
                a.provider = s.provider;
                runOnUiThread(() -> status.setText(getString(R.string.mail_checking, s.imapHost)));
                String error = null;
                try {
                    MailSync.test(a, pw);
                } catch (Imap.ImapException ex) {
                    error = ex.auth ? getString(R.string.mail_wrong_password) + "\n" + hint(a.provider) : ex.getMessage();
                } catch (Smtp.SmtpException ex) {
                    error = ex.auth() ? getString(R.string.mail_wrong_password) + "\n" + hint(a.provider) : ex.getMessage();
                } catch (IOException ex) {
                    error = getString(R.string.mail_cannot_reach, s.imapHost) + " (" + ex.getClass().getSimpleName() + ")";
                }
                String err = error;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    login.setEnabled(true);
                    if (err != null) {
                        status.setText(err.trim());
                        if (s.source.equals("guess")) manual.setVisibility(View.VISIBLE);
                        return;
                    }
                    MailStore.addAccount(this, a, pw);
                    adding = false;
                    Toast.makeText(this, getString(R.string.mail_added, e), Toast.LENGTH_LONG).show();
                    folder = "INBOX";
                    onResume();
                });
            }).start();
        });
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = Ui.dp(this, 14);
        content.addView(login, ll);
        TextView back = Ui.text(this, "← " + getString(R.string.mail_other_provider), 14, 0xFF9DB8FF, true);
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), 0, Ui.dp(this, 20));
        back.setOnClickListener(v -> showSetup(null));
        content.addView(back);
    }

    private void label(LinearLayout into, int text) {
        TextView t = Ui.text(this, getString(text), 13, getColor(R.color.text_muted), true);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), 0, 0);
        into.addView(t);
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** What the user must know for this provider (app password, switch on IMAP, ...). */
    private String hint(String provider) {
        switch (provider == null ? "" : provider) {
            case "gmail": return getString(R.string.mail_hint_gmail);
            case "gmx": case "webde": return getString(R.string.mail_hint_gmx);
            case "tonline": return getString(R.string.mail_hint_tonline);
            case "outlook": return getString(R.string.mail_hint_outlook);
            case "yahoo": case "aol": return getString(R.string.mail_hint_yahoo);
            case "icloud": return getString(R.string.mail_hint_icloud);
            default: return "";
        }
    }

    private EditText field(String hint, int type) {
        return field(hint, type, content);
    }

    private EditText field(String hint, int type, LinearLayout into) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | type);
        if (type == InputType.TYPE_CLASS_NUMBER) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(getColor(R.color.text_muted));
        e.setBackgroundResource(R.drawable.pill_bg);
        e.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        if ((type & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) e.setTypeface(Typeface.DEFAULT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        into.addView(e, lp);
        return e;
    }

    // ------------------------------------------------------------ Nova reads aloud

    @Override
    public void use(String text) {
        startActivity(new Intent(this, MailComposeActivity.class).putExtra(MailComposeActivity.EXTRA_BODY, text));
    }

    @Override
    public String readAloud() {
        if (account == null) return "";
        List<MailStore.Mail> unread = MailStore.get(this).unread(account.id, "INBOX", 10);
        if (unread.isEmpty()) return getString(R.string.mail_spoken_none);
        StringBuilder b = new StringBuilder(getResources().getQuantityString(R.plurals.mail_spoken_count, unread.size(), unread.size())).append(' ');
        for (MailStore.Mail m : unread) {
            b.append(getString(R.string.mail_spoken_one, m.sender(), m.subject.isEmpty() ? getString(R.string.mail_no_subject) : m.subject)).append(' ');
        }
        return b.toString().trim();
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
