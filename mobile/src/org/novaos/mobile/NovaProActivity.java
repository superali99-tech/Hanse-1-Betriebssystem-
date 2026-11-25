package org.novaos.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * "Nova Pro 5 Abo": more Nova - Pro 5 and no daily limit. No payment: while NovaOS is being
 * tested it is free (the prices are crossed out, nothing is charged). Switching it on is
 * confirmed with the phone's own lock (face, fingerprint or PIN).
 */
public class NovaProActivity extends Activity {
    private static final int CONFIRM = 61;
    private String plan = "month";
    private LinearLayout plans;
    private TextView action;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.ai_pro_title, getString(R.string.ai_pro_sub));
        plan = NovaPlan.plan(this);
        NovaOrb orb = new NovaOrb(this);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150));
        content.addView(orb, ol);

        LinearLayout features = new LinearLayout(this);
        features.setOrientation(LinearLayout.VERTICAL);
        features.setBackgroundResource(R.drawable.card_bg);
        features.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        feature(features, "💠", R.string.ai_f1, R.string.ai_f1_sub);
        feature(features, "♾️", R.string.ai_f2, R.string.ai_f2_sub);
        feature(features, "📚", R.string.ai_f3, R.string.ai_f3_sub);
        feature(features, "⚡", R.string.ai_f4, R.string.ai_f4_sub);
        content.addView(features);

        plans = new LinearLayout(this);
        plans.setOrientation(LinearLayout.VERTICAL);
        content.addView(plans);
        action = Ui.text(this, "", 17, Color.WHITE, true);
        action.setGravity(Gravity.CENTER);
        GradientDrawable ab = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFFA34CF0, 0xFF22D3EE});
        ab.setCornerRadius(Ui.dp(this, 20));
        action.setBackground(ab);
        action.setOnClickListener(v -> act());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56));
        al.topMargin = Ui.dp(this, 16);
        content.addView(action, al);
        TextView note = Ui.text(this, getString(R.string.ai_pro_note), 12.5f, 0xB3FFFFFF, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 10), 0, 0);
        content.addView(note);
        show();
    }

    private void feature(LinearLayout box, String icon, int title, int sub) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        row.addView(Ui.text(this, icon, 22, Color.WHITE, false), new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, getString(title), 15.5f, Color.WHITE, true));
        t.addView(Ui.text(this, getString(sub), 13, 0xB3FFFFFF, false));
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(row);
    }

    private void show() {
        boolean pro = NovaPlan.pro(this);
        plans.removeAllViews();
        plan(R.string.ai_month, "19,99 €", R.string.ai_per_month, R.string.ai_month_sub, "month", true);
        plan(R.string.ai_year, "179,99 €", R.string.ai_per_year, R.string.ai_year_sub, "year", false);
        action.setText(pro ? R.string.ai_pro_end : R.string.ai_pro_start);
        if (pro) {
            GradientDrawable ab = new GradientDrawable();
            ab.setCornerRadius(Ui.dp(this, 20));
            ab.setColor(0x33FFFFFF);
            ab.setStroke(Math.max(1, Ui.dp(this, 1)), 0x59FFFFFF);
            action.setBackground(ab);
        }
    }

    private void plan(int name, String price, int per, int sub, String key, boolean popular) {
        boolean on = key.equals(plan);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                on ? new int[] {0x4D8E6CFF, 0x3322D3EE} : new int[] {0x33FFFFFF, 0x1AFFFFFF});
        bg.setCornerRadius(Ui.dp(this, 20));
        bg.setStroke(Ui.dp(this, on ? 2 : 1), on ? 0xFFB98CFF : 0x2EFFFFFF);
        card.setBackground(bg);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.text(this, getString(name), 16, Color.WHITE, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (popular) {
            TextView b = Ui.text(this, getString(R.string.ai_popular), 11, Color.WHITE, true);
            GradientDrawable bb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFFA34CF0, 0xFF22D3EE});
            bb.setCornerRadius(Ui.dp(this, 10));
            b.setBackground(bb);
            b.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
            top.addView(b);
        }
        card.addView(top);
        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setGravity(Gravity.BOTTOM);
        TextView crossed = Ui.text(this, price, 17, 0x99FFFFFF, false);
        crossed.setPaintFlags(crossed.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        priceRow.addView(crossed);
        TextView free = Ui.text(this, "  0,00 € " + getString(per), 24, Color.WHITE, true);
        priceRow.addView(free);
        card.addView(priceRow);
        card.addView(Ui.text(this, getString(sub), 13, 0xB3FFFFFF, false));
        card.setOnClickListener(v -> {
            plan = key;
            show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 14);
        plans.addView(card, lp);
    }

    /** Switch on (confirmed with the phone's lock) or end. */
    private void act() {
        if (NovaPlan.pro(this)) {
            NovaPlan.setPro(this, false, plan);
            Toast.makeText(this, R.string.ai_pro_ended, Toast.LENGTH_LONG).show();
            show();
            return;
        }
        startActivityForResult(new Intent(this, ConfirmActivity.class).putExtra(ConfirmActivity.EXTRA_REASON, getString(R.string.ai_pro_confirm)), CONFIRM);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != CONFIRM || result != RESULT_OK) return;
        NovaPlan.setPro(this, true, plan);
        Toast.makeText(this, R.string.ai_pro_on, Toast.LENGTH_LONG).show();
        show();
        finish();
    }
}
