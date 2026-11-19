package org.novaos.tv;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Builds the 16:9 tiles; focused tiles grow and get a white frame (visible from the couch). */
final class Tiles {
    private Tiles() {}

    static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics()));
    }

    private static FrameLayout frame(Context c, boolean accent) {
        Resources r = c.getResources();
        return frame(c, accent, r.getDimensionPixelSize(R.dimen.tile_w), r.getDimensionPixelSize(R.dimen.tile_h));
    }

    private static FrameLayout frame(Context c, boolean accent, int w, int h) {
        Resources r = c.getResources();
        FrameLayout f = new FrameLayout(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMarginEnd(r.getDimensionPixelSize(R.dimen.tile_gap));
        f.setLayoutParams(lp);
        f.setBackgroundResource(accent ? R.drawable.accent_tile_bg : R.drawable.tile_bg);
        f.setFocusable(true);
        f.setClickable(true);
        int pad = dp(c, 3);
        f.setPadding(pad, pad, pad, pad);
        f.setOnFocusChangeListener((v, has) -> v.animate()
                .scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                .translationZ(has ? dp(c, 10) : 0).setDuration(140).start());
        return f;
    }

    private static View iconAndLabel(Context c, Drawable icon, String label, int iconDp) {
        return iconAndLabel(c, icon, label, iconDp, 1);
    }

    private static View iconAndLabel(Context c, Drawable icon, String label, int iconDp, int maxLines) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(icon);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        col.addView(iv, new LinearLayout.LayoutParams(dp(c, iconDp), dp(c, iconDp)));
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextColor(c.getColor(R.color.text));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(maxLines);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setLineSpacing(0, 0.92f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(c, 6);
        tv.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        col.addView(tv, tlp);
        return col;
    }

    /** App tile: the app's TV banner if it has one, otherwise icon + name. */
    static FrameLayout app(Context c, AppEntry e) {
        FrameLayout f = frame(c, false);
        if (e.banner != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(e.banner.getConstantState() != null
                    ? e.banner.getConstantState().newDrawable(c.getResources()) : e.banner);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            Drawable icon = e.icon.getConstantState() != null
                    ? e.icon.getConstantState().newDrawable(c.getResources()) : e.icon;
            f.addView(iconAndLabel(c, icon, e.label, 46));
        }
        f.setContentDescription(e.label);
        f.setTag(R.id.tag_entry, e);
        return f;
    }

    static FrameLayout action(Context c, int iconRes, int labelRes, boolean accent) {
        FrameLayout f = frame(c, accent);
        String label = c.getString(labelRes);
        // two lines: German labels such as "Bluetooth & Fernbedienungen" are long
        f.addView(iconAndLabel(c, c.getDrawable(iconRes), label, 34, 2));
        f.setContentDescription(label);
        return f;
    }

    /** TV channel: coloured tile with the channel name and its number. */
    static FrameLayout channel(Context c, int number, String name, int color) {
        FrameLayout f = frame(c, false);
        float radius = dp(c, 12);
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {color, darker(color)});
        normal.setCornerRadius(radius);
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {lighter(color), color});
        focused.setCornerRadius(radius);
        focused.setStroke(dp(c, 3), Color.WHITE);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, focused);
        bg.addState(new int[] {}, normal);
        f.setBackground(bg);

        TextView n = new TextView(c);
        n.setText(number > 0 ? String.valueOf(number) : "");
        n.setTextColor(0xCCFFFFFF);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        nlp.setMargins(dp(c, 9), dp(c, 6), 0, 0);
        f.addView(n, nlp);

        TextView t = new TextView(c);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, name.length() > 12 ? 16 : 21);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), 0);
        t.setShadowLayer(dp(c, 3), 0, dp(c, 1), 0x66000000);
        f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        f.setContentDescription(number > 0 ? number + " " + name : name);
        return f;
    }

    private static int darker(int color) {
        return Color.rgb(Color.red(color) * 7 / 10, Color.green(color) * 7 / 10, Color.blue(color) * 7 / 10);
    }

    private static int lighter(int color) {
        return Color.rgb(Math.min(255, Color.red(color) + 40), Math.min(255, Color.green(color) + 40),
                Math.min(255, Color.blue(color) + 40));
    }

    /** Video tile (Nova Filme): preview picture, title and channel name. */
    static FrameLayout video(Context c, Video v) {
        Resources r = c.getResources();
        FrameLayout f = frame(c, false, r.getDimensionPixelSize(R.dimen.video_w),
                r.getDimensionPixelSize(R.dimen.video_h));
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iv.setBackgroundColor(0xFF161A33);
        f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        ImageLoader.load(iv, v.thumbnailUrl());

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setBackgroundResource(R.drawable.video_scrim);
        text.setPadding(dp(c, 10), dp(c, 22), dp(c, 10), dp(c, 7));
        String clean = Catalog.cleanTitle(v.title, v.source.name);
        TextView t = new TextView(c);
        t.setText(clean);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLineSpacing(0, 0.92f);
        text.addView(t);
        TextView s = new TextView(c);
        s.setText(v.source.name);
        s.setTextColor(0xFFC9D2FF);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        s.setSingleLine(true);
        text.addView(s);
        f.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        f.setContentDescription(clean + ", " + v.source.name);
        return f;
    }

    /** Text-only tile (e.g. "no favorites yet"): not selectable with the remote. */
    static FrameLayout info(Context c, int textRes) {
        FrameLayout f = frame(c, false);
        f.setFocusable(false);
        f.setClickable(false);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) f.getLayoutParams();
        lp.width = lp.width * 2 + c.getResources().getDimensionPixelSize(R.dimen.tile_gap);
        TextView tv = new TextView(c);
        tv.setText(textRes);
        tv.setTextColor(c.getColor(R.color.text_muted));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        f.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return f;
    }
}
