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
    private String query = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!ChatDb.loggedIn(this)) {
            finish();
            return;
        }
        content = Page.setup(this, R.string.chat_contacts, getString(R.string.chat_contacts_sub));
        Ui.fab(this, R.drawable.ic_person_add, R.string.chat_add_contact, v -> add());
        Page.search(this, R.string.contacts_search, q -> {
            query = q;
            show();
        });
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
        int shown = 0;
        for (MatrixSync.Contact c : list) {
            if (!Search.matches(query, c.name, c.id)) continue;
            shown++;
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
                String[] items = {getString(R.string.chat_contact_rename), getString(R.string.delete)};
                new AlertDialog.Builder(this).setTitle(c.name).setItems(items, (d, w) -> {
                    if (w == 0) rename(this, c.name, c.id, this::show);
                    else new AlertDialog.Builder(this).setMessage(getString(R.string.delete_confirm, c.name))
                            .setPositiveButton(R.string.delete, (dd, ww) -> save(this, c, false, this::show))
                            .setNegativeButton(R.string.cancel, null).show();
                }).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            content.addView(row, lp);
        }
        if (shown == 0 && !list.isEmpty()) {
            TextView none = Ui.text(this, getString(R.string.search_none), 15, getColor(R.color.text_muted), false);
            none.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, 0);
            content.addView(none);
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
                    save(this, new MatrixSync.Contact(n.isEmpty() ? MatrixSync.localpart(user) : n, user), true, this::show);
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** Asks for the name and saves the contact (also from a chat: "save as contact"). */
    static void rename(Activity a, String name, String userId, Runnable done) {
        EditText field = new EditText(a);
        field.setHint(R.string.chat_name_hint);
        field.setSingleLine(true);
        field.setText(name);
        field.setSelection(field.getText().length());
        LinearLayout box = new LinearLayout(a);
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), 0);
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(a).setTitle(R.string.chat_contact_save).setMessage(userId).setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String n = field.getText().toString().trim();
                    save(a, new MatrixSync.Contact(n.isEmpty() ? MatrixSync.localpart(userId) : n, userId), true, done);
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** Adds (or renames) or removes a contact and saves the list in the account on the server. */
    static void save(Activity a, MatrixSync.Contact contact, boolean add, Runnable done) {
        MatrixApi api = ChatDb.api(a);
        String me = ChatDb.me(a);
        new Thread(() -> {
            try {
                List<MatrixSync.Contact> list = MatrixSync.contacts(MiniJson.parse(api.accountData(me, MatrixSync.CONTACTS_TYPE)));
                for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(contact.id)) list.remove(i);
                if (add) {
                    api.displayName(contact.id);  // 404: does not exist
                    list.add(contact);
                }
                api.putAccountData(me, MatrixSync.CONTACTS_TYPE, MatrixSync.contactsContent(list));
                ChatDb.get(a).setContacts(list);
                a.runOnUiThread(() -> {
                    if (done != null) done.run();
                    Toast.makeText(a, add ? R.string.chat_contact_saved : R.string.chat_contact_deleted, Toast.LENGTH_SHORT).show();
                });
            } catch (MatrixApi.MatrixException e) {
                a.runOnUiThread(() -> Toast.makeText(a, e.status == 404 ? a.getString(R.string.chat_not_found, contact.id)
                        : e.getMessage(), Toast.LENGTH_LONG).show());
            } catch (IOException | RuntimeException e) {
                a.runOnUiThread(() -> Toast.makeText(a, R.string.chat_no_server, Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
