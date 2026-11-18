package org.novaos.tv;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
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
        FrameLayout f = new FrameLayout(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                r.getDimensionPixelSize(R.dimen.tile_w), r.getDimensionPixelSize(R.dimen.tile_h));
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
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
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
        f.addView(iconAndLabel(c, c.getDrawable(iconRes), label, 38));
        f.setContentDescription(label);
        return f;
    }

    static FrameLayout info(Context c, int textRes) {
        FrameLayout f = frame(c, false);
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
