package org.novaos.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

/**
 * The screen a child sees while the phone is locked by Nova Family: why, until when, the
 * parents' message - and what always works: calling (parents, emergency), the torch, asking
 * the parents, and the parents' PIN. Used as overlay over all apps and as fallback activity.
 */
final class LockScreen {
    interface Actions {
        void call(String number);

        void torch();

        void ask(String kind, int minutes);

        void pin(String pin);

        void home();
    }

    private LockScreen() {}

    static View build(Context c, FamilyPolicy.State state, FamilyPolicy policy, Actions actions) {
        FrameLayout root = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF1A1440, 0xFF0B0E1F, 0xFF06070F});
        root.setBackground(bg);
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Ui.dp(c, 28);
        col.setPadding(side, Ui.dp(c, 54), side, Ui.dp(c, 40));
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextClock clock = new TextClock(c);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(64);
        clock.setGravity(Gravity.CENTER);
        col.addView(clock);
        TextClock date = new TextClock(c);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(16);
        col.addView(date);

        ImageView icon = new ImageView(c);
        icon.setImageResource("bedtime".equals(state.reason) ? R.drawable.ic_moon : "limit".equals(state.reason) ? R.drawable.ic_hourglass : R.drawable.ic_lock);
        GradientDrawable ib = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF8E6CFF, 0xFF3D7BFF});
        ib.setShape(GradientDrawable.OVAL);
        icon.setBackground(ib);
        int p = Ui.dp(c, 22);
        icon.setPadding(p, p, p, p);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(c, 96), Ui.dp(c, 96));
        il.topMargin = Ui.dp(c, 30);
        col.addView(icon, il);

        boolean app = "app".equals(state.reason);
        int title = "bedtime".equals(state.reason) ? R.string.lock_bedtime : "limit".equals(state.reason) ? R.string.lock_limit
                : app ? R.string.lock_app : R.string.lock_locked;
        TextView t = Ui.text(c, c.getString(title), 26, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 6));
        col.addView(t);
        String until;
        if (app) {
            until = c.getString(R.string.lock_app_text);
        } else if (state.until > 0) {
            String time = DateFormat.getTimeFormat(c).format(new Date(state.until));
            until = DateUtils.isToday(state.until) ? c.getString(R.string.lock_until_today, time)
                    : c.getString(R.string.lock_until_day, DateUtils.formatDateTime(c, state.until, DateUtils.FORMAT_SHOW_WEEKDAY), time);
        } else {
            until = c.getString(R.string.lock_until_parents);
        }
        TextView u = Ui.text(c, until, 17, 0xFFE6DEFF, false);
        u.setGravity(Gravity.CENTER);
        col.addView(u);
        if (!policy.message.isEmpty()) {
            TextView m = Ui.text(c, "💬 " + policy.message, 16, Color.WHITE, false);
            GradientDrawable mb = new GradientDrawable();
            mb.setCornerRadius(Ui.dp(c, 16));
            mb.setColor(0x26FFFFFF);
            m.setBackground(mb);
            m.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ml.topMargin = Ui.dp(c, 16);
            col.addView(m, ml);
        }

        // what always works
        LinearLayout calls = new LinearLayout(c);
        calls.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(c, 26);
        col.addView(calls, cl);
        if (app) calls.addView(button(c, "🏠 " + c.getString(R.string.lock_home), true, v -> actions.home()));
        for (String[] x : policy.callable) {
            calls.addView(button(c, "📞 " + c.getString(R.string.lock_call, x[0].isEmpty() ? x[1] : x[0]), true, v -> actions.call(x[1])));
        }
        calls.addView(button(c, "🆘 " + c.getString(R.string.lock_emergency), false, v -> actions.call("112")));
        LinearLayout row = new LinearLayout(c);
        row.addView(button(c, "🔦 " + c.getString(R.string.lock_torch), false, v -> actions.torch()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View askB = button(c, "🙋 " + c.getString(R.string.lock_ask), false, null);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        al.leftMargin = Ui.dp(c, 10);
        row.addView(askB, al);
        calls.addView(row);
        // asking the parents: unlock or more time
        LinearLayout askRow = new LinearLayout(c);
        askRow.setVisibility(View.GONE);
        askRow.addView(button(c, c.getString(R.string.lock_ask_unlock), false, v -> {
            actions.ask("unlock", 0);
            askRow.setVisibility(View.GONE);
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        a2.leftMargin = Ui.dp(c, 10);
        askRow.addView(button(c, c.getString(R.string.lock_ask_time), false, v -> {
            actions.ask("time", 30);
            askRow.setVisibility(View.GONE);
        }), a2);
        calls.addView(askRow);
        askB.setOnClickListener(v -> askRow.setVisibility(askRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        // parents' PIN (the parents unlock right here)
        LinearLayout pinRow = new LinearLayout(c);
        pinRow.setGravity(Gravity.CENTER_VERTICAL);
        pinRow.setVisibility(View.GONE);
        EditText pin = new EditText(c);
        pin.setHint(R.string.lock_pin_hint);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setTextColor(Color.WHITE);
        pin.setHintTextColor(0x99FFFFFF);
        pin.setBackgroundResource(R.drawable.pill_bg);
        pin.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        pinRow.addView(pin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View ok = button(c, c.getString(R.string.ok), true, v -> {
            actions.pin(pin.getText().toString());
            pin.setText("");
        });
        LinearLayout.LayoutParams okl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okl.leftMargin = Ui.dp(c, 10);
        pinRow.addView(ok, okl);
        TextView parents = Ui.text(c, "🔑 " + c.getString(R.string.lock_parents), 14, 0xFF9DB8FF, true);
        parents.setGravity(Gravity.CENTER);
        parents.setPadding(0, Ui.dp(c, 22), 0, Ui.dp(c, 8));
        parents.setOnClickListener(v -> pinRow.setVisibility(pinRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        calls.addView(parents);
        calls.addView(pinRow);
        TextView brand = Ui.text(c, c.getString(R.string.lock_brand), 12, 0x80FFFFFF, false);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, Ui.dp(c, 26), 0, 0);
        col.addView(brand);
        return root;
    }

    private static View button(Context c, String text, boolean accent, View.OnClickListener click) {
        TextView b = Ui.text(c, text, 16, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(accent ? R.drawable.accent_card_bg : R.drawable.card_bg);
        b.setPadding(Ui.dp(c, 16), Ui.dp(c, 15), Ui.dp(c, 16), Ui.dp(c, 15));
        if (click != null) b.setOnClickListener(click);
        b.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }
}
