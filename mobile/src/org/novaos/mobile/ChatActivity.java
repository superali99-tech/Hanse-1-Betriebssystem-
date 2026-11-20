package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Nova Chat: chatting (and calling) over Matrix, the open chat network. Login with a
 * Matrix account (matrix.org by default); chats, invitations, contacts saved in the
 * account on the server, notifications for new messages.
 */
public class ChatActivity extends Activity implements ChatSync.Listener {
    static final String SSO_REDIRECT = "org.novaos.mobile://chat-login";
    private static final String REGISTER_URL = "https://app.element.io/#/register";
    private LinearLayout content;
    private View fab;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.app_chat, "");
        fab = Ui.fab(this, R.drawable.ic_plus, R.string.chat_new, v -> newChat());
        handleSso(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSso(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChatSync.get(this).addListener(this);
        show();
        if (ChatDb.loggedIn(this)) {
            ChatSync.get(this).start();
            UpdateNotice.askPermission(this);  // Android 13+: notifications for messages
        }
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

    private void show() {
        content.removeAllViews();
        if (!ChatDb.loggedIn(this)) {
            fab.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.page_subtitle)).setText(R.string.chat_sub);
            showLogin();
            return;
        }
        fab.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.page_subtitle)).setText(ChatDb.me(this));
        LinearLayout pills = new LinearLayout(this);
        pills.addView(pill(R.string.chat_contacts, v -> startActivity(new Intent(this, ChatContactsActivity.class))));
        pills.addView(pill(R.string.chat_logout, v -> confirmLogout()));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.topMargin = Ui.dp(this, 10);
        pl.bottomMargin = Ui.dp(this, 6);
        content.addView(pills, pl);
        List<ChatDb.RoomRow> rooms = ChatDb.get(this).rooms();
        Page.status(this, rooms.isEmpty() ? getString(ChatDb.get(this).get("since") == null ? R.string.loading : R.string.chat_empty) : null);
        for (ChatDb.RoomRow r : rooms) content.addView(roomRow(r));
    }

    private View pill(int text, View.OnClickListener click) {
        TextView t = Ui.text(this, getString(text), 14, Color.WHITE, true);
        t.setBackgroundResource(R.drawable.pill_bg);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    /** Round picture with the first letter, coloured by name. */
    static View avatar(Activity a, String name, int sizeDp) {
        TextView t = Ui.text(a, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), sizeDp * 0.42f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.HSVToColor(new float[] {Math.abs(name.hashCode()) % 360, 0.5f, 0.78f}));
        t.setBackground(bg);
        t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, sizeDp), Ui.dp(a, sizeDp)));
        return t;
    }

    private View roomRow(ChatDb.RoomRow r) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 12);
        row.setPadding(p, p, p, p);
        row.addView(avatar(this, r.name, 48));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, r.name, 16, Color.WHITE, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        String last;
        if (r.invite) {
            last = getString(R.string.chat_invite_row, MatrixSync.localpart(r.inviter));
        } else {
            String p2 = ChatSync.preview(this, r.lastKind, r.lastBody);
            last = r.lastSender.equals(ChatDb.me(this)) && !p2.isEmpty() ? getString(R.string.chat_you, p2) : p2;
        }
        TextView preview = Ui.text(this, last, 14, getColor(r.invite ? R.color.nova_violet : R.color.text_muted), r.invite);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(preview);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 12);
        row.addView(texts, tl);
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.END);
        if (r.lastTs > 0) {
            side.addView(Ui.text(this, DateUtils.getRelativeTimeSpanString(r.lastTs, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(), 11, getColor(R.color.text_muted), false));
        }
        if (r.unread > 0) {
            TextView badge = Ui.text(this, String.valueOf(r.unread), 12, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackgroundResource(R.drawable.accent_round_bg);
            badge.setMinWidth(Ui.dp(this, 22));
            badge.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.topMargin = Ui.dp(this, 4);
            bl.gravity = Gravity.END;
            side.addView(badge, bl);
        }
        row.addView(side);
        row.setOnClickListener(v -> startActivity(new Intent(this, RoomActivity.class).putExtra(RoomActivity.EXTRA_ROOM, r.id)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);
        return row;
    }

    // ------------------------------------------------------------ login

    private EditText field(int hint, int type, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(getColor(R.color.text_muted));
        e.setBackgroundResource(R.drawable.pill_bg);
        e.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        lp.topMargin = Ui.dp(this, 10);
        e.setLayoutParams(lp);
        return e;
    }

    private void showLogin() {
        TextView intro = Ui.text(this, getString(R.string.chat_intro), 15, getColor(R.color.text), false);
        intro.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        content.addView(intro);
        EditText server = field(R.string.chat_server, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                ChatDb.account(this).getString("server", "matrix.org"));
        EditText user = field(R.string.chat_user, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, "");
        EditText password = field(R.string.chat_password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, "");
        content.addView(server);
        content.addView(user);
        content.addView(password);
        TextView status = Ui.text(this, "", 14, getColor(R.color.text_muted), false);
        status.setPadding(0, Ui.dp(this, 10), 0, 0);
        content.addView(status);
        View login = Ui.button(this, R.string.chat_login, v -> {
            String s = server.getText().toString().trim(), u = user.getText().toString().trim(), pw = password.getText().toString();
            if (s.isEmpty() || u.isEmpty() || pw.isEmpty()) {
                status.setText(R.string.chat_fill_in);
                return;
            }
            status.setText(R.string.chat_logging_in);
            v.setEnabled(false);
            new Thread(() -> {
                String error;
                try {
                    String base = MatrixApi.discover(u.startsWith("@") && u.contains(":") ? u : s);
                    Map<?, ?> res = new MatrixApi(base, null).login(u, pw);
                    ChatDb.account(this).edit().putString("server", s).apply();
                    ChatDb.saveLogin(this, base, res);
                    error = null;
                } catch (MatrixApi.MatrixException e) {
                    error = e.status == 403 ? getString(R.string.chat_wrong_password) : e.getMessage();
                } catch (IOException | RuntimeException e) {
                    error = getString(R.string.chat_no_server);
                }
                String err = error;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    v.setEnabled(true);
                    if (err != null) {
                        status.setText(err);
                    } else {
                        ChatSync.get(this).start();
                        show();
                    }
                });
            }).start();
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 14);
        content.addView(login, bl);
        TextView sso = Ui.text(this, getString(R.string.chat_login_browser), 15, getColor(R.color.nova_blue), true);
        sso.setPadding(0, Ui.dp(this, 18), 0, 0);
        sso.setOnClickListener(v -> new Thread(() -> {
            try {
                String base = MatrixApi.discover(server.getText().toString().trim());
                ChatDb.account(this).edit().putString("pending_base", base)
                        .putString("server", server.getText().toString().trim()).apply();
                String url = new MatrixApi(base, null).ssoUrl(SSO_REDIRECT);
                runOnUiThread(() -> open(url));
            } catch (IOException e) {
                runOnUiThread(() -> status.setText(R.string.chat_no_server));
            }
        }).start());
        content.addView(sso);
        TextView register = Ui.text(this, getString(R.string.chat_register), 15, getColor(R.color.nova_blue), true);
        register.setPadding(0, Ui.dp(this, 14), 0, 0);
        register.setOnClickListener(v -> open(REGISTER_URL));
        content.addView(register);
        TextView note = Ui.text(this, getString(R.string.chat_note), 12, getColor(R.color.text_muted), false);
        note.setPadding(0, Ui.dp(this, 18), 0, 0);
        content.addView(note);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    /** Back from the browser login: org.novaos.mobile://chat-login?loginToken=... */
    private void handleSso(Intent i) {
        Uri data = i == null ? null : i.getData();
        if (data == null || !"chat-login".equals(data.getHost())) return;
        String token = data.getQueryParameter("loginToken");
        String base = ChatDb.account(this).getString("pending_base", "");
        if (token == null || base.isEmpty()) return;
        Toast.makeText(this, R.string.chat_logging_in, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Map<?, ?> res = new MatrixApi(base, null).loginToken(token);
                ChatDb.saveLogin(this, base, res);
                runOnUiThread(() -> {
                    ChatSync.get(this).start();
                    show();
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this).setMessage(R.string.chat_logout_confirm)
                .setPositiveButton(R.string.chat_logout, (d, w) -> {
                    MatrixApi api = ChatDb.api(this);
                    ChatSync.get(this).stop();
                    ChatSync.ChatJob.cancel(this);
                    new Thread(api::logout).start();
                    ChatDb.get(this).logout(this);
                    show();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    // ------------------------------------------------------------ new chat

    private void newChat() {
        if (!ChatDb.loggedIn(this)) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        EditText id = new EditText(this);
        id.setHint(getString(R.string.chat_id_hint, MatrixSync.server(ChatDb.me(this))));
        id.setSingleLine(true);
        id.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(id);
        EditText name = new EditText(this);
        name.setHint(R.string.chat_name_hint);
        name.setSingleLine(true);
        box.addView(name);
        CheckBox save = new CheckBox(this);
        save.setText(R.string.chat_save_contact);
        save.setChecked(true);
        box.addView(save);
        new AlertDialog.Builder(this).setTitle(R.string.chat_new).setView(box)
                .setPositiveButton(R.string.chat_start, (d, w) -> {
                    String user = MatrixSync.userId(id.getText().toString(), MatrixSync.server(ChatDb.me(this)));
                    if (user == null) {
                        Toast.makeText(this, R.string.chat_bad_id, Toast.LENGTH_LONG).show();
                        return;
                    }
                    startDirect(this, user, save.isChecked() ? name.getText().toString().trim() : null);
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** Opens the direct chat with userId (creates it if needed); optionally saves the contact. */
    static void startDirect(Activity a, String userId, String saveAsName) {
        ChatDb.RoomRow existing = ChatDb.get(a).directRoom(userId);
        if (existing != null && saveAsName == null) {
            a.startActivity(new Intent(a, RoomActivity.class).putExtra(RoomActivity.EXTRA_ROOM, existing.id));
            return;
        }
        Toast.makeText(a, R.string.chat_creating, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String error = null, roomId = existing != null ? existing.id : null;
            try {
                MatrixApi api = ChatDb.api(a);
                String me = ChatDb.me(a);
                String display = api.displayName(userId);  // 404 if the person does not exist
                if (saveAsName != null) {
                    List<MatrixSync.Contact> contacts = ChatDb.get(a).contacts();
                    boolean known = false;
                    for (MatrixSync.Contact c : contacts) known |= c.id.equals(userId);
                    if (!known) {
                        contacts.add(new MatrixSync.Contact(saveAsName.isEmpty() ? (display.isEmpty() ? MatrixSync.localpart(userId) : display)
                                : saveAsName, userId));
                        api.putAccountData(me, MatrixSync.CONTACTS_TYPE, MatrixSync.contactsContent(contacts));
                        ChatDb.get(a).setContacts(contacts);
                    }
                }
                if (roomId == null) {
                    roomId = api.createDirect(userId);
                    api.markDirect(me, userId, roomId);
                }
            } catch (MatrixApi.MatrixException e) {
                error = e.status == 404 ? a.getString(R.string.chat_not_found, userId) : e.getMessage();
            } catch (IOException | RuntimeException e) {
                error = a.getString(R.string.chat_no_server);
            }
            String err = error, room = roomId;
            a.runOnUiThread(() -> {
                if (err != null) {
                    Toast.makeText(a, err, Toast.LENGTH_LONG).show();
                } else {
                    a.startActivity(new Intent(a, RoomActivity.class).putExtra(RoomActivity.EXTRA_ROOM, room)
                            .putExtra(RoomActivity.EXTRA_NAME, saveAsName != null && !saveAsName.isEmpty() ? saveAsName : MatrixSync.localpart(userId)));
                }
            });
        }).start();
    }
}
