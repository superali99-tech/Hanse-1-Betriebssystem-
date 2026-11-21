package org.novaos.mobile;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Setting up NovaOS (first start, like a new phone): sign in (Google account of the phone,
 * Microsoft or any e-mail), screen lock (password, PIN or pattern - Android's own lock),
 * permissions, age (children: Nova Family). Every step can be skipped.
 */
public class SetupActivity extends Activity {
    private static final int WELCOME = 0, ACCOUNT = 1, LOCK = 2, PERMISSIONS = 3, AGE = 4, DONE = 5;
    private static final int PICK_GOOGLE = 61, PERMS = 62, ROLE_HOME = 63, ADD_GOOGLE = 64;
    private LinearLayout content;
    private int step = WELCOME;
    private int age = 18;

    static boolean done(Context c) {
        return Profile.setupDone(c);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.setup_title, "");
        findViewById(R.id.back).setOnClickListener(v -> onBackPressed());
        if (state != null) step = state.getInt("step", WELCOME);
        age = Profile.age(this) > 0 ? Profile.age(this) : 18;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("step", step);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show(step);  // also after the lock screen settings or a permission dialog
    }

    @Override
    public void onBackPressed() {
        if (step > WELCOME) show(step - 1);
        else super.onBackPressed();
    }

    private void show(int s) {
        step = s;
        content.removeAllViews();
        Page.status(this, null);
        TextView title = findViewById(R.id.page_title);
        TextView sub = findViewById(R.id.page_subtitle);
        sub.setText(s == WELCOME || s == DONE ? "" : getString(R.string.setup_step, s, 4));
        findViewById(R.id.back).setVisibility(s == WELCOME ? View.INVISIBLE : View.VISIBLE);
        switch (s) {
            case WELCOME: title.setText(R.string.setup_title); welcome(); break;
            case ACCOUNT: title.setText(R.string.setup_account_title); account(); break;
            case LOCK: title.setText(R.string.setup_lock_title); lock(); break;
            case PERMISSIONS: title.setText(R.string.setup_perm_title); permissions(); break;
            case AGE: title.setText(R.string.setup_age_title); age(); break;
            default: title.setText(R.string.setup_done_title); finished(); break;
        }
    }

    // ------------------------------------------------------------ building blocks

    private TextView text(String s, float size, boolean bold) {
        TextView t = Ui.text(this, s, size, Color.WHITE, bold);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private void addText(String s) {
        TextView t = text(s, 15, false);
        t.setTextColor(getColor(R.color.text_muted));
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 10));
        content.addView(t);
    }

    /** Big choice: icon, title, explanation. */
    private View option(int icon, String title, String sub, boolean accent, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(accent ? R.drawable.accent_card_bg : R.drawable.card_bg);
        int p = Ui.dp(this, 16);
        r.setPadding(p, p, p, p);
        if (icon != 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(icon);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 16.5f, true));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(sub, 13, false);
            s.setTextColor(accent ? 0xDDFFFFFF : getColor(R.color.text_muted));
            col.addView(s);
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cl.leftMargin = icon != 0 ? Ui.dp(this, 16) : 0;
        r.addView(col, cl);
        r.setClickable(true);
        r.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        r.setLayoutParams(lp);
        content.addView(r);
        return r;
    }

    private void nextButtons(int next, boolean skip) {
        View b = Ui.button(this, R.string.setup_next, v -> show(next));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 22);
        content.addView(b, lp);
        if (skip) {
            TextView s = text(getString(R.string.setup_skip), 15, true);
            s.setTextColor(0xFF9DB8FF);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 20));
            s.setOnClickListener(v -> show(next));
            content.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | type);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(getColor(R.color.text_muted));
        e.setBackgroundResource(R.drawable.pill_bg);
        e.setPadding(Ui.dp(this, 18), Ui.dp(this, 13), Ui.dp(this, 18), Ui.dp(this, 13));
        if ((type & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) e.setTypeface(Fonts.regular(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        content.addView(e, lp);
        return e;
    }

    // ------------------------------------------------------------ 0 welcome

    private void welcome() {
        View orb = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF8E6CFF, 0xFF3D7BFF, 0xFF22C1E8});
        g.setShape(GradientDrawable.OVAL);
        orb.setBackground(g);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(Ui.dp(this, 110), Ui.dp(this, 110));
        ol.gravity = Gravity.CENTER_HORIZONTAL;
        ol.topMargin = Ui.dp(this, 30);
        ol.bottomMargin = Ui.dp(this, 26);
        content.addView(orb, ol);
        TextView hello = text(getString(R.string.setup_welcome), 30, true);
        hello.setGravity(Gravity.CENTER);
        content.addView(hello, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = text(getString(R.string.setup_welcome_text), 16, false);
        t.setTextColor(getColor(R.color.text_muted));
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 10));
        content.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View b = Ui.button(this, R.string.setup_start, v -> show(ACCOUNT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 30);
        content.addView(b, lp);
        TextView later = text(getString(R.string.setup_later), 15, true);
        later.setTextColor(0xFF9DB8FF);
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 20));
        later.setOnClickListener(v -> {
            Profile.setSetupDone(this, true);
            finish();
        });
        content.addView(later, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------ 1 account

    private void account() {
        addText(getString(R.string.setup_account_text));
        EditText name = field(getString(R.string.setup_your_name), InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(Profile.name(this));
        if (!Profile.email(this).isEmpty()) {
            option(R.drawable.ic_check, getString(R.string.setup_signed_in, Profile.email(this)),
                    getString(Profile.verified(this) ? R.string.setup_signed_in_ok : R.string.setup_signed_in_unchecked), true,
                    v -> next(name, LOCK));
        }
        option(R.drawable.ic_account, getString(R.string.setup_google), getString(R.string.setup_google_sub), false, v -> {
            saveName(name);
            google();
        });
        option(R.drawable.ic_account, getString(R.string.setup_microsoft), getString(R.string.setup_microsoft_sub), false, v -> {
            saveName(name);
            emailForm("microsoft");
        });
        option(R.drawable.ic_mail, getString(R.string.setup_email), getString(R.string.setup_email_sub), false, v -> {
            saveName(name);
            emailForm("email");
        });
        option(R.drawable.ic_person_add, getString(R.string.setup_create), getString(R.string.setup_create_sub), false, v -> {
            saveName(name);
            create();
        });
        nextButtons(LOCK, true);
        content.getChildAt(content.getChildCount() - 2).setOnClickListener(v -> next(name, LOCK));
        content.getChildAt(content.getChildCount() - 1).setOnClickListener(v -> next(name, LOCK));
    }

    private void saveName(EditText name) {
        String n = name.getText().toString().trim();
        if (!n.isEmpty()) Profile.setName(this, n);
    }

    private void next(EditText name, int step) {
        saveName(name);
        show(step);
    }

    /** The Google account of the phone (Android's own account picker, also to add one). */
    private void google() {
        try {
            Intent i = AccountManager.newChooseAccountIntent(null, null, new String[] {"com.google"}, null, null, null, null);
            startActivityForResult(i, PICK_GOOGLE);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.setup_no_google, Toast.LENGTH_LONG).show();
            emailForm("email");
        }
    }

    private void create() {
        new AlertDialog.Builder(this).setTitle(R.string.setup_create)
                .setItems(new String[] {getString(R.string.setup_create_google), getString(R.string.setup_create_microsoft),
                        getString(R.string.setup_create_other)}, (d, w) -> {
                    if (w == 0) {
                        try {
                            startActivityForResult(new Intent(Settings.ACTION_ADD_ACCOUNT)
                                    .putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[] {"com.google"}), ADD_GOOGLE);
                        } catch (ActivityNotFoundException e) {
                            browse("https://accounts.google.com/signup");
                        }
                    } else if (w == 1) {
                        browse("https://signup.live.com");
                    } else {
                        Toast.makeText(this, R.string.setup_create_other_hint, Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void browse(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.nova_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    /** E-mail (checked at the mail server, and set up in Nova Mail) or Microsoft (only the address). */
    private void emailForm(String provider) {
        boolean ms = provider.equals("microsoft");
        content.removeAllViews();
        ((TextView) findViewById(R.id.page_title)).setText(ms ? R.string.setup_microsoft : R.string.setup_email);
        addText(getString(ms ? R.string.setup_microsoft_text : R.string.setup_email_text));
        EditText email = field(getString(ms ? R.string.setup_microsoft_hint : R.string.mail_address), InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (!Profile.email(this).isEmpty()) email.setText(Profile.email(this));
        EditText pass = ms ? null : field(getString(R.string.mail_password), InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox alsoMail = null;
        if (!ms) {
            alsoMail = new CheckBox(this);
            alsoMail.setText(R.string.setup_also_mail);
            alsoMail.setTextColor(Color.WHITE);
            alsoMail.setChecked(true);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.topMargin = Ui.dp(this, 10);
            content.addView(alsoMail, cl);
        }
        TextView status = text("", 14, false);
        status.setTextColor(getColor(R.color.text_muted));
        status.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), 0, 0);
        content.addView(status);
        View go = Ui.button(this, ms ? R.string.setup_next : R.string.mail_sign_in, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 18);
        content.addView(go, lp);
        TextView unchecked = text(getString(R.string.setup_without_check), 15, true);
        unchecked.setTextColor(0xFF9DB8FF);
        unchecked.setGravity(Gravity.CENTER);
        unchecked.setPadding(0, Ui.dp(this, 16), 0, 0);
        unchecked.setVisibility(View.GONE);
        content.addView(unchecked, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView back = text("← " + getString(R.string.setup_other_way), 15, true);
        back.setTextColor(0xFF9DB8FF);
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 20));
        back.setOnClickListener(v -> show(ACCOUNT));
        content.addView(back);
        CheckBox mailBox = alsoMail;
        unchecked.setOnClickListener(v -> {
            Profile.setAccount(this, null, email.getText().toString().trim(), provider, false);
            show(LOCK);
        });
        go.setOnClickListener(v -> {
            String e = email.getText().toString().trim();
            if (!Mime.validEmail(e)) {
                status.setText(R.string.setup_bad_email);
                return;
            }
            if (ms) {  // Microsoft does not allow checking a password here: only the address
                Profile.setAccount(this, null, e, "microsoft", false);
                show(LOCK);
                return;
            }
            String pw = pass.getText().toString();
            if (pw.isEmpty()) {
                status.setText(R.string.mail_fill_in);
                return;
            }
            go.setEnabled(false);
            status.setText(R.string.mail_finding);
            boolean addMail = mailBox != null && mailBox.isChecked();
            new Thread(() -> {
                MailStore.Account a = new MailStore.Account();
                a.email = e;
                a.name = Profile.name(this);
                MailServers.Settings s = MailSync.find(e);
                String err = null;
                if (s == null) {
                    err = getString(R.string.mail_no_server);
                } else {
                    a.s = s;
                    a.provider = s.provider;
                    runOnUiThread(() -> status.setText(getString(R.string.mail_checking, s.imapHost)));
                    try {
                        MailSync.test(a, pw);
                    } catch (Imap.ImapException | Smtp.SmtpException ex) {
                        err = getString(R.string.mail_wrong_password);
                    } catch (IOException ex) {
                        err = getString(R.string.mail_cannot_reach, s.imapHost);
                    }
                }
                String error = err;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    go.setEnabled(true);
                    if (error != null) {
                        status.setText(error);
                        unchecked.setVisibility(View.VISIBLE);
                        return;
                    }
                    Profile.setAccount(this, null, e, "email", true);
                    if (addMail) MailStore.addAccount(this, a, pw);
                    Toast.makeText(this, getString(R.string.setup_signed_in, e), Toast.LENGTH_SHORT).show();
                    show(LOCK);
                });
            }).start();
        });
    }

    // ------------------------------------------------------------ 2 screen lock

    private void lock() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        boolean secure = km != null && km.isDeviceSecure();
        addText(getString(R.string.setup_lock_text));
        if (secure) option(R.drawable.ic_check, getString(R.string.setup_lock_ok), getString(R.string.setup_lock_ok_sub), true, v -> show(PERMISSIONS));
        option(R.drawable.ic_key, getString(R.string.setup_lock_password), getString(R.string.setup_lock_password_sub), false,
                v -> setLock(R.string.setup_lock_choose_password));
        option(R.drawable.ic_lock, getString(R.string.setup_lock_pin), getString(R.string.setup_lock_pin_sub), false,
                v -> setLock(R.string.setup_lock_choose_pin));
        option(R.drawable.ic_shield, getString(R.string.setup_lock_pattern), getString(R.string.setup_lock_pattern_sub), false,
                v -> setLock(R.string.setup_lock_choose_pattern));
        nextButtons(PERMISSIONS, !secure);
    }

    /** Android's own screen lock (the only real lock of the phone). */
    private void setLock(int hint) {
        Toast.makeText(this, hint, Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD));
        } catch (ActivityNotFoundException | SecurityException e) {
            Launch.settings(this, Settings.ACTION_SECURITY_SETTINGS);
        }
    }

    // ------------------------------------------------------------ 3 permissions

    private static final class Perm {
        final int icon, title, sub;
        final String[] names;

        Perm(int icon, int title, int sub, String... names) {
            this.icon = icon;
            this.title = title;
            this.sub = sub;
            this.names = names;
        }
    }

    private List<Perm> perms() {
        List<Perm> l = new ArrayList<>();
        l.add(new Perm(R.drawable.ic_camera_switch, R.string.setup_perm_camera, R.string.setup_perm_camera_sub, Manifest.permission.CAMERA));
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(new Perm(R.drawable.ic_chat, R.string.setup_perm_notify, R.string.setup_perm_notify_sub, "android.permission.POST_NOTIFICATIONS"));
        }
        l.add(new Perm(R.drawable.ic_location, R.string.setup_perm_location, R.string.setup_perm_location_sub, Manifest.permission.ACCESS_COARSE_LOCATION));
        l.add(new Perm(R.drawable.ic_mic, R.string.setup_perm_mic, R.string.setup_perm_mic_sub, Manifest.permission.RECORD_AUDIO));
        l.add(new Perm(R.drawable.ic_image, R.string.setup_perm_photos, R.string.setup_perm_photos_sub, Media.permissions()));
        l.add(new Perm(R.drawable.ic_person_add, R.string.setup_perm_contacts, R.string.setup_perm_contacts_sub, Manifest.permission.READ_CONTACTS));
        return l;
    }

    private boolean granted(Perm p) {
        for (String n : p.names) if (checkSelfPermission(n) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    private void permissions() {
        addText(getString(R.string.setup_perm_text));
        List<String> missing = new ArrayList<>();
        for (Perm p : perms()) {
            boolean ok = granted(p);
            if (!ok) for (String n : p.names) missing.add(n);
            View row = option(ok ? R.drawable.ic_check : p.icon, getString(p.title),
                    ok ? getString(R.string.setup_perm_granted) : getString(p.sub), false,
                    v -> requestPermissions(p.names, PERMS));
            row.setAlpha(ok ? 0.75f : 1f);
        }
        if (!missing.isEmpty()) {
            View all = Ui.button(this, R.string.setup_perm_all, v -> requestPermissions(missing.toArray(new String[0]), PERMS));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 18);
            content.addView(all, lp);
        }
        nextButtons(AGE, false);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == PERMS && step == PERMISSIONS) show(PERMISSIONS);
    }

    // ------------------------------------------------------------ 4 age

    private void age() {
        addText(getString(R.string.setup_age_text));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        TextView minus = stepper("−");
        TextView value = text(String.valueOf(age), 56, true);
        value.setGravity(Gravity.CENTER);
        TextView plus = stepper("+");
        row.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        row.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 150), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(this, 16);
        content.addView(row, rl);
        TextView years = text(getString(R.string.setup_age_years), 15, false);
        years.setGravity(Gravity.CENTER);
        years.setTextColor(getColor(R.color.text_muted));
        content.addView(years, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView kids = text(getString(R.string.setup_age_child), 14, false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 16));
        bg.setColor(0x337C5CFF);
        kids.setBackground(bg);
        kids.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kl.topMargin = Ui.dp(this, 18);
        content.addView(kids, kl);
        Runnable update = () -> {
            value.setText(String.valueOf(age));
            kids.setVisibility(age < 16 ? View.VISIBLE : View.GONE);
        };
        update.run();
        minus.setOnClickListener(v -> {
            age = Math.max(3, age - 1);
            update.run();
        });
        plus.setOnClickListener(v -> {
            age = Math.min(110, age + 1);
            update.run();
        });
        View b = Ui.button(this, R.string.setup_next, v -> {
            Profile.setAge(this, age);
            show(DONE);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 22);
        content.addView(b, lp);
        TextView skip = text(getString(R.string.setup_skip), 15, true);
        skip.setTextColor(0xFF9DB8FF);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 20));
        skip.setOnClickListener(v -> show(DONE));
        content.addView(skip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView stepper(String s) {
        TextView t = text(s, 30, true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.round_bg);
        return t;
    }

    // ------------------------------------------------------------ 5 done

    private void finished() {
        String name = Profile.firstName(this);
        TextView t = text(name.isEmpty() ? getString(R.string.setup_done_text_noname) : getString(R.string.setup_done_text, name), 22, true);
        t.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 10));
        content.addView(t);
        addText(getString(R.string.setup_done_sub));
        if (!isDefaultHome()) {
            option(R.drawable.ic_home_set, getString(R.string.set_home), getString(R.string.setup_home_sub), false, v -> askHome());
        }
        View b = Ui.button(this, R.string.setup_finish, v -> {
            Profile.setSetupDone(this, true);
            finish();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 22);
        content.addView(b, lp);
    }

    private boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        ResolveInfo ri = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void askHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), ROLE_HOME);
                return;
            }
        }
        Launch.settings(this, Settings.ACTION_HOME_SETTINGS);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_GOOGLE && result == RESULT_OK && data != null) {
            String account = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (account != null && !account.isEmpty()) {
                Profile.setAccount(this, null, account, "google", true);
                Toast.makeText(this, getString(R.string.setup_signed_in, account), Toast.LENGTH_SHORT).show();
                step = LOCK;  // shown by onResume
            }
        } else if (request == ADD_GOOGLE) {
            google();  // the new account can now be chosen
        }
    }
}
