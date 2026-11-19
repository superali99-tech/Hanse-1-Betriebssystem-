package org.novaos.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Horizontal tile rows with a title (start page, Nova Filme). */
final class Rows {
    private Rows() {}

    /** Adds a title and an empty, horizontally scrolling row; returns the row for the tiles. */
    static LinearLayout add(Context c, LinearLayout rows, String title) {
        int side = c.getResources().getDimensionPixelSize(R.dimen.margin_side);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(c.getColor(R.color.text));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimension(R.dimen.row_title));
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(side, Tiles.dp(c, 14), side, 0);
        rows.addView(t);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(side, Tiles.dp(c, 10), side, Tiles.dp(c, 10));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rows.addView(hs);
        return row;
    }

    /** Rows of tiles (the LinearLayouts inside the HorizontalScrollViews). */
    static List<LinearLayout> tileRows(LinearLayout rows) {
        List<LinearLayout> out = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View v = rows.getChildAt(i);
            if (v instanceof HorizontalScrollView) out.add((LinearLayout) ((HorizontalScrollView) v).getChildAt(0));
        }
        return out;
    }

    static View firstTile(LinearLayout rows) {
        for (LinearLayout row : tileRows(rows)) {
            View t = firstFocusable(row);
            if (t != null) return t;
        }
        return null;
    }

    static View firstFocusable(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i).isFocusable()) return row.getChildAt(i);
        }
        return null;
    }

    /**
     * Left/right stays inside a row (like on every TV launcher): without this,
     * "right" on the last tile of a short row jumped diagonally into the next
     * row (found in the Android test).
     */
    static void lockEdges(LinearLayout rows) {
        for (LinearLayout row : tileRows(rows)) {
            View first = null, last = null;
            for (int i = 0; i < row.getChildCount(); i++) {
                View t = row.getChildAt(i);
                if (!t.isFocusable()) continue;
                if (t.getId() == View.NO_ID) t.setId(View.generateViewId());
                if (first == null) first = t;
                last = t;
            }
            if (first != null) {
                first.setNextFocusLeftId(first.getId());
                last.setNextFocusRightId(last.getId());
            }
        }
    }
}
