package org.novaos.mobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small building blocks of the NovaOS Mobile screens. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics()));
    }

    static int widthDp(Context c) {
        return Math.round(c.getResources().getConfiguration().screenWidthDp);
    }

    /** Draw behind the transparent status and navigation bars (content uses fitsSystemWindows). */
    @SuppressWarnings("deprecation")
    static void edgeToEdge(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /** Full screen for video: bars come back with a swipe from the edge. */
    @SuppressWarnings("deprecation")
    static void immersive(Activity a, boolean on) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (on) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /** Round "+" style button at the bottom right, above the navigation bar. */
    static android.widget.ImageButton fab(Activity a, int icon, int label, View.OnClickListener click) {
        android.widget.ImageButton b = new android.widget.ImageButton(a);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.accent_round_bg);
        b.setContentDescription(a.getString(label));
        int p = dp(a, 16);
        b.setPadding(p, p, p, p);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setElevation(dp(a, 6));
        b.setOnClickListener(click);
        android.widget.FrameLayout.LayoutParams fl = new android.widget.FrameLayout.LayoutParams(dp(a, 60), dp(a, 60),
                Gravity.BOTTOM | Gravity.END);
        int margin = dp(a, 22);
        fl.rightMargin = margin;
        fl.bottomMargin = margin;
        android.widget.FrameLayout content = a.findViewById(android.R.id.content);
        content.addView(b, fl);
        View list = a.findViewById(R.id.content);  // the page's list: room for the button at its end
        if (list != null) {
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                    list.getPaddingBottom() + dp(a, 84));
        }
        // the page is drawn behind the navigation bar: keep the button above it (the insets are
        // read here because the page itself consumes them before they reach the button)
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) b.getLayoutParams();
            lp.bottomMargin = margin + insets.getSystemWindowInsetBottom();
            lp.rightMargin = margin + insets.getSystemWindowInsetRight();
            b.setLayoutParams(lp);
            return v.onApplyWindowInsets(insets);
        });
        content.requestApplyInsets();
        return b;
    }

    /** Coloured NovaOS button (e.g. "Allow access"). */
    static android.widget.Button button(Context c, int text, View.OnClickListener click) {
        android.widget.Button b = new android.widget.Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundResource(R.drawable.accent_card_bg);
        b.setPadding(dp(c, 24), 0, dp(c, 24), 0);
        b.setMinHeight(dp(c, 48));
        b.setOnClickListener(click);
        return b;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView section(Context c, int res) {
        TextView t = text(c, c.getString(res), 17, Color.WHITE, true);
        t.setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 8));
        return t;
    }

    static Drawable copy(Context c, Drawable d) {
        return d.getConstantState() != null ? d.getConstantState().newDrawable(c.getResources()) : d;
    }

    /** App icon with name below (favorites, app overview); without name in the dock. */
    static LinearLayout appCell(Context c, App app, boolean withLabel) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(c, 6);
        col.setPadding(pad, pad, pad, pad);
        col.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), null, roundMask(dp(c, 16))));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(copy(c, app.icon));
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int s = c.getResources().getDimensionPixelSize(R.dimen.icon);
        col.addView(iv, new LinearLayout.LayoutParams(s, s));
        if (withLabel) {
            TextView t = text(c, app.label, 12.5f, Color.WHITE, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(dp(c, 3), 0, dp(c, 1), 0x99000000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 5);
            col.addView(t, lp);
        }
        col.setContentDescription(app.label);
        col.setClickable(true);
        col.setFocusable(true);
        return col;
    }

    private static GradientDrawable roundMask(int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(radius);
        return g;
    }

    /** Coloured channel tile (like on NovaOS TV): number and name. */
    static View channelTile(Context c, int number, String name, int color) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {color, darker(color)});
        bg.setCornerRadius(dp(c, 18));
        box.setBackground(new RippleDrawable(ColorStateList.valueOf(0x50FFFFFF), bg, null));
        box.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        TextView no = text(c, String.valueOf(number), 12, 0xCCFFFFFF, true);
        box.addView(no);
        TextView n = text(c, name, name.length() > 12 ? 15 : 19, Color.WHITE, true);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setShadowLayer(dp(c, 3), 0, dp(c, 1), 0x66000000);
        box.addView(n);
        box.setClickable(true);
        box.setContentDescription(number + " " + name);
        return box;
    }

    static int darker(int color) {
        return Color.rgb(Color.red(color) * 7 / 10, Color.green(color) * 7 / 10, Color.blue(color) * 7 / 10);
    }
}
