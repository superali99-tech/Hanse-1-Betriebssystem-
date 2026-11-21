package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One chat of Nova Chat: messages as bubbles (text, emojis, photos, voice messages, files),
 * writing with the emoji keyboard, voice messages, sending photos, quick answers and Nova,
 * who writes and reads aloud for you.
 */
public class RoomActivity extends Activity implements ChatSync.Listener, NovaWriter.Target {
    static final String EXTRA_ROOM = "room";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_NOVA = "nova";   // open with Nova listening (from the Nova app)
    private static final int PICK_PHOTO = 41, MIC_VOICE = 42, SAVE_PHOTO = 43;

    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String roomId, me;
    private LinearLayout content, inputRow, pill, recordRow, quickChips;
    private HorizontalScrollView quickRow;
    private ScrollView scroller;
    private EditText input;
    private ImageButton sendButton, novaButton, emojiButton;
    private TextView recordTime;
    private View recordDot;
    private EmojiPanel emojiPanel;
    private NovaWriter nova;
    private final VoiceRecorder recorder = new VoiceRecorder();
    private String lastRead = "";
    private String shown = "";
    private boolean atBottom = true;
    private final List<String> pending = new ArrayList<>();  // "sending …" bubbles
    private List<MatrixSync.Message> messages = new ArrayList<>();
    private Bitmap savePhoto;

    // voice messages: one player for the chat, the bubbles of the current list
    private MediaPlayer player;
    private String playing;
    private final Map<String, View[]> audioViews = new HashMap<>();  // event id -> {play button, wave, time}
    private final Runnable progress = new Runnable() {
        @Override
        public void run() {
            updateAudio();
            if (player != null) ui.postDelayed(this, 120);
        }
    };
    private final Runnable recordTick = new Runnable() {
        @Override
        public void run() {
            if (!recorder.recording()) return;
            recordTime.setText(Waveform.time(recorder.elapsed()));
            float l = recorder.level();
            recordDot.setScaleX(0.8f + l * 0.7f);
            recordDot.setScaleY(0.8f + l * 0.7f);
            if (recorder.elapsed() >= VoiceRecorder.MAX_MS) {
                finishRecording(true);
                return;
            }
            ui.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        roomId = getIntent().getStringExtra(EXTRA_ROOM);
        if (roomId == null || !ChatDb.loggedIn(this)) {
            finish();
            return;
        }
        me = ChatDb.me(this);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        content = Page.setup(this, R.string.app_chat, "");
        // glass design: a colourful background behind the bubbles (purple, pink, orange)
        ViewGroup pageRoot = (ViewGroup) findViewById(R.id.page_title).getRootView().findViewById(android.R.id.content);
        if (pageRoot != null && pageRoot.getChildCount() > 0 && pageRoot.getChildAt(0) instanceof ViewGroup) {
            ViewGroup frame = (ViewGroup) pageRoot.getChildAt(0);
            if (frame.getChildAt(0) instanceof android.widget.ImageView) {
                ((android.widget.ImageView) frame.getChildAt(0)).setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[] {0xFF6A3DE8, 0xFFC13584, 0xFFF0763A}));
            }
            if (frame.getChildCount() > 1) frame.getChildAt(1).setBackgroundColor(0x2E000000);
        }
        ((TextView) findViewById(R.id.page_title)).setText(name != null ? name : "");
        scroller = findViewById(R.id.scroller);
        scroller.setOnScrollChangeListener((v, x, y, ox, oy) -> atBottom = !scroller.canScrollVertically(1));
        scroller.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot && atBottom) scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        });
        ViewGroup parent = (ViewGroup) scroller.getParent();
        int at = parent.indexOfChild(scroller) + 1;
        int side = getResources().getDimensionPixelSize(R.dimen.side);

        // quick answers (one tap sends)
        quickRow = new HorizontalScrollView(this);
        quickRow.setHorizontalScrollBarEnabled(false);
        quickRow.setClipToPadding(false);
        quickRow.setPadding(side, Ui.dp(this, 4), side, 0);
        quickChips = new LinearLayout(this);
        quickRow.addView(quickChips);
        quickRow.setVisibility(View.GONE);
        parent.addView(quickRow, at++, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Nova writes for you
        nova = new NovaWriter(this, this, true, R.string.chat_send);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.setMargins(side, Ui.dp(this, 6), side, 0);
        parent.addView(nova, at++, nl);

        // input row: [emoji | text | photo] [Nova] [microphone / send]
        inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(side - Ui.dp(this, 6), Ui.dp(this, 8), side - Ui.dp(this, 4), Ui.dp(this, 10));
        pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackgroundResource(R.drawable.pill_bg);
        emojiButton = smallButton(R.drawable.ic_emoji, R.string.chat_emoji, v -> toggleEmoji());
        pill.addView(emojiButton, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        input = new EditText(this);
        input.setHint(R.string.chat_write);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(getColor(R.color.text_muted));
        input.setBackground(null);
        input.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10));
        input.setMaxLines(5);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND) {
                sendInput();
                return true;
            }
            return false;
        });
        input.setOnClickListener(v -> hideEmoji());
        input.setOnFocusChangeListener((v, focus) -> {
            if (focus) hideEmoji();
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSendButton();
                showQuick();
            }
        });
        pill.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pill.addView(smallButton(R.drawable.ic_image, R.string.chat_send_photo, v -> pickPhoto()),
                new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        inputRow.addView(pill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // recording a voice message: [cancel] ● 0:07 (instead of the text field)
        recordRow = new LinearLayout(this);
        recordRow.setGravity(Gravity.CENTER_VERTICAL);
        recordRow.setBackgroundResource(R.drawable.pill_bg);
        recordRow.addView(smallButton(R.drawable.ic_delete, R.string.chat_voice_cancel, v -> finishRecording(false)),
                new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        recordDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFFFF4D5E);
        recordDot.setBackground(dot);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12));
        dl.leftMargin = Ui.dp(this, 8);
        recordRow.addView(recordDot, dl);
        recordTime = Ui.text(this, "0:00", 16, Color.WHITE, true);
        LinearLayout.LayoutParams rt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rt.leftMargin = Ui.dp(this, 10);
        recordRow.addView(recordTime, rt);
        TextView recHint = Ui.text(this, getString(R.string.chat_voice_recording), 14, getColor(R.color.text_muted), false);
        recHint.setSingleLine(true);
        LinearLayout.LayoutParams rh = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rh.leftMargin = Ui.dp(this, 10);
        recordRow.addView(recHint, rh);
        recordRow.setVisibility(View.GONE);
        inputRow.addView(recordRow, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));

        novaButton = new ImageButton(this);
        novaButton.setImageResource(R.drawable.ic_spark);
        GradientDrawable ng = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF8E6CFF, 0xFF22C1E8});
        ng.setShape(GradientDrawable.OVAL);
        novaButton.setBackground(ng);
        novaButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int np = Ui.dp(this, 11);
        novaButton.setPadding(np, np, np, np);
        novaButton.setContentDescription(getString(R.string.chat_nova));
        novaButton.setOnClickListener(v -> toggleNova());
        LinearLayout.LayoutParams nb = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        nb.leftMargin = Ui.dp(this, 6);
        inputRow.addView(novaButton, nb);

        sendButton = new ImageButton(this);
        sendButton.setBackgroundResource(R.drawable.accent_round_bg);
        sendButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 13);
        sendButton.setPadding(p, p, p, p);
        sendButton.setOnClickListener(v -> sendPressed());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50));
        sl.leftMargin = Ui.dp(this, 6);
        inputRow.addView(sendButton, sl);
        parent.addView(inputRow, at++, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        emojiPanel = new EmojiPanel(this, new EmojiPanel.Listener() {
            @Override
            public void onEmoji(String emoji) {
                int s = Math.max(0, input.getSelectionStart()), e = Math.max(0, input.getSelectionEnd());
                input.getText().replace(Math.min(s, e), Math.max(s, e), emoji);
            }

            @Override
            public void onBackspace() {
                int s = input.getSelectionStart();
                if (s <= 0) return;
                int before = Character.offsetByCodePoints(input.getText(), s, -1);
                // whole emoji with joiners and variation selectors
                CharSequence t = input.getText();
                while (before > 0 && (t.charAt(before) == '️' || t.charAt(before - 1) == '‍')) {
                    before = Character.offsetByCodePoints(t, before, -1);
                }
                input.getText().delete(before, s);
            }
        });
        emojiPanel.setVisibility(View.GONE);
        parent.addView(emojiPanel, at, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 290)));
        updateSendButton();
        Calls.addButtons(this, roomId);
        if (getIntent().getBooleanExtra(EXTRA_NOVA, false)) ui.post(() -> nova.open());
    }

    private ImageButton smallButton(int icon, int label, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackground(null);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 10);
        b.setPadding(p, p, p, p);
        b.setAlpha(0.85f);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        return b;
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
        if (recorder.recording()) finishRecording(false);
        if (player != null && player.isPlaying()) player.pause();
        nova.stopSpeaking();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (nova != null) nova.destroy();
        recorder.cancel();
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (emojiPanel != null && emojiPanel.getVisibility() == View.VISIBLE) {
            hideEmoji();
        } else if (nova != null && nova.isOpen()) {
            nova.close();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onChatChanged() {
        if (!isDestroyed()) show();
    }

    // ------------------------------------------------------------ messages

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
        inputRow.setVisibility(View.VISIBLE);
        messages = ChatDb.get(this).messages(roomId);
        long h = 17;
        for (MatrixSync.Message m : messages) h = h * 31 + m.eventId.hashCode() * 7L + (m.body == null ? 0 : m.body.hashCode());
        String sig = messages.size() + "|" + h + "|" + pending.size();
        if (sig.equals(shown) && content.getChildCount() > 0) return;
        shown = sig;
        content.removeAllViews();
        audioViews.clear();
        if (room.encrypted) {
            TextView t = Ui.text(this, getString(R.string.chat_encrypted_room), 13, getColor(R.color.text_muted), false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 6));
            content.addView(t);
        }
        if (!room.directUser.isEmpty() && !isContact(room.directUser)) {  // direct chat with someone not saved yet
            TextView save = Ui.text(this, "➕ " + getString(R.string.chat_contact_save), 14, Color.WHITE, true);
            save.setBackgroundResource(R.drawable.pill_bg);
            save.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
            save.setOnClickListener(v -> ChatContactsActivity.rename(this, room.name, room.directUser, () -> {
                shown = "";
                show();
            }));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.gravity = Gravity.CENTER_HORIZONTAL;
            sl.topMargin = Ui.dp(this, 6);
            sl.bottomMargin = Ui.dp(this, 6);
            content.addView(save, sl);
        }
        Page.status(this, messages.isEmpty() && pending.isEmpty() ? getString(R.string.chat_no_messages) : null);
        boolean group = room.directUser.isEmpty();
        String prevSender = "";
        for (MatrixSync.Message m : messages) {
            content.addView(bubble(m, m.sender.equals(me), group && !m.sender.equals(prevSender)));
            prevSender = m.sender;
        }
        for (String p : pending) content.addView(pendingBubble(p));
        updateAudio();
        showQuick();
        atBottom = true;
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
        boolean big = "text".equals(m.kind) && Emoji.onlyEmoji(m.body);
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        if (!big) {
            GradientDrawable bg = new GradientDrawable();
            float r = Ui.dp(this, 18), small = Ui.dp(this, 5);
            bg.setCornerRadii(mine ? new float[] {r, r, r, r, small, small, r, r} : new float[] {r, r, r, r, r, r, small, small});
            if (mine) {
                bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                bg.setColors(new int[] {0xFF2F8BFF, 0xFF0A6CFF});  // blue, like the glass design
            } else {
                bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                bg.setColors(new int[] {0x52FFFFFF, 0x38FFFFFF});  // glass
                bg.setStroke(Math.max(1, Ui.dp(this, 1)), 0x40FFFFFF);
            }
            b.setBackground(bg);
        }
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);
        boolean media = !m.url.isEmpty() && ("image".equals(m.kind) || "audio".equals(m.kind) || "video".equals(m.kind) || "file".equals(m.kind));
        if (media && "image".equals(m.kind)) {
            b.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 6));
            b.addView(photo(m, maxW));
        } else if (media && "audio".equals(m.kind)) {
            b.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 6));
            b.addView(audio(m, mine, Math.min(maxW, Ui.dp(this, 260))),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (media) {
            b.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 6));
            b.addView(file(m, maxW), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            b.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 7));
            String text = "encrypted".equals(m.kind) ? getString(R.string.chat_encrypted_message)
                    : "call".equals(m.kind) ? ("invite".equals(m.body) ? "📞 " + getString(R.string.chat_call_started)
                    : "📞 " + getString(R.string.chat_call_ended))
                    : ChatSync.preview(this, m.kind, m.body);
            TextView t = Ui.text(this, text, big ? 42 : 16, Color.WHITE, false);
            t.setMaxWidth(maxW);
            // natural width (a vertical LinearLayout would otherwise squeeze it to the time's width)
            b.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView time = Ui.text(this, DateFormat.getTimeFormat(this).format(new Date(m.ts)), 11, 0xB3FFFFFF, false);
        time.setGravity(Gravity.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.gravity = Gravity.END;
        if (media && "image".equals(m.kind)) tl.rightMargin = Ui.dp(this, 8);
        b.addView(time, tl);
        b.setOnLongClickListener(v -> {
            menu(m, mine);
            return true;
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 4);
        wrap.addView(b, bl);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private View pendingBubble(String text) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.END);
        TextView t = Ui.text(this, "⏳ " + text, 15, Color.WHITE, false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 18));
        bg.setColor(0x557C5CFF);
        t.setBackground(bg);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 4);
        wrap.addView(t, lp);
        return wrap;
    }

    /** Photo in the bubble: preview from the server, tap = full screen. */
    private View photo(MatrixSync.Message m, int maxW) {
        int w = Math.min(maxW, Ui.dp(this, 250));
        int h = m.w > 0 && m.h > 0 ? (int) Math.min(w * 1.4f, w * (float) m.h / m.w) : w * 3 / 4;
        h = Math.max(h, Ui.dp(this, 80));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0x33000000);
        int radius = Ui.dp(this, 14);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        iv.setClipToOutline(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        iv.setContentDescription(getString(R.string.chat_photo));
        ChatMedia.get(this, m.url, 800, m.mime, f -> {
            if (f == null || isDestroyed()) return;
            Bitmap bmp = ChatMedia.bitmap(f, 900);
            if (bmp != null) iv.setImageBitmap(bmp);
        });
        iv.setOnClickListener(v -> openPhoto(m));
        iv.setOnLongClickListener(v -> {
            menu(m, m.sender.equals(me));
            return true;
        });
        return iv;
    }

    private boolean isContact(String userId) {
        for (MatrixSync.Contact c : ChatDb.get(this).contacts()) if (c.id.equals(userId)) return true;
        return false;
    }

    private void openPhoto(MatrixSync.Message m) {
        Toast loading = Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT);
        loading.show();
        ChatMedia.get(this, m.url, 0, m.mime, f -> {
            loading.cancel();
            if (isDestroyed()) return;
            Bitmap bmp = f == null ? null : ChatMedia.bitmap(f, 2400);
            if (bmp == null) {
                Toast.makeText(this, R.string.chat_media_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setOnClickListener(v -> d.dismiss());
            root.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 12));
            ImageButton close = roundButton(R.drawable.ic_close, R.string.close, v -> d.dismiss());
            bar.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            ImageButton save = roundButton(R.drawable.ic_download_s, R.string.chat_save_photo, v -> savePhoto(bmp));
            bar.addView(save, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
            root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
            d.setContentView(root);
            d.show();
        });
    }

    private ImageButton roundButton(int icon, int label, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.round_bg);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        return b;
    }

    /** Saves a photo of the chat in the gallery (Pictures/Nova Chat). */
    private void savePhoto(Bitmap bmp) {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            savePhoto = bmp;
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, SAVE_PHOTO);
            return;
        }
        String name = "NovaChat_" + System.currentTimeMillis() + ".jpg";
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nova Chat");
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IOException("no gallery");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null || !bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IOException("write");
                }
            } else {
                @SuppressWarnings("deprecation")
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Nova Chat");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no folder");
                File f = new File(dir, name);
                try (OutputStream out = new FileOutputStream(f)) {
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IOException("write");
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
            }
            Toast.makeText(this, R.string.chat_photo_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, R.string.chat_media_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Voice message: play button, wave, length. */
    private View audio(MatrixSync.Message m, boolean mine, int width) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton play = new ImageButton(this);
        play.setImageResource(R.drawable.ic_play_s);
        GradientDrawable pb = new GradientDrawable();
        pb.setShape(GradientDrawable.OVAL);
        pb.setColor(mine ? 0x40FFFFFF : 0xFF7C5CFF);
        play.setBackground(pb);
        play.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 10);
        play.setPadding(p, p, p, p);
        play.setContentDescription(getString(R.string.chat_play));
        play.setOnClickListener(v -> toggleAudio(m));
        row.addView(play, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        WaveView wave = new WaveView(this);
        wave.set(m.waveform, Color.WHITE, 0x66FFFFFF);
        col.addView(wave, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 30)));
        TextView time = Ui.text(this, (m.voice ? "🎤 " : "🎵 ") + Waveform.time(m.duration), 12, 0xCCFFFFFF, false);
        col.addView(time);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(width - Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.leftMargin = Ui.dp(this, 10);
        row.addView(col, cl);
        audioViews.put(m.eventId, new View[] {play, wave, time});
        return row;
    }

    /** File or video: name and size, tap = open with an app of the phone. */
    private View file(MatrixSync.Message m, int maxW) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource("video".equals(m.kind) ? R.drawable.ic_video : R.drawable.ic_file);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, m.body.isEmpty() ? getString(R.string.chat_file_unnamed) : m.body, 15, Color.WHITE, true);
        name.setMaxWidth(maxW - Ui.dp(this, 60));
        name.setMaxLines(2);
        col.addView(name);
        TextView size = Ui.text(this, (m.size > 0 ? Formatter.formatShortFileSize(this, m.size) + " · " : "")
                + getString(R.string.chat_tap_open), 12, 0xCCFFFFFF, false);
        col.addView(size);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.leftMargin = Ui.dp(this, 10);
        row.addView(col, cl);
        row.setOnClickListener(v -> openFile(m));
        return row;
    }

    private void openFile(MatrixSync.Message m) {
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        ChatMedia.get(this, m.url, 0, m.mime, f -> {
            if (f == null || isDestroyed()) {
                if (!isDestroyed()) Toast.makeText(this, R.string.chat_media_failed, Toast.LENGTH_LONG).show();
                return;
            }
            String mime = m.mime.isEmpty() ? "application/octet-stream" : m.mime;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(NovaFileProvider.uri(f), mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_app_for_file, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void menu(MatrixSync.Message m, boolean mine) {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.chat_read_aloud));
        actions.add(() -> {
            if (!nova.isOpen()) nova.setVisibility(View.VISIBLE);
            nova.say(spoken(m));
        });
        if ("text".equals(m.kind) || "notice".equals(m.kind)) {
            items.add(getString(R.string.chat_copy));
            actions.add(() -> {
                ClipboardManager cm = getSystemService(ClipboardManager.class);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Nova Chat", m.body));
                Toast.makeText(this, R.string.chat_copied, Toast.LENGTH_SHORT).show();
            });
        }
        if (mine) {
            items.add(getString(R.string.chat_delete_message));
            actions.add(() -> new AlertDialog.Builder(this).setMessage(R.string.chat_delete_confirm)
                    .setPositiveButton(R.string.delete, (d, w) -> deleteMessage(m))
                    .setNegativeButton(R.string.cancel, null).show());
        }
        new AlertDialog.Builder(this).setItems(items.toArray(new String[0]), (d, w) -> actions.get(w).run()).show();
    }

    private void deleteMessage(MatrixSync.Message m) {
        MatrixApi api = ChatDb.api(this);
        new Thread(() -> {
            try {
                api.redact(roomId, m.eventId);
                ChatDb.get(this).removeMessage(m.eventId);
                runOnUiThread(this::show);
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ------------------------------------------------------------ voice messages: playing

    private void toggleAudio(MatrixSync.Message m) {
        if (m.eventId.equals(playing) && player != null) {
            if (player.isPlaying()) player.pause();
            else player.start();
            ui.removeCallbacks(progress);
            ui.post(progress);
            return;
        }
        releasePlayer();
        playing = m.eventId;
        updateAudio();
        ChatMedia.get(this, m.url, 0, m.mime, f -> {
            if (isDestroyed() || !m.eventId.equals(playing)) return;
            if (f == null) {
                playing = null;
                updateAudio();
                Toast.makeText(this, R.string.chat_media_failed, Toast.LENGTH_LONG).show();
                return;
            }
            try {
                player = new MediaPlayer();
                player.setDataSource(f.getPath());
                player.setOnCompletionListener(mp -> {
                    releasePlayer();
                    updateAudio();
                });
                player.prepare();
                player.start();
                ui.post(progress);
            } catch (IOException | RuntimeException e) {
                releasePlayer();
                updateAudio();
                Toast.makeText(this, R.string.chat_cannot_play, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void releasePlayer() {
        ui.removeCallbacks(progress);
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
                // gone already
            }
        }
        player = null;
        playing = null;
    }

    private void updateAudio() {
        for (Map.Entry<String, View[]> e : audioViews.entrySet()) {
            boolean current = e.getKey().equals(playing);
            boolean on = current && player != null && player.isPlaying();
            ((ImageButton) e.getValue()[0]).setImageResource(on ? R.drawable.ic_pause : R.drawable.ic_play_s);
            float p = 0;
            if (current && player != null) {
                try {
                    p = player.getDuration() > 0 ? (float) player.getCurrentPosition() / player.getDuration() : 0;
                } catch (RuntimeException ignored) {
                    p = 0;
                }
            }
            ((WaveView) e.getValue()[1]).progress(p);
        }
    }

    // ------------------------------------------------------------ voice messages: recording

    private void startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, MIC_VOICE);
            return;
        }
        releasePlayer();
        updateAudio();
        if (!recorder.start(this)) {
            Toast.makeText(this, R.string.chat_voice_failed, Toast.LENGTH_LONG).show();
            return;
        }
        hideEmoji();
        pill.setVisibility(View.GONE);
        novaButton.setVisibility(View.GONE);
        recordRow.setVisibility(View.VISIBLE);
        quickRow.setVisibility(View.GONE);
        updateSendButton();
        ui.post(recordTick);
    }

    private void finishRecording(boolean send) {
        ui.removeCallbacks(recordTick);
        VoiceRecorder.Result r = send ? recorder.stop() : null;
        if (!send) recorder.cancel();
        recordRow.setVisibility(View.GONE);
        pill.setVisibility(View.VISIBLE);
        novaButton.setVisibility(View.VISIBLE);
        updateSendButton();
        showQuick();
        if (send && r == null) {
            Toast.makeText(this, R.string.chat_voice_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (r != null) sendVoice(r);
    }

    private void sendVoice(VoiceRecorder.Result r) {
        String label = getString(R.string.chat_voice_sending);
        pending.add(label);
        show();
        MatrixApi api = ChatDb.api(this);
        new Thread(() -> {
            String error = null;
            try {
                byte[] data = readAll(new FileInputStream(r.file), 30L * 1024 * 1024);
                String url = api.upload(data, "audio/mp4", "voice.m4a");
                Map<String, Object> content = MiniJson.obj("msgtype", "m.audio", "body", getString(R.string.chat_voice_body),
                        "url", url, "info", MiniJson.obj("mimetype", "audio/mp4", "size", data.length, "duration", r.duration),
                        "org.matrix.msc1767.audio", MiniJson.obj("duration", r.duration, "waveform", r.waveform),
                        "org.matrix.msc3245.voice", MiniJson.obj());
                String id = api.sendEvent(roomId, "m.room.message", content);
                // keep the recording as the downloaded copy
                File cached = ChatMedia.file(this, url, 0, "audio/mp4");
                if (!r.file.renameTo(cached) && !r.file.delete()) r.file.deleteOnExit();
                MatrixSync.Message m = localMessage(id, "audio", getString(R.string.chat_voice_body));
                m.url = url;
                m.mime = "audio/mp4";
                m.size = data.length;
                m.duration = r.duration;
                m.voice = true;
                m.waveform = r.waveform;
                ChatDb.get(this).addMessage(m);
            } catch (IOException e) {
                error = e.getMessage();
                if (!r.file.delete()) r.file.deleteOnExit();
            }
            String err = error;
            runOnUiThread(() -> {
                pending.remove(label);
                show();
                if (err != null) failed(err, null);
            });
        }).start();
    }

    // ------------------------------------------------------------ photos

    private void pickPhoto() {
        hideEmoji();
        try {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.nova_no_app, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_PHOTO && result == RESULT_OK && data != null && data.getData() != null) sendPhoto(data.getData());
    }

    private void sendPhoto(Uri uri) {
        String label = getString(R.string.chat_photo_sending);
        pending.add(label);
        show();
        MatrixApi api = ChatDb.api(this);
        new Thread(() -> {
            String error = null;
            try {
                String type = getContentResolver().getType(uri);
                byte[] data;
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException("cannot read");
                data = readAll(in, 40L * 1024 * 1024);
                String mime = type != null && type.startsWith("image/") ? type : "image/jpeg";
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (o.outWidth <= 0) throw new IOException(getString(R.string.chat_not_a_photo));
                int w = o.outWidth, h = o.outHeight;
                // big photos: smaller (max. 2048 px) and upright, like other chat apps
                if (!mime.equals("image/gif") && (data.length > 1500 * 1024 || Math.max(w, h) > 2048 || rotation(data) != 0)) {
                    int sample = 1;
                    while (Math.max(w, h) / (sample * 2) >= 2048) sample *= 2;
                    BitmapFactory.Options d = new BitmapFactory.Options();
                    d.inSampleSize = sample;
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, d);
                    if (bmp == null) throw new IOException(getString(R.string.chat_not_a_photo));
                    float scale = Math.min(1f, 2048f / Math.max(bmp.getWidth(), bmp.getHeight()));
                    Matrix mx = new Matrix();
                    mx.postScale(scale, scale);
                    mx.postRotate(rotation(data));
                    Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mx, true);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    out.compress(Bitmap.CompressFormat.JPEG, 86, bo);
                    data = bo.toByteArray();
                    mime = "image/jpeg";
                    w = out.getWidth();
                    h = out.getHeight();
                }
                String fileName = "Foto" + ChatMedia.extension(mime);
                String url = api.upload(data, mime, fileName);
                Map<String, Object> content = MiniJson.obj("msgtype", "m.image", "body", fileName, "url", url,
                        "info", MiniJson.obj("mimetype", mime, "size", data.length, "w", w, "h", h));
                String id = api.sendEvent(roomId, "m.room.message", content);
                for (int px : new int[] {0, 800}) {  // our own photo needs no download
                    try (OutputStream out = new FileOutputStream(ChatMedia.file(this, url, px, mime))) {
                        out.write(data);
                    }
                }
                MatrixSync.Message m = localMessage(id, "image", fileName);
                m.url = url;
                m.mime = mime;
                m.size = data.length;
                m.w = w;
                m.h = h;
                ChatDb.get(this).addMessage(m);
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String err = error;
            runOnUiThread(() -> {
                pending.remove(label);
                show();
                if (err != null) failed(err, null);
            });
        }).start();
    }

    private static int rotation(byte[] jpeg) {
        try {
            ExifInterface exif = new ExifInterface(new java.io.ByteArrayInputStream(jpeg));
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static byte[] readAll(InputStream in, long max) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = i.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > max) throw new IOException("too large");
            }
            return b.toByteArray();
        }
    }

    // ------------------------------------------------------------ writing

    private void updateSendButton() {
        boolean text = input.getText().toString().trim().length() > 0;
        boolean send = text || recorder.recording();
        sendButton.setImageResource(send ? R.drawable.ic_send : R.drawable.ic_mic);
        sendButton.setContentDescription(getString(send ? R.string.chat_send : R.string.chat_voice));
    }

    private void sendPressed() {
        if (recorder.recording()) {
            finishRecording(true);
        } else if (input.getText().toString().trim().isEmpty()) {
            startRecording();
        } else {
            sendInput();
        }
    }

    private void sendInput() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        sendText(text);
    }

    private void sendText(String text) {
        MatrixApi api = ChatDb.api(this);
        new Thread(() -> {
            try {
                String id = api.sendText(roomId, text);
                ChatDb.get(this).addMessage(localMessage(id, "text", text));
                runOnUiThread(this::show);
            } catch (IOException e) {
                runOnUiThread(() -> failed(e.getMessage(), text));
            }
        }).start();
    }

    private MatrixSync.Message localMessage(String id, String kind, String body) {
        MatrixSync.Message m = new MatrixSync.Message();
        m.eventId = id;
        m.roomId = roomId;
        m.sender = me;
        m.senderName = MatrixSync.localpart(me);
        m.body = body;
        m.kind = kind;
        m.ts = System.currentTimeMillis();
        return m;
    }

    private void failed(String error, String text) {
        if (isDestroyed()) return;
        if (text != null && input.getText().length() == 0) input.setText(text);
        new AlertDialog.Builder(this).setMessage(getString(R.string.chat_send_failed, error))
                .setPositiveButton(R.string.ok, null).show();
    }

    private void toggleEmoji() {
        if (emojiPanel.getVisibility() == View.VISIBLE) {
            hideEmoji();
            input.requestFocus();
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(input, 0);
            return;
        }
        nova.close();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        ui.postDelayed(() -> {
            emojiPanel.setVisibility(View.VISIBLE);
            emojiButton.setAlpha(1f);
            emojiButton.setActivated(true);
        }, 120);
    }

    private void hideEmoji() {
        if (emojiPanel == null) return;
        emojiPanel.setVisibility(View.GONE);
        emojiButton.setAlpha(0.85f);
    }

    private void toggleNova() {
        if (nova.isOpen()) {
            nova.close();
            return;
        }
        hideEmoji();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        nova.open();
    }

    /** Quick answers to the last message of the other person. */
    private void showQuick() {
        if (quickChips == null) return;
        quickChips.removeAllViews();
        MatrixSync.Message last = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (!"call".equals(messages.get(i).kind)) {
                last = messages.get(i);
                break;
            }
        }
        if (last == null || last.sender.equals(me) || input.getText().length() > 0 || recorder.recording()
                || "encrypted".equals(last.kind)) {
            quickRow.setVisibility(View.GONE);
            return;
        }
        for (String answer : QuickReplies.to(last.body, last.kind, german)) {
            TextView chip = Ui.text(this, answer, 14, Color.WHITE, false);
            chip.setBackgroundResource(R.drawable.pill_bg);
            chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            chip.setOnClickListener(v -> {
                quickRow.setVisibility(View.GONE);
                sendText(answer);
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.rightMargin = Ui.dp(this, 8);
            quickChips.addView(chip, cl);
        }
        quickRow.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------ Nova (NovaWriter.Target)

    @Override
    public void use(String text) {
        sendText(text);
    }

    @Override
    public void edit(String text) {
        input.setText(text);
        input.setSelection(input.getText().length());
        input.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(input, 0);
    }

    /** The messages of the others since my last message (at most 5), else the last one. */
    @Override
    public String readAloud() {
        List<MatrixSync.Message> list = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && list.size() < 5; i--) {
            MatrixSync.Message m = messages.get(i);
            if (m.sender.equals(me)) {
                if (!list.isEmpty()) break;
                continue;
            }
            if (!"call".equals(m.kind)) list.add(0, m);
        }
        if (list.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (MatrixSync.Message m : list) b.append(spoken(m)).append(' ');
        return b.toString().trim();
    }

    private String spoken(MatrixSync.Message m) {
        String who = m.sender.equals(me) ? getString(R.string.chat_spoken_you) : m.senderName;
        switch (m.kind) {
            case "image": return getString(R.string.chat_spoken_photo, who);
            case "audio": return getString(R.string.chat_spoken_voice, who);
            case "video": case "file": return getString(R.string.chat_spoken_file, who, m.body);
            case "encrypted": return getString(R.string.chat_spoken_encrypted, who);
            default: return getString(R.string.chat_spoken_text, who, m.body);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == NovaWriter.MIC) nova.permissionResult(ok);
        else if (code == MIC_VOICE && ok) startRecording();
        else if (code == MIC_VOICE) Toast.makeText(this, R.string.chat_voice_need_mic, Toast.LENGTH_LONG).show();
        else if (code == SAVE_PHOTO && ok && savePhoto != null) savePhoto(savePhoto);
    }

    // ------------------------------------------------------------ invitations

    private void showInvite(ChatDb.RoomRow room) {
        inputRow.setVisibility(View.GONE);
        quickRow.setVisibility(View.GONE);
        content.removeAllViews();
        shown = "";
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
}
