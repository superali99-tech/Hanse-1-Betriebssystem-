package org.novaos.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Nova Phone Shop: the Nova phones (Nova 1, Nova 2, Pro, Pro Max, Lite) with trailer banners,
 * pictures, prices and a wish list. The phones are concepts for NovaOS - they cannot be
 * bought, and the shop never asks for payment details.
 */
public class ShopActivity extends Activity {
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private String query = "", filter = "all";
    private HorizontalScrollView hero;
    private LinearLayout dots;
    private int heroPage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.app_shop, getString(R.string.shop_sub));
        Page.search(this, R.string.shop_search, q -> {
            query = q;
            show();
        });
        show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();  // the wish list may have changed on a product page
        ui.postDelayed(advance, 4500);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(advance);
        super.onPause();
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("shop", MODE_PRIVATE);
    }

    static Set<String> wishes(Context c) {
        return new HashSet<>(prefs(c).getStringSet("wishes", new HashSet<>()));
    }

    private void show() {
        content.removeAllViews();
        boolean searching = !query.trim().isEmpty();
        if (!searching && filter.equals("all")) addHero();
        addFilters();
        Set<String> wishes = wishes(this);
        LinearLayout row = null;
        int n = 0;
        for (PhoneModels.Model m : PhoneModels.ALL) {
            if (!filter.equals("all") && !(filter.equals("wish") ? wishes.contains(m.id) : m.line.equals(filter))) continue;
            StringBuilder text = new StringBuilder(m.name).append(' ').append(m.tagline(german)).append(' ').append(m.chip);
            for (String[] h : m.highlights) text.append(' ').append(german ? h[1] : h[2]);
            if (!Search.matches(query, text.toString())) continue;
            if (n % 2 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = Ui.dp(this, 10);
                content.addView(row, rl);
            }
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (n % 2 == 1) cl.leftMargin = Ui.dp(this, 10);
            row.addView(card(m, wishes.contains(m.id)), cl);
            n++;
        }
        if (n % 2 == 1) {
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, 1, 1);
            cl.leftMargin = Ui.dp(this, 10);
            row.addView(new View(this), cl);
        }
        if (n == 0) {
            TextView none = Ui.text(this, getString(filter.equals("wish") && !searching ? R.string.shop_wish_empty : R.string.search_none), 15,
                    getColor(R.color.text_muted), false);
            none.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, 0);
            content.addView(none);
        }
        TextView note = Ui.text(this, getString(R.string.shop_concept), 12, getColor(R.color.text_muted), false);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 10));
        content.addView(note);
    }

    // ------------------------------------------------------------ trailer banners

    private void addHero() {
        hero = new HorizontalScrollView(this);
        hero.setHorizontalScrollBarEnabled(false);
        LinearLayout pages = new LinearLayout(this);
        String[][] banners = {
                {"nova2promax", "1", "0xFF1A0F3D", "0xFF3B1E8C"},
                {"nova2", "0", "0xFF5A1E4A", "0xFFE0699A"},
                {"novalite", "1", "0xFF0F3550", "0xFF2D8FD0"}};
        int[] titles = {R.string.shop_hero1, R.string.shop_hero2, R.string.shop_hero3};
        int width = getResources().getDisplayMetrics().widthPixels - 2 * getResources().getDimensionPixelSize(R.dimen.side);
        for (int i = 0; i < banners.length; i++) {
            PhoneModels.Model m = PhoneModels.get(banners[i][0]);
            pages.addView(banner(m, Integer.parseInt(banners[i][1]), Long.decode(banners[i][2]).intValue(),
                    Long.decode(banners[i][3]).intValue(), titles[i]), new LinearLayout.LayoutParams(width, Ui.dp(this, 250)));
        }
        hero.addView(pages);
        // swiping: snap to a whole banner
        hero.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_UP || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                int page = Math.round(hero.getScrollX() / (float) width);
                heroPage = Math.max(0, Math.min(banners.length - 1, page));
                hero.post(() -> hero.smoothScrollTo(heroPage * width, 0));
                showDots();
                ui.removeCallbacks(advance);
                ui.postDelayed(advance, 7000);
            }
            return false;
        });
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(this, 6);
        content.addView(hero, hl);
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < banners.length; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 7), Ui.dp(this, 7));
            dl.setMargins(Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3), 0);
            dots.addView(d, dl);
        }
        content.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        heroPage = 0;
        showDots();
    }

    private void showDots() {
        if (dots == null) return;
        for (int i = 0; i < dots.getChildCount(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == heroPage ? Color.WHITE : 0x55FFFFFF);
            dots.getChildAt(i).setBackground(g);
        }
    }

    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            if (hero != null && hero.isAttachedToWindow() && hero.getChildCount() > 0) {
                ViewGroup pages = (ViewGroup) hero.getChildAt(0);
                heroPage = (heroPage + 1) % pages.getChildCount();
                hero.smoothScrollTo(pages.getChildAt(heroPage).getLeft(), 0);
                showDots();
            }
            ui.postDelayed(this, 4500);
        }
    };

    private View banner(PhoneModels.Model m, int colour, int from, int to, int title) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {from, to});
        bg.setCornerRadius(Ui.dp(this, 24));
        f.setBackground(bg);
        f.setClipToOutline(true);
        PhoneRender r = new PhoneRender(this);
        r.set(m, m.colours.get(colour), PhoneRender.BOTH);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(Ui.dp(this, 190), ViewGroup.LayoutParams.MATCH_PARENT);
        rl.gravity = Gravity.END;
        f.addView(r, rl);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 170), Ui.dp(this, 18));
        TextView name = Ui.text(this, m.name, 13, 0xCCFFFFFF, true);
        name.setLetterSpacing(0.08f);
        texts.addView(name);
        TextView t = Ui.text(this, getString(title), 24, Color.WHITE, true);
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        texts.addView(t);
        texts.addView(Ui.text(this, getString(R.string.shop_from, PhoneModels.price(PhoneModels.from(m), german)), 15, Color.WHITE, false));
        TextView more = Ui.text(this, getString(R.string.shop_more), 14, Color.WHITE, true);
        GradientDrawable mb = new GradientDrawable();
        mb.setCornerRadius(Ui.dp(this, 18));
        mb.setColor(0x33FFFFFF);
        more.setBackground(mb);
        more.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = Ui.dp(this, 14);
        texts.addView(more, ml);
        f.addView(texts, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setOnClickListener(v -> open(m));
        return f;
    }

    // ------------------------------------------------------------ filters and cards

    private void addFilters() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        String[][] all = {{"all", getString(R.string.shop_all)}, {"pro", "Pro"}, {"standard", getString(R.string.shop_standard)},
                {"lite", getString(R.string.shop_lite)}, {"wish", "♥ " + getString(R.string.shop_wishlist)}};
        for (String[] f : all) {
            TextView t = Ui.text(this, f[1], 14, Color.WHITE, f[0].equals(filter));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 18));
            if (f[0].equals(filter)) {
                bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                bg.setColors(new int[] {0xFFFFB547, 0xFFFF4D6D});
            } else {
                bg.setColor(0x26FFFFFF);
            }
            t.setBackground(bg);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
            t.setOnClickListener(v -> {
                filter = f[0];
                show();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            chips.addView(t, lp);
        }
        hs.addView(chips);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(this, 14);
        content.addView(hs, hl);
    }

    private View card(PhoneModels.Model m, boolean wished) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card_bg);
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 14));
        FrameLayout pic = new FrameLayout(this);
        PhoneRender r = new PhoneRender(this);
        r.set(m, m.colours.get(0), PhoneRender.BACK);
        pic.addView(r, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!m.badge(german).isEmpty()) {
            TextView b = Ui.text(this, m.badge(german), 11, Color.WHITE, true);
            GradientDrawable bb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFFFFB547, 0xFFFF4D6D});
            bb.setCornerRadius(Ui.dp(this, 10));
            b.setBackground(bb);
            b.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
            pic.addView(b, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (wished) {
            TextView h = Ui.text(this, "♥", 18, 0xFFFF4D6D, true);
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.gravity = Gravity.END;
            pic.addView(h, hl);
        }
        c.addView(pic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 190)));
        TextView name = Ui.text(this, m.name, 16, Color.WHITE, true);
        name.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(name);
        TextView tag = Ui.text(this, m.tagline(german), 12, getColor(R.color.text_muted), false);
        tag.setMaxLines(2);
        tag.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(tag);
        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        for (PhoneModels.Colour col : m.colours) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col.body);
            g.setStroke(Ui.dp(this, 1), 0x66FFFFFF);
            d.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12));
            dl.rightMargin = Ui.dp(this, 5);
            dotsRow.addView(d, dl);
        }
        c.addView(dotsRow);
        c.addView(Ui.text(this, getString(R.string.shop_from, PhoneModels.price(PhoneModels.from(m), german)), 15, Color.WHITE, true));
        c.setOnClickListener(v -> open(m));
        return c;
    }

    private void open(PhoneModels.Model m) {
        startActivity(new Intent(this, PhoneDetailActivity.class).putExtra(PhoneDetailActivity.EXTRA_ID, m.id));
    }
}
