package org.novaos.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Set;

/**
 * A Nova phone in the Nova Phone Shop: large picture (tap: front, back, both), colours,
 * storage with price, highlights and technical data, wish list. A concept - not for sale.
 */
public class PhoneDetailActivity extends Activity {
    static final String EXTRA_ID = "id";
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private PhoneModels.Model model;
    private int colour, storage;
    private PhoneRender render;
    private TextView colourName, price, wish;
    private LinearLayout colours, storages;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = PhoneModels.get(String.valueOf(getIntent().getStringExtra(EXTRA_ID)));
        if (model == null) {
            finish();
            return;
        }
        LinearLayout content = Page.setup(this, R.string.app_shop, "");
        ((TextView) findViewById(R.id.page_title)).setText(model.name);

        // the big picture; tap turns it (both -> front -> back)
        render = new PhoneRender(this);
        render.setContentDescription(model.name);
        render.setOnClickListener(v -> {
            int next = render.view() == PhoneRender.BOTH ? PhoneRender.FRONT : render.view() == PhoneRender.FRONT ? PhoneRender.BACK : PhoneRender.BOTH;
            render.set(model, model.colours.get(colour), next);
        });
        GradientDrawable stage = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x33FFFFFF, 0x0DFFFFFF});
        stage.setCornerRadius(Ui.dp(this, 26));
        render.setBackground(stage);
        content.addView(render, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 380)));
        TextView turn = Ui.text(this, getString(R.string.shop_turn), 12, getColor(R.color.text_muted), false);
        turn.setGravity(Gravity.CENTER);
        turn.setPadding(0, Ui.dp(this, 6), 0, 0);
        content.addView(turn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // colours
        colours = new LinearLayout(this);
        colours.setGravity(Gravity.CENTER);
        colours.setPadding(0, Ui.dp(this, 12), 0, 0);
        content.addView(colours, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        colourName = Ui.text(this, "", 13, getColor(R.color.text_muted), false);
        colourName.setGravity(Gravity.CENTER);
        content.addView(colourName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // name, badge, tagline, price
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(this, 16), 0, 0);
        head.addView(Ui.text(this, model.name, 26, Color.WHITE, true));
        if (!model.badge(german).isEmpty()) {
            TextView b = Ui.text(this, model.badge(german), 12, Color.WHITE, true);
            GradientDrawable bb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFFFFB547, 0xFFFF4D6D});
            bb.setCornerRadius(Ui.dp(this, 10));
            b.setBackground(bb);
            b.setPadding(Ui.dp(this, 9), Ui.dp(this, 3), Ui.dp(this, 9), Ui.dp(this, 3));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = Ui.dp(this, 10);
            head.addView(b, bl);
        }
        content.addView(head);
        TextView tag = Ui.text(this, model.tagline(german), 16, 0xE6FFFFFF, false);
        tag.setPadding(0, Ui.dp(this, 4), 0, 0);
        content.addView(tag);
        price = Ui.text(this, "", 24, Color.WHITE, true);
        price.setPadding(0, Ui.dp(this, 12), 0, 0);
        content.addView(price);

        // storage
        storages = new LinearLayout(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(storages);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 10);
        content.addView(hs, sl);

        // wish list (no buying: the Nova phones are concepts)
        wish = Ui.text(this, "", 16, Color.WHITE, true);
        wish.setGravity(Gravity.CENTER);
        wish.setBackgroundResource(R.drawable.accent_card_bg);
        wish.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        wish.setOnClickListener(v -> toggleWish());
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.topMargin = Ui.dp(this, 16);
        content.addView(wish, wl);
        TextView concept = Ui.text(this, getString(R.string.shop_concept_short), 12, getColor(R.color.text_muted), false);
        concept.setGravity(Gravity.CENTER);
        concept.setPadding(0, Ui.dp(this, 6), 0, 0);
        content.addView(concept, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // highlights
        content.addView(Ui.section(this, R.string.shop_highlights));
        for (String[] h : model.highlights) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackgroundResource(R.drawable.card_bg);
            r.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
            TextView icon = Ui.text(this, h[0], 22, Color.WHITE, false);
            icon.setGravity(Gravity.CENTER);
            r.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 36), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView t = Ui.text(this, german ? h[1] : h[2], 15, Color.WHITE, false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tl.leftMargin = Ui.dp(this, 10);
            r.addView(t, tl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = Ui.dp(this, 8);
            content.addView(r, rl);
        }

        // technical data
        content.addView(Ui.section(this, R.string.shop_specs));
        LinearLayout specs = new LinearLayout(this);
        specs.setOrientation(LinearLayout.VERTICAL);
        specs.setBackgroundResource(R.drawable.card_bg);
        specs.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        for (String[] s : model.specs) {
            LinearLayout r = new LinearLayout(this);
            r.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            r.addView(Ui.text(this, german ? s[0] : s[1], 14, getColor(R.color.text_muted), false),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
            TextView v = Ui.text(this, german ? s[2] : s[3], 14, Color.WHITE, false);
            r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
            specs.addView(r);
        }
        content.addView(specs);
        TextView note = Ui.text(this, getString(R.string.shop_concept), 12, getColor(R.color.text_muted), false);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 10));
        content.addView(note);
        refresh();
    }

    private void refresh() {
        render.set(model, model.colours.get(colour), render.view());
        colours.removeAllViews();
        for (int i = 0; i < model.colours.size(); i++) {
            PhoneModels.Colour c = model.colours.get(i);
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c.body);
            g.setStroke(Ui.dp(this, i == colour ? 3 : 1), i == colour ? Color.WHITE : 0x66FFFFFF);
            d.setBackground(g);
            d.setContentDescription(c.name(german));
            int which = i;
            d.setOnClickListener(v -> {
                colour = which;
                refresh();
            });
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
            dl.setMargins(Ui.dp(this, 7), 0, Ui.dp(this, 7), Ui.dp(this, 6));
            colours.addView(d, dl);
        }
        colourName.setText(model.colours.get(colour).name(german));
        price.setText(PhoneModels.price(model.price(storage), german));
        storages.removeAllViews();
        for (int i = 0; i < model.storage.length; i++) {
            TextView t = Ui.text(this, PhoneModels.storage(model.storage[i]) + "\n" + PhoneModels.price(model.price(i), german), 14,
                    Color.WHITE, i == storage);
            t.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 16));
            bg.setColor(i == storage ? 0x33FFFFFF : 0x14FFFFFF);
            bg.setStroke(Ui.dp(this, i == storage ? 2 : 1), i == storage ? 0xFFFFB547 : 0x33FFFFFF);
            t.setBackground(bg);
            t.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
            int which = i;
            t.setOnClickListener(v -> {
                storage = which;
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 10);
            storages.addView(t, lp);
        }
        wish.setText(ShopActivity.wishes(this).contains(model.id) ? "♥ " + getString(R.string.shop_on_wishlist) : "♡ " + getString(R.string.shop_add_wishlist));
    }

    private void toggleWish() {
        Set<String> w = ShopActivity.wishes(this);
        boolean add = !w.contains(model.id);
        if (add) w.add(model.id);
        else w.remove(model.id);
        ShopActivity.prefs(this).edit().putStringSet("wishes", w).apply();
        Toast.makeText(this, add ? getString(R.string.shop_wish_added, model.name) : getString(R.string.shop_wish_removed, model.name),
                Toast.LENGTH_SHORT).show();
        refresh();
    }
}
