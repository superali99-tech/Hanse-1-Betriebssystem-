package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Nova Family: parents see their children's phones and set the rules (lock now, lock until,
 * bedtime, screen time, apps, a message); on the child's phone: joining with the family code,
 * switching on the protection, asking for more time.
 */
public class FamilyActivity extends Activity implements Family.Listener {
    private static final int PERMS = 71;
    private LinearLayout content;
    private String mode = "";  // "", parent-setup, child-setup

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.app_family, "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Family.LISTENERS.add(this);
        if (Family.parent(this)) {
            Family.listen(this);
            Family.sendAsync(this, MiniJson.obj("type", "ping", "to", "*"));
        }
        if (Family.child(this)) FamilyService.start(this);
        show();
    }

    @Override
    protected void onPause() {
        Family.LISTENERS.remove(this);
        super.onPause();
    }

    @Override
    public void onFamilyChanged() {
        if (!isDestroyed() && mode.isEmpty()) show();
    }

    @Override
    public void onBackPressed() {
        if (!mode.isEmpty()) {
            mode = "";
            show();
        } else {
            super.onBackPressed();
        }
    }

    private void show() {
        content.removeAllViews();
        Page.status(this, null);
        TextView sub = findViewById(R.id.page_subtitle);
        if (mode.equals("parent-setup")) {
            sub.setText(R.string.family_parent);
            parentSetup();
        } else if (mode.equals("child-setup")) {
            sub.setText(R.string.family_child);
            childSetup();
        } else if (Family.parent(this)) {
            sub.setText(R.string.family_parent_sub);
            parentHome();
        } else if (Family.child(this)) {
            sub.setText(R.string.family_child_sub);
            childHome();
        } else {
            sub.setText(R.string.family_sub);
            choose();
        }
    }

    // ------------------------------------------------------------ building blocks

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = Ui.text(this, s, size, color, bold);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private void para(String s) {
        TextView t = text(s, 15, getColor(R.color.text_muted), false);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 8));
        content.addView(t);
    }

    private View card(int icon, String title, String sub, boolean accent, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(accent ? R.drawable.accent_card_bg : R.drawable.card_bg);
        int p = Ui.dp(this, 16);
        r.setPadding(p, p, p, p);
        if (icon != 0) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageResource(icon);
            r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 16.5f, Color.WHITE, true));
        if (sub != null && !sub.isEmpty()) col.addView(text(sub, 13, accent ? 0xDDFFFFFF : getColor(R.color.text_muted), false));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cl.leftMargin = icon != 0 ? Ui.dp(this, 16) : 0;
        r.addView(col, cl);
        if (click != null) {
            r.setClickable(true);
            r.setOnClickListener(click);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        content.addView(r, lp);
        return r;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(getColor(R.color.text_muted));
        e.setBackgroundResource(R.drawable.pill_bg);
        e.setPadding(Ui.dp(this, 18), Ui.dp(this, 13), Ui.dp(this, 18), Ui.dp(this, 13));
        e.setTypeface(Typeface.DEFAULT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        content.addView(e, lp);
        return e;
    }

    private View button(int label, View.OnClickListener click) {
        View b = Ui.button(this, label, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 18);
        content.addView(b, lp);
        return b;
    }

    private TextView chip(String text, View.OnClickListener click) {
        TextView t = text(text, 14, Color.WHITE, true);
        t.setBackgroundResource(R.drawable.pill_bg);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        lp.topMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------ who uses this phone?

    private void choose() {
        para(getString(R.string.family_intro));
        card(R.drawable.ic_family, getString(R.string.family_parent), getString(R.string.family_parent_choose), true, v -> {
            mode = "parent-setup";
            show();
        });
        card(R.drawable.ic_shield, getString(R.string.family_child), getString(R.string.family_child_choose), false, v -> {
            mode = "child-setup";
            show();
        });
        TextView how = text(getString(R.string.family_how), 13, getColor(R.color.text_muted), false);
        how.setPadding(Ui.dp(this, 2), Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 20));
        content.addView(how);
    }

    // ------------------------------------------------------------ parents

    private void parentSetup() {
        para(getString(R.string.family_parent_setup_text));
        EditText name = field(getString(R.string.family_your_name), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(Profile.name(this));
        EditText phone = field(getString(R.string.family_your_phone), InputType.TYPE_CLASS_PHONE);
        EditText pin = field(getString(R.string.family_new_pin), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText pin2 = field(getString(R.string.family_new_pin_again), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        TextView status = text("", 14, 0xFFFFB4B4, false);
        status.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, 0);
        content.addView(status);
        button(R.string.family_create, v -> {
            String n = name.getText().toString().trim(), pn = pin.getText().toString();
            if (n.isEmpty()) {
                status.setText(R.string.family_need_name);
                return;
            }
            if (pn.length() < 4 || !pn.equals(pin2.getText().toString())) {
                status.setText(R.string.family_pin_rules);
                return;
            }
            Family.becomeParent(this, FamilyCrypto.newCode(), n, phone.getText().toString().trim(), pn);
            mode = "";
            show();
        });
        // second parent: same code
        TextView or = text(getString(R.string.family_have_code), 15, 0xFF9DB8FF, true);
        or.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, 0);
        content.addView(or);
        EditText code = field(getString(R.string.family_code_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        View join = Ui.button(this, R.string.family_join_parent, v -> {
            String c = FamilyCrypto.normalize(code.getText().toString());
            if (c == null) {
                status.setText(R.string.family_bad_code);
                return;
            }
            Family.becomeParent(this, c, name.getText().toString().trim(), phone.getText().toString().trim(), null);
            mode = "";
            show();
        });
        LinearLayout.LayoutParams jl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        jl.topMargin = Ui.dp(this, 10);
        jl.bottomMargin = Ui.dp(this, 24);
        content.addView(join, jl);
    }

    private void parentHome() {
        // the family code (for the children's phones)
        String code = Family.code(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.accent_card_bg);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 16));
        box.addView(text(getString(R.string.family_code_title), 13, 0xDDFFFFFF, true));
        TextView c = text(code, 26, Color.WHITE, true);
        c.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        c.setTextIsSelectable(true);
        box.addView(c);
        box.addView(text(getString(R.string.family_code_how), 13, 0xDDFFFFFF, false));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 6);
        content.addView(box, bl);

        List<Family.Child> kids = Family.children(this);
        if (kids.isEmpty()) {
            TextView t = text(getString(R.string.family_no_children), 15, getColor(R.color.text_muted), false);
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), Ui.dp(this, 4), 0);
            content.addView(t);
        }
        for (Family.Child k : kids) childCard(k);
        LinearLayout tools = new LinearLayout(this);
        tools.addView(chip("⟳ " + getString(R.string.family_refresh), v -> {
            Family.sendAsync(this, MiniJson.obj("type", "ping", "to", "*"));
            new Thread(() -> {
                try {
                    Family.poll(getApplicationContext());
                } catch (Exception ignored) {
                    // the listener tries again
                }
            }).start();
            Toast.makeText(this, R.string.family_refreshing, Toast.LENGTH_SHORT).show();
        }));
        tools.addView(chip("🚪 " + getString(R.string.family_leave), v -> new AlertDialog.Builder(this)
                .setMessage(R.string.family_leave_parent_confirm).setPositiveButton(R.string.family_leave, (d, w) -> {
                    Family.leave(this);
                    show();
                }).setNegativeButton(R.string.cancel, null).show()));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(this, 16);
        tl.bottomMargin = Ui.dp(this, 24);
        content.addView(tools, tl);
    }

    private String stateText(Family.Child k) {
        if (!k.locked) return "✅ " + getString(R.string.family_state_free);
        String until = k.until > 0 ? " " + getString(R.string.family_until, time(k.until)) : "";
        switch (k.reason) {
            case "bedtime": return "🌙 " + getString(R.string.lock_bedtime) + until;
            case "limit": return "⏳ " + getString(R.string.lock_limit) + until;
            default: return "🔒 " + getString(R.string.family_state_locked) + until;
        }
    }

    private String time(long ms) {
        String t = DateFormat.getTimeFormat(this).format(new Date(ms));
        return DateUtils.isToday(ms) ? t : DateUtils.formatDateTime(this, ms, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY) + " " + t;
    }

    private void childCard(Family.Child k) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ChatActivity.avatar(this, k.name.isEmpty() ? "?" : k.name, 48));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(k.name.isEmpty() ? k.model : k.name, 18, Color.WHITE, true));
        String seen = k.lastSeen > 0 ? DateUtils.getRelativeTimeSpanString(k.lastSeen, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() : "–";
        col.addView(text(k.model + " · " + getString(R.string.family_seen, seen)
                + (k.battery >= 0 ? " · 🔋 " + k.battery + " %" : ""), 12.5f, getColor(R.color.text_muted), false));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cl.leftMargin = Ui.dp(this, 12);
        head.addView(col, cl);
        card.addView(head);
        TextView st = text(stateText(k), 17, Color.WHITE, true);
        st.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
        card.addView(st);
        FamilyPolicy p = k.policy;
        StringBuilder rules = new StringBuilder(getString(R.string.family_screen_today, minutes(k.screen)));
        if (p.limitMinutes > 0) rules.append(" / ").append(minutes(p.allowedMinutes(FamilyPolicy.day(System.currentTimeMillis(), TimeZone.getDefault()))));
        if (p.bedtime) rules.append("\n").append(getString(R.string.family_bedtime_line, FamilyPolicy.time(p.bedFrom), FamilyPolicy.time(p.bedTo), days(p.days)));
        if (!p.blocked.isEmpty()) rules.append("\n").append(getResources().getQuantityString(R.plurals.family_blocked_apps, p.blocked.size(), p.blocked.size()));
        card.addView(text(rules.toString(), 13.5f, getColor(R.color.text_muted), false));
        List<String> missing = new ArrayList<>();
        Map<String, Object> pr = k.protection;
        if (!pr.isEmpty()) {
            if (!Boolean.TRUE.equals(pr.get("overlay"))) missing.add(getString(R.string.family_prot_overlay));
            if (!Boolean.TRUE.equals(pr.get("admin"))) missing.add(getString(R.string.family_prot_admin));
            if (!Boolean.TRUE.equals(pr.get("home"))) missing.add(getString(R.string.family_prot_home));
        }
        if (!missing.isEmpty()) {
            TextView w = text("⚠ " + getString(R.string.family_prot_missing, android.text.TextUtils.join(", ", missing)), 13, 0xFFFFC27A, false);
            w.setPadding(0, Ui.dp(this, 6), 0, 0);
            card.addView(w);
        }
        // actions
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.addView(chip("🔒 " + getString(R.string.family_lock_now), v -> {
            FamilyPolicy np = k.policy;
            np.lockUntil = -1;
            np.freeUntil = 0;
            Family.setPolicy(this, k.id, np);
            Toast.makeText(this, getString(R.string.family_sent_lock, k.name), Toast.LENGTH_SHORT).show();
        }));
        row.addView(chip("🔓 " + getString(R.string.family_unlock), v -> unlock(k)));
        row.addView(chip("⏰ " + getString(R.string.family_lock_until), v -> lockUntil(k)));
        hs.addView(row);
        card.addView(hs);
        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        hs2.setHorizontalScrollBarEnabled(false);
        LinearLayout row2 = new LinearLayout(this);
        row2.addView(chip("🌙 " + getString(R.string.lock_bedtime), v -> bedtime(k)));
        row2.addView(chip("⏳ " + getString(R.string.family_limit), v -> limit(k)));
        row2.addView(chip("➕ " + getString(R.string.family_more_time), v -> moreTime(k)));
        row2.addView(chip("🚫 " + getString(R.string.family_apps), v -> apps(k)));
        row2.addView(chip("💬 " + getString(R.string.family_message), v -> message(k)));
        row2.addView(chip("🗑 " + getString(R.string.family_remove), v -> remove(k)));
        hs2.addView(row2);
        card.addView(hs2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 14);
        content.addView(card, lp);
    }

    private String minutes(int m) {
        if (m < 60) return getString(R.string.family_min, m);
        return m % 60 == 0 ? getString(R.string.family_hours, m / 60) : getString(R.string.family_hours_min, m / 60, m % 60);
    }

    private String days(int mask) {
        if (mask == 0x7F) return getString(R.string.family_every_day);
        String[] names = getResources().getStringArray(R.array.family_days);
        List<String> on = new ArrayList<>();
        for (int i = 0; i < 7; i++) if ((mask >> i & 1) == 1) on.add(names[i]);
        return android.text.TextUtils.join(", ", on);
    }

    private void unlock(Family.Child k) {
        String[] items = {getString(R.string.family_unlock_30), getString(R.string.family_unlock_60), getString(R.string.family_unlock_120),
                getString(R.string.family_unlock_rules)};
        new AlertDialog.Builder(this).setTitle(getString(R.string.family_unlock_title, k.name)).setItems(items, (d, w) -> {
            FamilyPolicy p = k.policy;
            long now = System.currentTimeMillis();
            p.lockUntil = 0;
            p.freeUntil = w == 0 ? now + 30 * 60000L : w == 1 ? now + 60 * 60000L : w == 2 ? now + 120 * 60000L : 0;
            Family.setPolicy(this, k.id, p);
            Toast.makeText(this, getString(R.string.family_sent_unlock, k.name), Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void lockUntil(Family.Child k) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (tp, h, m) -> {
            FamilyPolicy p = k.policy;
            p.lockUntil = FamilyPolicy.next(now, h * 60 + m);
            p.freeUntil = 0;
            Family.setPolicy(this, k.id, p);
            Toast.makeText(this, getString(R.string.family_sent_lock_until, k.name, FamilyPolicy.time(h * 60 + m)), Toast.LENGTH_LONG).show();
        }, 7, 0, DateFormat.is24HourFormat(this)).show();
    }

    private void bedtime(Family.Child k) {
        String[] items = {getString(R.string.family_bed_off), getString(R.string.family_bed_preset, "20:00", "7:00"),
                getString(R.string.family_bed_preset, "21:00", "7:00"), getString(R.string.family_bed_preset, "22:00", "7:00"),
                getString(R.string.family_bed_school), getString(R.string.family_bed_custom)};
        new AlertDialog.Builder(this).setTitle(R.string.lock_bedtime).setItems(items, (d, w) -> {
            FamilyPolicy p = k.policy;
            switch (w) {
                case 0: p.bedtime = false; break;
                case 1: setBed(p, 20 * 60, 7 * 60, 0x7F); break;
                case 2: setBed(p, 21 * 60, 7 * 60, 0x7F); break;
                case 3: setBed(p, 22 * 60, 7 * 60, 0x7F); break;
                case 4: setBed(p, 21 * 60, 6 * 60 + 30, 0x4F); break;  // Sunday to Thursday evening
                default: customBedtime(k); return;
            }
            Family.setPolicy(this, k.id, p);
            Toast.makeText(this, R.string.family_saved, Toast.LENGTH_SHORT).show();
        }).show();
    }

    private static void setBed(FamilyPolicy p, int from, int to, int days) {
        p.bedtime = true;
        p.bedFrom = from;
        p.bedTo = to;
        p.days = days;
    }

    private void customBedtime(Family.Child k) {
        FamilyPolicy p = k.policy;
        boolean is24 = DateFormat.is24HourFormat(this);
        new TimePickerDialog(this, (a, h1, m1) -> new TimePickerDialog(this, (b, h2, m2) -> {
            String[] names = getResources().getStringArray(R.array.family_days);
            boolean[] on = new boolean[7];
            for (int i = 0; i < 7; i++) on[i] = (p.days >> i & 1) == 1 || !p.bedtime;
            new AlertDialog.Builder(this).setTitle(R.string.family_bed_days).setMultiChoiceItems(names, on, (d, i, c) -> on[i] = c)
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        int mask = 0;
                        for (int i = 0; i < 7; i++) if (on[i]) mask |= 1 << i;
                        setBed(p, h1 * 60 + m1, h2 * 60 + m2, mask == 0 ? 0x7F : mask);
                        Family.setPolicy(this, k.id, p);
                        Toast.makeText(this, R.string.family_saved, Toast.LENGTH_SHORT).show();
                    }).setNegativeButton(R.string.cancel, null).show();
        }, p.bedTo / 60, p.bedTo % 60, is24).show(), p.bedFrom / 60, p.bedFrom % 60, is24).show();
        Toast.makeText(this, R.string.family_bed_from_to, Toast.LENGTH_LONG).show();
    }

    private void limit(Family.Child k) {
        int[] values = {0, 30, 60, 90, 120, 180, 240};
        String[] items = new String[values.length];
        for (int i = 0; i < values.length; i++) items[i] = values[i] == 0 ? getString(R.string.family_limit_off) : minutes(values[i]);
        new AlertDialog.Builder(this).setTitle(R.string.family_limit_title).setItems(items, (d, w) -> {
            FamilyPolicy p = k.policy;
            p.limitMinutes = values[w];
            Family.setPolicy(this, k.id, p);
            Toast.makeText(this, R.string.family_saved, Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void moreTime(Family.Child k) {
        int[] values = {15, 30, 60};
        String[] items = {minutes(15), minutes(30), minutes(60)};
        new AlertDialog.Builder(this).setTitle(getString(R.string.family_more_time_title, k.name)).setItems(items, (d, w) ->
                Family.answer(this, k.id, "time", values[w], true)).show();
    }

    private void apps(Family.Child k) {
        if (k.apps.isEmpty()) {
            Family.sendAsync(this, MiniJson.obj("type", "ping", "to", k.id, "apps", true));
            Toast.makeText(this, R.string.family_apps_loading, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[k.apps.size()];
        boolean[] on = new boolean[k.apps.size()];
        for (int i = 0; i < k.apps.size(); i++) {
            labels[i] = k.apps.get(i)[1];
            on[i] = k.policy.blocked.contains(k.apps.get(i)[0]);
        }
        new AlertDialog.Builder(this).setTitle(R.string.family_apps_title).setMultiChoiceItems(labels, on, (d, i, c) -> on[i] = c)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    FamilyPolicy p = k.policy;
                    p.blocked.clear();
                    for (int i = 0; i < on.length; i++) if (on[i]) p.blocked.add(k.apps.get(i)[0]);
                    Family.setPolicy(this, k.id, p);
                    Toast.makeText(this, R.string.family_saved, Toast.LENGTH_SHORT).show();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void message(Family.Child k) {
        EditText e = new EditText(this);
        e.setText(k.policy.message);
        e.setHint(R.string.family_message_hint);
        new AlertDialog.Builder(this).setTitle(R.string.family_message_title).setView(e).setPositiveButton(R.string.ok, (d, w) -> {
            FamilyPolicy p = k.policy;
            p.message = e.getText().toString().trim();
            Family.setPolicy(this, k.id, p);
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void remove(Family.Child k) {
        new AlertDialog.Builder(this).setMessage(getString(R.string.family_remove_confirm, k.name)).setPositiveButton(R.string.family_remove, (d, w) -> {
            Family.sendAsync(this, MiniJson.obj("type", "release", "to", k.id));
            Family.removeChild(this, k.id);
            show();
        }).setNegativeButton(R.string.cancel, null).show();
    }

    // ------------------------------------------------------------ child's phone

    private void childSetup() {
        para(getString(R.string.family_child_setup_text));
        EditText name = field(getString(R.string.family_child_name), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(Profile.firstName(this));
        EditText code = field(getString(R.string.family_code_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        TextView status = text("", 14, 0xFFFFB4B4, false);
        status.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, 0);
        content.addView(status);
        button(R.string.family_connect, v -> {
            String c = FamilyCrypto.normalize(code.getText().toString());
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                status.setText(R.string.family_need_name);
                return;
            }
            if (c == null) {
                status.setText(R.string.family_bad_code);
                return;
            }
            Family.becomeChild(this, c, n);
            mode = "";
            show();
        });
    }

    private void childHome() {
        FamilyPolicy p = Family.policy(this);
        FamilyPolicy.State s = Family.state(this);
        card(R.drawable.ic_shield, getString(R.string.family_protected), p.version == 0 ? getString(R.string.family_waiting_parents)
                : getString(R.string.family_protected_sub), true, null);
        StringBuilder rules = new StringBuilder(getString(R.string.family_screen_today, minutes(Family.screenMinutes(this))));
        int allowed = p.allowedMinutes(FamilyPolicy.day(System.currentTimeMillis(), TimeZone.getDefault()));
        if (allowed > 0) rules.append(" / ").append(minutes(allowed));
        if (p.bedtime) rules.append("\n").append(getString(R.string.family_bedtime_line, FamilyPolicy.time(p.bedFrom), FamilyPolicy.time(p.bedTo), days(p.days)));
        if (s.locked) rules.append("\n").append(getString(R.string.family_now_locked));
        TextView r = text(rules.toString(), 15, Color.WHITE, false);
        r.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, Ui.dp(this, 4));
        content.addView(r);
        LinearLayout ask = new LinearLayout(this);
        ask.addView(chip("🙋 " + getString(R.string.lock_ask_time), v -> {
            Family.sendAsync(this, MiniJson.obj("type", "request", "kind", "time", "minutes", 30));
            Toast.makeText(this, R.string.lock_asked, Toast.LENGTH_LONG).show();
        }));
        content.addView(ask);

        // protection checklist
        TextView h = Ui.section(this, R.string.family_protection);
        content.addView(h);
        para(getString(R.string.family_protection_text));
        boolean home = FamilyService.isDefaultHome(this);
        check(home, R.string.family_prot_home, R.string.family_prot_home_sub, v -> askHome());
        check(Settings.canDrawOverlays(this), R.string.family_prot_overlay, R.string.family_prot_overlay_sub, v -> open(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        check(FamilyService.adminActive(this), R.string.family_prot_admin, R.string.family_prot_admin_sub, v -> open(new Intent(
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, FamilyAdmin.class))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.family_admin_explain))));
        check(checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED, R.string.family_prot_phone,
                R.string.family_prot_phone_sub, v -> requestPermissions(new String[] {Manifest.permission.READ_PHONE_STATE}, PERMS));
        if (Build.VERSION.SDK_INT >= 33) {
            check(checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED, R.string.setup_perm_notify,
                    R.string.family_prot_notify_sub, v -> requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, PERMS));
        }
        check(FamilyService.batteryFree(this), R.string.family_prot_battery, R.string.family_prot_battery_sub, v -> open(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
        check(FamilyService.usageAccess(this), R.string.family_prot_usage, R.string.family_prot_usage_sub,
                v -> open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        TextView leave = text("🚪 " + getString(R.string.family_leave_child), 15, 0xFF9DB8FF, true);
        leave.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 24));
        leave.setOnClickListener(v -> leaveChild());
        content.addView(leave);
    }

    private void check(boolean ok, int title, int sub, View.OnClickListener fix) {
        View c = card(ok ? R.drawable.ic_check : R.drawable.ic_shield, getString(title), ok ? getString(R.string.family_prot_on) : getString(sub),
                false, ok ? null : fix);
        c.setAlpha(ok ? 0.75f : 1f);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Launch.settings(this, Settings.ACTION_SETTINGS);
        }
    }

    private void askHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 72);
                return;
            }
        }
        Launch.settings(this, Settings.ACTION_HOME_SETTINGS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        show();
    }

    /** Leaving the family on the child's phone needs the parents' PIN (not before the parents set rules). */
    private void leaveChild() {
        if (Family.policy(this).version == 0) {
            new AlertDialog.Builder(this).setMessage(R.string.family_leave_unpaired).setPositiveButton(R.string.family_leave, (d, w) -> {
                Family.leave(this);
                show();
            }).setNegativeButton(R.string.cancel, null).show();
            return;
        }
        EditText pin = new EditText(this);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint(R.string.lock_pin_hint);
        new AlertDialog.Builder(this).setTitle(R.string.family_leave_child).setMessage(R.string.family_leave_child_text).setView(pin)
                .setPositiveButton(R.string.family_leave, (d, w) -> {
                    if (Family.pinOk(this, pin.getText().toString())) {
                        Family.leave(this);
                        show();
                    } else {
                        Toast.makeText(this, R.string.lock_pin_wrong, Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(R.string.cancel, null).show();
    }
}
