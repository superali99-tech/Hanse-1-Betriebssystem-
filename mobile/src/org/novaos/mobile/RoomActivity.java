package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/** One chat of Nova Chat: messages as bubbles, writing, accepting invitations. */
public class RoomActivity extends Activity implements ChatSync.Listener {
    static final String EXTRA_ROOM = "room";
    static final String EXTRA_NAME = "name";
    private String roomId;
    private LinearLayout content, bar;
    private ScrollView scroller;
    private EditText input;
    private String lastRead = "";
    private int shown = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        roomId = getIntent().getStringExtra(EXTRA_ROOM);
        if (roomId == null || !ChatDb.loggedIn(this)) {
            finish();
            return;
        }
        String name = getIntent().getStringExtra(EXTRA_NAME);
        content = Page.setup(this, R.string.app_chat, "");
        ((TextView) findViewById(R.id.page_title)).setText(name != null ? name : "");
        scroller = findViewById(R.id.scroller);
        ViewGroup parent = (ViewGroup) scroller.getParent();
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        input = new EditText(this);
        input.setHint(R.string.chat_write);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(getColor(R.color.text_muted));
        input.setBackgroundResource(R.drawable.pill_bg);
        input.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        input.setMaxLines(5);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton send = new ImageButton(this);
        send.setImageResource(R.drawable.ic_send);
        send.setBackgroundResource(R.drawable.accent_round_bg);
        send.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 13);
        send.setPadding(p, p, p, p);
        send.setContentDescription(getString(R.string.chat_send));
        send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50));
        sl.leftMargin = Ui.dp(this, 8);
        bar.addView(send, sl);
        parent.addView(bar, parent.indexOfChild(scroller) + 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        Calls.addButtons(this, roomId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChatSync.openRoom = roomId;
        ChatSync.cancel(this, roomId);
        ChatSync.get(this).addListener(this);
        ChatSync.get(this).start();
        show();
    }

    @Override
    protected void onPause() {
        ChatSync.openRoom = null;
        ChatSync.get(this).removeListener(this);
        super.onPause();
    }

    @Override
    public void onChatChanged() {
        if (!isDestroyed()) show();
    }

    private void show() {
        ChatDb.RoomRow room = ChatDb.get(this).room(roomId);
        if (room == null) {
            Page.status(this, getString(R.string.loading));
            return;
        }
        ((TextView) findViewById(R.id.page_title)).setText(room.name);
        ((TextView) findViewById(R.id.page_subtitle)).setText(room.directUser.isEmpty() ? "" : room.directUser);
        if (room.invite) {
            showInvite(room);
            return;
        }
        bar.setVisibility(View.VISIBLE);
        List<MatrixSync.Message> messages = ChatDb.get(this).messages(roomId);
        if (messages.size() == shown && content.getChildCount() > 0) return;
        shown = messages.size();
        content.removeAllViews();
        if (room.encrypted) {
            TextView t = Ui.text(this, getString(R.string.chat_encrypted_room), 13, getColor(R.color.text_muted), false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 6));
            content.addView(t);
        }
        Page.status(this, messages.isEmpty() ? getString(R.string.chat_no_messages) : null);
        String me = ChatDb.me(this);
        boolean group = room.directUser.isEmpty();
        String prevSender = "";
        for (MatrixSync.Message m : messages) {
            content.addView(bubble(m, m.sender.equals(me), group && !m.sender.equals(prevSender)));
            prevSender = m.sender;
        }
        scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        if (!messages.isEmpty()) {
            MatrixSync.Message last = messages.get(messages.size() - 1);
            if (!last.eventId.equals(lastRead) && room.unread > 0 || !last.eventId.equals(lastRead) && !last.sender.equals(me)) {
                lastRead = last.eventId;
                ChatDb.get(this).setUnread(roomId, 0);
                MatrixApi api = ChatDb.api(this);
                new Thread(() -> api.read(roomId, last.eventId)).start();
            }
        }
    }

    private View bubble(MatrixSync.Message m, boolean mine, boolean showName) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(mine ? Gravity.END : Gravity.START);
        if (showName && !mine) {
            TextView n = Ui.text(this, m.senderName, 12, getColor(R.color.nova_violet), true);
            n.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 2));
            wrap.addView(n);
        }
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        float r = Ui.dp(this, 18), small = Ui.dp(this, 5);
        bg.setCornerRadii(mine ? new float[] {r, r, r, r, small, small, r, r} : new float[] {r, r, r, r, r, r, small, small});
        if (mine) {
            bg.setOrientation(GradientDrawable.Orientation.TL_BR);
            bg.setColors(new int[] {0xFF7C5CFF, 0xFF3D7BFF});
        } else {
            bg.setColor(0x33FFFFFF);
        }
        b.setBackground(bg);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 7));
        String text = "encrypted".equals(m.kind) ? getString(R.string.chat_encrypted_message)
                : "call".equals(m.kind) ? ("invite".equals(m.body) ? "📞 " + getString(R.string.chat_call_started)
                : "📞 " + getString(R.string.chat_call_ended))
                : ChatSync.preview(this, m.kind, m.body);
        TextView t = Ui.text(this, text, 16, Color.WHITE, false);
        t.setTextIsSelectable(true);
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
        b.addView(t);
        TextView time = Ui.text(this, DateFormat.getTimeFormat(this).format(new Date(m.ts)), 11, 0xB3FFFFFF, false);
        time.setGravity(Gravity.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.gravity = Gravity.END;
        b.addView(time, tl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 4);
        wrap.addView(b, bl);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private void showInvite(ChatDb.RoomRow room) {
        bar.setVisibility(View.GONE);
        content.removeAllViews();
        shown = -1;
        Page.status(this, null);
        TextView t = Ui.text(this, getString(R.string.chat_invite_text, room.name), 17, Color.WHITE, false);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 14));
        content.addView(t);
        LinearLayout buttons = new LinearLayout(this);
        View accept = Ui.button(this, R.string.chat_accept, v -> act(true));
        buttons.addView(accept);
        View decline = Ui.button(this, R.string.chat_decline, v -> act(false));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.leftMargin = Ui.dp(this, 10);
        buttons.addView(decline, dl);
        content.addView(buttons);
    }

    private void act(boolean accept) {
        MatrixApi api = ChatDb.api(this);
        new Thread(() -> {
            try {
                if (accept) api.join(roomId);
                else api.leave(roomId);
                if (!accept) ChatDb.get(this).removeRoom(roomId);
                runOnUiThread(() -> {
                    if (!accept) finish();
                    else Toast.makeText(this, R.string.chat_joined, Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void send() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        MatrixApi api = ChatDb.api(this);
        String me = ChatDb.me(this);
        new Thread(() -> {
            try {
                String id = api.sendText(roomId, text);
                MatrixSync.Message m = new MatrixSync.Message();
                m.eventId = id;
                m.roomId = roomId;
                m.sender = me;
                m.senderName = MatrixSync.localpart(me);
                m.body = text;
                m.kind = "text";
                m.ts = System.currentTimeMillis();
                ChatDb.get(this).addMessage(m);
                runOnUiThread(this::show);
            } catch (IOException e) {
                runOnUiThread(() -> {
                    input.setText(text);
                    new AlertDialog.Builder(this).setMessage(getString(R.string.chat_send_failed, e.getMessage()))
                            .setPositiveButton(R.string.ok, null).show();
                });
            }
        }).start();
    }
}
