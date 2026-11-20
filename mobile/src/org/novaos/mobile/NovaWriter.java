package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Nova writes for you (Nova Chat, Nova Mail): speak instead of typing, Nova reads the text
 * back and asks "Soll ich das senden?" - "Ja" sends it. Nova also reads new messages aloud.
 * For everyone who cannot or does not want to type or read.
 */
final class NovaWriter extends LinearLayout implements Ears.Listener {
    static final int MIC = 31;

    interface Target {
        /** The finished text: send it (chat) or put it into the e-mail. */
        void use(String text);

        /** What Nova reads aloud ("" = nothing new). */
        String readAloud();

        /** Change the text by hand. */
        void edit(String text);
    }

    private static final int IDLE = 0, LISTEN = 1, ASK = 2, CONFIRM = 3;
    private final Activity activity;
    private final Target target;
    private final boolean confirm;
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final Ears ears;
    private final Speaker speaker;
    private final TextView status, result;
    private final View orb;
    private final Button sendButton, editButton;
    private String pending;
    private int mode = IDLE;

    /** confirm: ask before using the text (chat: before sending). */
    NovaWriter(Activity a, Target t, boolean confirm, int useLabel) {
        super(a);
        activity = a;
        target = t;
        this.confirm = confirm;
        ears = new Ears(a, this);
        speaker = new Speaker(a);
        setOrientation(VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2181C3A);
        bg.setCornerRadius(Ui.dp(a, 22));
        bg.setStroke(Ui.dp(a, 1), 0x558E6CFF);
        setBackground(bg);
        int p = Ui.dp(a, 14);
        setPadding(p, Ui.dp(a, 10), p, p);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        orb = new View(a);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF8E6CFF, 0xFF3D7BFF, 0xFF22C1E8});
        g.setShape(GradientDrawable.OVAL);
        orb.setBackground(g);
        orb.setOnClickListener(v -> listenMessage());
        orb.setContentDescription(a.getString(R.string.writer_speak));
        head.addView(orb, new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
        TextView title = Ui.text(a, a.getString(R.string.writer_title), 16, Color.WHITE, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(a, 12);
        head.addView(title, tl);
        ImageButton close = new ImageButton(a);
        close.setImageResource(R.drawable.ic_close);
        close.setBackgroundResource(R.drawable.round_bg);
        close.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int cp = Ui.dp(a, 9);
        close.setPadding(cp, cp, cp, cp);
        close.setContentDescription(a.getString(R.string.close));
        close.setOnClickListener(v -> close());
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36)));
        addView(head);

        status = Ui.text(a, "", 14, a.getColor(R.color.text_muted), false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(a, 8);
        addView(status, sl);
        result = Ui.text(a, "", 18, Color.WHITE, false);
        result.setMaxLines(6);
        result.setVisibility(GONE);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(a, 6);
        addView(result, rl);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button speak = chip(a, R.string.writer_speak, v -> listenMessage());
        sendButton = chip(a, useLabel, v -> use());
        editButton = chip(a, R.string.writer_edit, v -> {
            if (pending != null) target.edit(pending);
            close();
        });
        Button read = chip(a, R.string.writer_read, v -> readAloud());
        buttons.addView(speak);
        buttons.addView(sendButton);
        buttons.addView(editButton);
        buttons.addView(read);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(buttons);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(a, 10);
        addView(hs, bl);
        setVisibility(GONE);
        showPending();
    }

    private Button chip(Activity a, int label, OnClickListener click) {
        Button b = new Button(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundResource(R.drawable.pill_bg);
        b.setMinHeight(Ui.dp(a, 40));
        b.setMinimumHeight(Ui.dp(a, 40));
        b.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), 0);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(a, 40));
        lp.rightMargin = Ui.dp(a, 8);
        b.setLayoutParams(lp);
        return b;
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    /** Opens the panel and listens right away. */
    void open() {
        setVisibility(VISIBLE);
        listenMessage();
    }

    void close() {
        ears.cancel();
        speaker.stop();
        mode = IDLE;
        pulse(0);
        setVisibility(GONE);
    }

    void destroy() {
        ears.destroy();
        speaker.shutdown();
    }

    void permissionResult(boolean granted) {
        if (granted) listenMessage();
        else status.setText(R.string.writer_need_mic);
    }

    private void listenMessage() {
        speaker.stop();
        if (!Ears.available(activity)) {
            status.setText(R.string.writer_no_speech);
            return;
        }
        if (!Ears.allowed(activity)) {
            activity.requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, MIC);
            return;
        }
        mode = LISTEN;
        status.setText(R.string.writer_listen);
        ears.listen();
    }

    private void showPending() {
        result.setVisibility(pending == null ? GONE : VISIBLE);
        result.setText(pending == null ? "" : pending);
        sendButton.setEnabled(pending != null);
        sendButton.setAlpha(pending != null ? 1f : 0.45f);
        editButton.setEnabled(pending != null);
        editButton.setAlpha(pending != null ? 1f : 0.45f);
    }

    private void ask() {
        mode = ASK;
        status.setText(R.string.writer_confirm);
        speaker.say("„" + pending + "“. " + activity.getString(R.string.writer_confirm), () -> {
            if (mode != ASK || !isOpen()) return;
            mode = CONFIRM;
            status.setText(R.string.writer_confirm_hint);
            if (Ears.available(activity) && Ears.allowed(activity)) ears.listen();
        });
    }

    private void use() {
        if (pending == null) return;
        String text = pending;
        pending = null;
        showPending();
        mode = IDLE;
        ears.cancel();
        target.use(text);
        status.setText(confirm ? R.string.writer_sent : R.string.writer_inserted);
        speaker.say(activity.getString(confirm ? R.string.writer_sent : R.string.writer_inserted), null);
    }

    void readAloud() {
        ears.cancel();
        mode = IDLE;
        String text = target.readAloud();
        if (text == null || text.isEmpty()) text = activity.getString(R.string.writer_nothing_new);
        status.setText(R.string.writer_reading);
        speaker.say(text, () -> {
            if (isOpen() && mode == IDLE) status.setText(R.string.writer_after_read);
        });
    }

    /** Reads text aloud (e.g. one message or e-mail). */
    void say(String text) {
        speaker.say(text, null);
    }

    void stopSpeaking() {
        speaker.stop();
    }

    private void pulse(float level) {
        float s = 1f + level * 0.35f;
        orb.setScaleX(s);
        orb.setScaleY(s);
        orb.setAlpha(mode == LISTEN || mode == CONFIRM ? 0.75f + level * 0.25f : 1f);
    }

    // ------------------------------------------------------------ Ears.Listener

    @Override
    public void onReady() {
        pulse(0.1f);
    }

    @Override
    public void onPartial(String text) {
        if (mode == LISTEN) status.setText("„" + text + "“");
    }

    @Override
    public void onResult(String text) {
        pulse(0);
        if (mode == CONFIRM) {
            if (Dictation.isYes(text)) {
                use();
            } else if (Dictation.isNo(text)) {
                pending = null;
                showPending();
                mode = IDLE;
                status.setText(R.string.writer_not_sent);
                speaker.say(activity.getString(R.string.writer_not_sent), null);
            } else if (Dictation.isAgain(text)) {
                listenMessage();
            } else {  // a new text instead
                pending = Dictation.clean(text, german);
                showPending();
                ask();
            }
            return;
        }
        if (mode != LISTEN) return;
        if (Dictation.isRead(text) && text.trim().split("\\s+").length <= 3) {
            readAloud();
            return;
        }
        pending = Dictation.clean(text, german);
        showPending();
        if (confirm) {
            ask();
        } else {
            use();
        }
    }

    @Override
    public void onFailed(boolean nothingHeard) {
        pulse(0);
        if (mode == CONFIRM) {
            mode = IDLE;
            status.setText(R.string.writer_tap_send);
            return;
        }
        mode = IDLE;
        status.setText(nothingHeard ? R.string.writer_nothing_heard : R.string.writer_error);
    }

    @Override
    public void onLevel(float level) {
        pulse(level);
    }
}
