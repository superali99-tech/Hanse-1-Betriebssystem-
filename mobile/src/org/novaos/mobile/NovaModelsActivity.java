package org.novaos.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * "Modell auswählen" (Nova AI): Nova 5, Nova Pro 5 (subscription), Nova Vision 5 (coming),
 * Nova Mini - glass cards, the chosen one with a glowing frame and a tick.
 */
public class NovaModelsActivity extends Activity {
    private NovaAi.Model picked;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.ai_models_title, getString(R.string.ai_models_sub));
        picked = NovaPlan.chosen(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        TextView confirm = Ui.text(this, getString(R.string.ai_models_confirm), 17, Color.WHITE, true);
        confirm.setGravity(Gravity.CENTER);
        GradientDrawable cb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xCC8E6CFF, 0xCC3D7BFF});
        cb.setCornerRadius(Ui.dp(this, 20));
        cb.setStroke(Math.max(1, Ui.dp(this, 1)), 0x80B9A8FF);
        confirm.setBackground(cb);
        confirm.setOnClickListener(v -> confirm());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56));
        bl.topMargin = Ui.dp(this, 18);
        content.addView(confirm, bl);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    private void show() {
        list.removeAllViews();
        boolean pro = NovaPlan.pro(this);
        NovaAi.Model active = NovaPlan.model(this);
        add(NovaAi.Model.NOVA5, "🧠", R.string.ai_m_nova5, R.string.ai_m_nova5_sub, active == NovaAi.Model.NOVA5 ? R.string.ai_badge_active : 0, pro);
        add(NovaAi.Model.PRO5, "💠", R.string.ai_m_pro5, R.string.ai_m_pro5_sub, active == NovaAi.Model.PRO5 ? R.string.ai_badge_active : R.string.ai_badge_pro, pro);
        add(NovaAi.Model.VISION5, "📷", R.string.ai_m_vision5, R.string.ai_m_vision5_sub, R.string.ai_badge_soon, pro);
        add(NovaAi.Model.MINI, "⚡", R.string.ai_m_mini, R.string.ai_m_mini_sub, active == NovaAi.Model.MINI ? R.string.ai_badge_active : 0, pro);
    }

    private void add(NovaAi.Model m, String icon, int name, int sub, int badge, boolean pro) {
        boolean selected = m == picked;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                selected ? new int[] {0x4D5B8CFF, 0x338E6CFF} : new int[] {0x33FFFFFF, 0x1AFFFFFF});
        bg.setCornerRadius(Ui.dp(this, 22));
        bg.setStroke(Ui.dp(this, selected ? 2 : 1), selected ? 0xFF7CC4F5 : 0x2EFFFFFF);
        row.setBackground(bg);
        int p = Ui.dp(this, 16);
        row.setPadding(p, p, p, p);
        row.setAlpha(m.ready ? 1f : 0.55f);
        TextView ic = Ui.text(this, icon, 24, Color.WHITE, false);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.OVAL);
        ib.setColor(0x26FFFFFF);
        ib.setStroke(Math.max(1, Ui.dp(this, 1)), 0x40FFFFFF);
        ic.setBackground(ib);
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.text(this, getString(name), 19, Color.WHITE, true));
        if (badge != 0) {
            TextView b = Ui.text(this, getString(badge), 11, badge == R.string.ai_badge_pro ? 0xFFFFD36B : 0xFF7CD8FF, true);
            GradientDrawable bb = new GradientDrawable();
            bb.setCornerRadius(Ui.dp(this, 10));
            bb.setStroke(Math.max(1, Ui.dp(this, 1)), badge == R.string.ai_badge_pro ? 0xB3FFD36B : 0xB37CD8FF);
            b.setBackground(bb);
            b.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = Ui.dp(this, 10);
            top.addView(b, bl);
        }
        texts.addView(top);
        texts.addView(Ui.text(this, getString(sub), 14, 0xCCFFFFFF, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 14);
        row.addView(texts, tl);
        TextView end = Ui.text(this, selected ? "✓" : "›", selected ? 18 : 24, Color.WHITE, true);
        end.setGravity(Gravity.CENTER);
        if (selected) {
            GradientDrawable eb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF7CC4F5, 0xFF8E6CFF});
            eb.setShape(GradientDrawable.OVAL);
            end.setBackground(eb);
        }
        row.addView(end, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
        row.setOnClickListener(v -> {
            if (!m.ready) {
                Toast.makeText(this, R.string.ai_soon_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            picked = m;
            show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 12);
        list.addView(row, lp);
    }

    /** Pro 5 needs the subscription: then the subscription page first. */
    private void confirm() {
        if (picked.pro && !NovaPlan.pro(this)) {
            NovaPlan.choose(this, picked);  // used as soon as Nova Pro 5 is active
            startActivity(new Intent(this, NovaProActivity.class));
            return;
        }
        NovaPlan.choose(this, picked);
        finish();
    }
}
