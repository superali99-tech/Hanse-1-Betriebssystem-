package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
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
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * The NovaOS account: who is signed in, screen lock and fingerprint, protection of the Nova
 * apps, change the name, forgot the PIN, sign out. Changes need a confirmation first: the
 * screen lock or fingerprint (asked by Android), or - without a screen lock - a code sent
 * by e-mail through the user's own Nova Mail account.
 */
public class AccountActivity extends Activity {
    private static final int CONFIRM = 7;
    private LinearLayout content;
    private Runnable afterConfirm;
    private String code;
    private long codeSent;
    private int codeTries;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.account_title, getString(R.string.account_sub));
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    private void show() {
        content.removeAllViews();
        String name = Profile.name(this), email = Profile.email(this), provider = Profile.provider(this);
        boolean signedIn = !email.isEmpty();

        // who
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        head.addView(ChatActivity.avatar(this, name.isEmpty() ? (email.isEmpty() ? "?" : email) : name, 64));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.addView(Ui.text(this, name.isEmpty() ? getString(R.string.account_no_name) : name, 22, Color.WHITE, true));
        who.addView(Ui.text(this, signedIn ? email + (Profile.verified(this) ? "  ✓" : "") : getString(R.string.account_signed_out), 14,
                getColor(R.color.text_muted), false));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        wl.leftMargin = Ui.dp(this, 14);
        head.addView(who, wl);
        content.addView(head);

        // information
        content.addView(Ui.section(this, R.string.account_info));
        LinearLayout info = card();
        infoRow(info, R.string.account_name, name.isEmpty() ? "—" : name);
        infoRow(info, R.string.account_email, signedIn ? email : "—");
        infoRow(info, R.string.account_provider, getString(provider.equals("google") ? R.string.account_google
                : provider.equals("microsoft") ? R.string.account_microsoft : provider.equals("email") ? R.string.account_mail : R.string.account_none));
        infoRow(info, R.string.account_checked, getString(Profile.verified(this) ? R.string.account_yes : R.string.account_no));
        infoRow(info, R.string.account_age, Profile.age(this) > 0 ? String.valueOf(Profile.age(this)) : "—");
        infoRow(info, R.string.account_device, Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        content.addView(info);

        // security
        content.addView(Ui.section(this, R.string.account_security));
        LinearLayout sec = card();
        boolean secure = AppLock.secure(this);
        infoRow(sec, R.string.account_screen_lock, getString(secure ? R.string.account_lock_on : R.string.account_lock_off));
        infoRow(sec, R.string.account_fingerprint, getString(biometricState()));
        content.addView(sec);
        if (!secure) content.addView(action("🔒 " + getString(R.string.account_set_lock), false, v -> setLock()));
        if (secure && biometricState() == R.string.account_bio_none) content.addView(action("☝ " + getString(R.string.account_add_fingerprint), false, v -> addFingerprint()));

        // Nova apps behind the screen lock
        LinearLayout lockRow = card();
        LinearLayout lr = new LinearLayout(this);
        lr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout lt = new LinearLayout(this);
        lt.setOrientation(LinearLayout.VERTICAL);
        lt.addView(Ui.text(this, getString(R.string.account_app_lock), 16, Color.WHITE, true));
        lt.addView(Ui.text(this, getString(secure ? R.string.account_app_lock_sub : R.string.account_app_lock_needs), 13,
                getColor(R.color.text_muted), false));
        lr.addView(lt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        sw.setChecked(AppLock.enabled(this));
        sw.setEnabled(secure);
        sw.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                AppLock.setEnabled(this, true);
                return;
            }
            sw.setChecked(true);  // switching off needs the confirmation
            confirm(getString(R.string.account_reason_app_lock), () -> {
                AppLock.setEnabled(this, false);
                show();
            });
        });
        lr.addView(sw);
        lockRow.addView(lr);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = Ui.dp(this, 10);
        content.addView(lockRow, ll);

        // actions
        content.addView(Ui.section(this, R.string.account_actions));
        content.addView(action("✏️  " + getString(R.string.account_change_name), false,
                v -> confirm(getString(R.string.account_reason_name), this::changeName)));
        if (secure) content.addView(action("🔑  " + getString(R.string.account_change_lock), false, v -> setLock()));
        content.addView(action("❓  " + getString(R.string.account_forgot), false, v -> forgot()));
        content.addView(action("🧭  " + getString(R.string.account_setup_again), false,
                v -> startActivity(new Intent(this, SetupActivity.class))));
        if (signedIn || !name.isEmpty()) content.addView(action("⎋  " + getString(R.string.account_sign_out), true,
                v -> confirm(getString(R.string.account_reason_sign_out), this::signOut)));
        TextView note = Ui.text(this, getString(R.string.account_note), 12, getColor(R.color.text_muted), false);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 10));
        content.addView(note);
    }

    private int biometricState() {
        if (Build.VERSION.SDK_INT < 29) return R.string.account_bio_unknown;
        BiometricManager bm = getSystemService(BiometricManager.class);
        if (bm == null) return R.string.account_bio_unknown;
        int r = Build.VERSION.SDK_INT >= 30 ? bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) : bm.canAuthenticate();
        return r == BiometricManager.BIOMETRIC_SUCCESS ? R.string.account_bio_on
                : r == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ? R.string.account_bio_none : R.string.account_bio_missing;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card_bg);
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        return c;
    }

    private void infoRow(LinearLayout box, int label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        r.addView(Ui.text(this, getString(label), 14, getColor(R.color.text_muted), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = Ui.text(this, value, 14, Color.WHITE, false);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        box.addView(r);
    }

    private TextView action(String text, boolean danger, View.OnClickListener click) {
        TextView t = Ui.text(this, text, 16, danger ? 0xFFFF8A8A : Color.WHITE, true);
        t.setBackgroundResource(R.drawable.card_bg);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------ confirmation before changes

    /**
     * With a screen lock: Android asks for PIN, pattern, password or fingerprint. Without:
     * a 6-digit code by e-mail (through the own Nova Mail account of the profile address),
     * otherwise a question (and the advice to set up a screen lock).
     */
    private void confirm(String reason, Runnable ok) {
        if (AppLock.secure(this)) {
            afterConfirm = ok;
            startActivityForResult(new Intent(this, ConfirmActivity.class).putExtra(ConfirmActivity.EXTRA_REASON, reason), CONFIRM);
            return;
        }
        MailStore.Account mail = null;
        for (MailStore.Account a : MailStore.accounts(this)) if (a.email.equalsIgnoreCase(Profile.email(this))) mail = a;
        if (mail != null) {
            sendCode(mail, reason, ok);
            return;
        }
        new AlertDialog.Builder(this).setTitle(reason).setMessage(R.string.account_no_protection)
                .setPositiveButton(R.string.ok, (d, w) -> ok.run()).setNegativeButton(R.string.cancel, null).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == CONFIRM && result == RESULT_OK && afterConfirm != null) afterConfirm.run();
        else if (request == CONFIRM) Toast.makeText(this, R.string.account_not_confirmed, Toast.LENGTH_SHORT).show();
        afterConfirm = null;
    }

    private void sendCode(MailStore.Account account, String reason, Runnable ok) {
        code = LockRule.newCode();
        codeSent = System.currentTimeMillis();
        codeTries = 0;
        String c = code;
        Toast.makeText(this, getString(R.string.account_code_sending, account.email), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String error = null;
            try {
                Mime.Outgoing o = new Mime.Outgoing();
                o.from = new Mime.Address(account.name, account.email);
                o.to.add(new Mime.Address(account.name, account.email));
                o.subject = getString(R.string.account_code_subject, c);
                o.text = getString(R.string.account_code_text, c, reason);
                MailSync.send(getApplicationContext(), account, o);
            } catch (IOException | RuntimeException e) {
                error = e.getMessage();
            }
            String err = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (err != null) {
                    Toast.makeText(this, getString(R.string.account_code_failed, err), Toast.LENGTH_LONG).show();
                    return;
                }
                askCode(account.email, ok);
            });
        }).start();
    }

    private void askCode(String email, Runnable ok) {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setHint("123456");
        field.setTextSize(24);
        field.setGravity(Gravity.CENTER);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog d = new AlertDialog.Builder(this).setTitle(R.string.account_code_title)
                .setMessage(getString(R.string.account_code_message, email)).setView(box)
                .setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean right = LockRule.codeOk(code, codeSent, codeTries, field.getText().toString(), System.currentTimeMillis());
            codeTries++;
            if (right) {
                code = null;
                d.dismiss();
                ok.run();
            } else {
                field.setError(getString(codeTries >= LockRule.CODE_TRIES ? R.string.account_code_blocked : R.string.account_code_wrong));
                if (codeTries >= LockRule.CODE_TRIES) code = null;
            }
        }));
        d.show();
    }

    // ------------------------------------------------------------ the actions

    private void changeName() {
        EditText field = new EditText(this);
        field.setText(Profile.name(this));
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(R.string.account_change_name).setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    Profile.setName(this, field.getText().toString());
                    show();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void setLock() {
        try {
            startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD));
        } catch (ActivityNotFoundException e) {
            Launch.settings(this, Settings.ACTION_SECURITY_SETTINGS);
        }
    }

    private void addFingerprint() {
        Intent i = Build.VERSION.SDK_INT >= 30 ? new Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                : Build.VERSION.SDK_INT >= 28 ? new Intent(Settings.ACTION_FINGERPRINT_ENROLL) : new Intent(Settings.ACTION_SECURITY_SETTINGS);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Launch.settings(this, Settings.ACTION_SECURITY_SETTINGS);
        }
    }

    /** Forgot PIN, pattern or password: what really helps (Android does not let any app reset it). */
    private void forgot() {
        String[] items = {getString(R.string.account_forgot_new), getString(R.string.account_forgot_find), getString(R.string.account_forgot_samsung),
                getString(R.string.account_forgot_reset)};
        new AlertDialog.Builder(this).setTitle(R.string.account_forgot).setItems(items, (d, w) -> {
            if (w == 0) setLock();
            else if (w == 1) open("https://www.google.com/android/find");
            else if (w == 2) open("https://smartthingsfind.samsung.com/");
            else new AlertDialog.Builder(this).setTitle(R.string.account_forgot_reset).setMessage(R.string.account_reset_text)
                        .setPositiveButton(R.string.account_open_settings, (dd, ww) -> confirm(getString(R.string.account_reason_reset),
                                () -> Launch.settings(this, Settings.ACTION_SETTINGS)))
                        .setNegativeButton(R.string.cancel, null).show();
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private void signOut() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        CheckBox mail = new CheckBox(this);
        mail.setText(R.string.account_sign_out_mail);
        CheckBox chat = new CheckBox(this);
        chat.setText(R.string.account_sign_out_chat);
        if (!MailStore.accounts(this).isEmpty()) box.addView(mail);
        if (ChatDb.loggedIn(this)) box.addView(chat);
        new AlertDialog.Builder(this).setTitle(R.string.account_sign_out).setMessage(R.string.account_sign_out_text).setView(box)
                .setPositiveButton(R.string.account_sign_out, (d, w) -> {
                    if (mail.isChecked()) {
                        for (MailStore.Account a : MailStore.accounts(this)) MailStore.get(this).removeAccount(this, a.id);
                    }
                    if (chat.isChecked()) {
                        MatrixApi api = ChatDb.api(this);
                        ChatSync.get(this).stop();
                        ChatSync.ChatJob.cancel(this);
                        new Thread(api::logout).start();
                        ChatDb.get(this).logout(this);
                    }
                    Profile.signOut(this);
                    Toast.makeText(this, R.string.account_signed_out_toast, Toast.LENGTH_LONG).show();
                    show();
                }).setNegativeButton(R.string.cancel, null).show();
    }
}
