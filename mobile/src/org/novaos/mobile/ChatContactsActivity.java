package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

/**
 * Contacts of Nova Chat. They are saved in the Matrix account on the server
 * ("org.novaos.contacts"), so they are there again on every phone after logging in.
 */
public class ChatContactsActivity extends Activity implements ChatSync.Listener {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!ChatDb.loggedIn(this)) {
            finish();
            return;
        }
        content = Page.setup(this, R.string.chat_contacts, getString(R.string.chat_contacts_sub));
        Ui.fab(this, R.drawable.ic_person_add, R.string.chat_add_contact, v -> add());
        refreshFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChatSync.get(this).addListener(this);
        show();
    }

    @Override
    protected void onPause() {
        ChatSync.get(this).removeListener(this);
        super.onPause();
    }

    @Override
    public void onChatChanged() {
        if (!isDestroyed()) show();
    }

    private void refreshFromServer() {
        MatrixApi api = ChatDb.api(this);
        String me = ChatDb.me(this);
        new Thread(() -> {
            try {
                List<MatrixSync.Contact> list = MatrixSync.contacts(MiniJson.parse(api.accountData(me, MatrixSync.CONTACTS_TYPE)));
                ChatDb.get(this).setContacts(list);
                runOnUiThread(this::show);
            } catch (IOException | RuntimeException ignored) {
                // the saved copy is shown
            }
        }).start();
    }

    private void show() {
        content.removeAllViews();
        List<MatrixSync.Contact> list = ChatDb.get(this).contacts();
        Page.status(this, list.isEmpty() ? getString(R.string.chat_no_contacts) : null);
        for (MatrixSync.Contact c : list) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.card_bg);
            int p = Ui.dp(this, 12);
            row.setPadding(p, p, p, p);
            row.addView(ChatActivity.avatar(this, c.name, 44));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Ui.text(this, c.name, 16, Color.WHITE, true));
            texts.addView(Ui.text(this, c.id, 13, getColor(R.color.text_muted), false));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tl.leftMargin = Ui.dp(this, 12);
            row.addView(texts, tl);
            row.setOnClickListener(v -> ChatActivity.startDirect(this, c.id, null));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage(getString(R.string.delete_confirm, c.name))
                        .setPositiveButton(R.string.delete, (d, w) -> save(c, false))
                        .setNegativeButton(R.string.cancel, null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            content.addView(row, lp);
        }
    }

    private void add() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        EditText name = new EditText(this);
        name.setHint(R.string.chat_name_hint);
        name.setSingleLine(true);
        box.addView(name);
        EditText id = new EditText(this);
        id.setHint(getString(R.string.chat_id_hint, MatrixSync.server(ChatDb.me(this))));
        id.setSingleLine(true);
        id.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(id);
        new AlertDialog.Builder(this).setTitle(R.string.chat_add_contact).setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String user = MatrixSync.userId(id.getText().toString(), MatrixSync.server(ChatDb.me(this)));
                    if (user == null) {
                        Toast.makeText(this, R.string.chat_bad_id, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String n = name.getText().toString().trim();
                    save(new MatrixSync.Contact(n.isEmpty() ? MatrixSync.localpart(user) : n, user), true);
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** Adds or removes a contact and saves the list in the account on the server. */
    private void save(MatrixSync.Contact contact, boolean add) {
        MatrixApi api = ChatDb.api(this);
        String me = ChatDb.me(this);
        new Thread(() -> {
            try {
                List<MatrixSync.Contact> list = MatrixSync.contacts(MiniJson.parse(api.accountData(me, MatrixSync.CONTACTS_TYPE)));
                for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(contact.id)) list.remove(i);
                if (add) {
                    api.displayName(contact.id);  // 404: does not exist
                    list.add(contact);
                }
                api.putAccountData(me, MatrixSync.CONTACTS_TYPE, MatrixSync.contactsContent(list));
                ChatDb.get(this).setContacts(list);
                runOnUiThread(() -> {
                    show();
                    Toast.makeText(this, add ? R.string.chat_contact_saved : R.string.chat_contact_deleted, Toast.LENGTH_SHORT).show();
                });
            } catch (MatrixApi.MatrixException e) {
                runOnUiThread(() -> Toast.makeText(this, e.status == 404 ? getString(R.string.chat_not_found, contact.id)
                        : e.getMessage(), Toast.LENGTH_LONG).show());
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.chat_no_server, Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
