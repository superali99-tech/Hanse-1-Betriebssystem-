package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The address book of Nova Mail: saved contacts with name, e-mail address, phone number and
 * a note. They are suggested first when writing and can be saved straight from a mail.
 * Stored on the phone only (mail.db).
 */
public class MailContactsActivity extends Activity {
    static final String EXTRA_NAME = "name", EXTRA_EMAIL = "email";
    private LinearLayout content;
    private String query = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.mail_contacts, getString(R.string.mail_contacts_sub));
        Ui.fab(this, R.drawable.ic_person_add, R.string.mail_contact_add, v -> edit(this, null, "", "", this::show));
        Page.search(this, R.string.contacts_search, q -> {
            query = q;
            show();
        });
        show();
        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (state == null && email != null) {
            MailStore.Contact known = MailStore.get(this).contact(email);
            edit(this, known, getIntent().getStringExtra(EXTRA_NAME), email, this::show);
        }
    }

    private void show() {
        content.removeAllViews();
        List<MailStore.Contact> all = MailStore.get(this).contacts();
        Page.status(this, all.isEmpty() ? getString(R.string.mail_contacts_empty) : null);
        int shown = 0;
        for (MailStore.Contact k : all) {
            if (!Search.matches(query, k.name, k.email, k.phone, k.note)) continue;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.card_bg);
            int p = Ui.dp(this, 12);
            row.setPadding(p, p, p, p);
            row.addView(ChatActivity.avatar(this, k.display(), 44));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(this, k.display(), 16, Color.WHITE, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            String sub = k.email + (k.phone.isEmpty() ? "" : "  ·  " + k.phone);
            TextView s = Ui.text(this, sub, 13, getColor(R.color.text_muted), false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(s);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tl.leftMargin = Ui.dp(this, 12);
            row.addView(texts, tl);
            row.setOnClickListener(v -> actions(k));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            content.addView(row, lp);
        }
        if (shown == 0 && !all.isEmpty()) {
            TextView none = Ui.text(this, getString(R.string.search_none), 15, getColor(R.color.text_muted), false);
            none.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, 0);
            content.addView(none);
        }
    }

    private void actions(MailStore.Contact k) {
        List<String> items = new ArrayList<>();
        List<Runnable> run = new ArrayList<>();
        items.add("✉️  " + getString(R.string.mail_contact_write));
        run.add(() -> startActivity(new Intent(this, MailComposeActivity.class)
                .putExtra(MailComposeActivity.EXTRA_TO, new Mime.Address(k.name, k.email).toString())));
        if (!k.phone.isEmpty()) {
            items.add("📞  " + getString(R.string.mail_contact_call, k.phone));
            run.add(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(k.phone))));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                }
            });
        }
        items.add("✏️  " + getString(R.string.mail_contact_edit));
        run.add(() -> edit(this, k, k.name, k.email, this::show));
        items.add("🗑  " + getString(R.string.delete));
        run.add(() -> new AlertDialog.Builder(this).setMessage(getString(R.string.delete_confirm, k.display()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    MailStore.get(this).deleteContact(k.email);
                    show();
                }).setNegativeButton(R.string.cancel, null).show());
        // a message and a list do not go together in one dialog: the note is the first line
        if (!k.note.isEmpty()) {
            items.add(0, "📝  " + k.note);
            run.add(0, () -> {});
        }
        new AlertDialog.Builder(this).setTitle(k.display())
                .setItems(items.toArray(new String[0]), (d, w) -> run.get(w).run()).show();
    }

    /** Dialog to add or change a contact (also used from a mail: "save as contact"). */
    static void edit(Activity a, MailStore.Contact existing, String name, String email, Runnable done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), 0);
        EditText n = field(a, box, R.string.mail_contact_name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText e = field(a, box, R.string.mail_contact_email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText ph = field(a, box, R.string.mail_contact_phone, InputType.TYPE_CLASS_PHONE);
        EditText note = field(a, box, R.string.mail_contact_note, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        n.setText(existing != null ? existing.name : name == null ? "" : name);
        e.setText(existing != null ? existing.email : email == null ? "" : email);
        if (existing != null) {
            ph.setText(existing.phone);
            note.setText(existing.note);
        }
        AlertDialog d = new AlertDialog.Builder(a).setTitle(existing == null ? R.string.mail_contact_add : R.string.mail_contact_edit)
                .setView(box).setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String mail = e.getText().toString().trim();
            if (!Mime.validEmail(mail)) {
                e.setError(a.getString(R.string.mail_contact_bad_email));
                return;
            }
            MailStore.Contact k = new MailStore.Contact(n.getText().toString().trim(), mail);
            k.phone = ph.getText().toString().trim();
            k.note = note.getText().toString().trim();
            MailStore.get(a).saveContact(existing == null ? null : existing.email, k);
            Toast.makeText(a, a.getString(R.string.mail_contact_saved, k.display()), Toast.LENGTH_SHORT).show();
            d.dismiss();
            if (done != null) done.run();
        }));
        d.show();
    }

    private static EditText field(Activity a, LinearLayout box, int hint, int type) {
        EditText t = new EditText(a);
        t.setHint(hint);
        t.setSingleLine(true);
        t.setInputType(type);
        box.addView(t);
        return t;
    }
}
