package org.novaos.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Lays out tiles left to right and wraps into new rows (search results). */
public class FlowLayout extends ViewGroup {
    public FlowLayout(Context c) {
        super(c);
    }

    public FlowLayout(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    private int gap() {
        return getResources().getDimensionPixelSize(R.dimen.tile_gap);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int x = getPaddingLeft(), y = getPaddingTop(), rowH = 0, gap = gap();
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            measureChild(ch, widthSpec, heightSpec);
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x + w > width - getPaddingRight() && x > getPaddingLeft()) {
                x = getPaddingLeft();
                y += rowH + gap;
                rowH = 0;
            }
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
        setMeasuredDimension(width, resolveSize(y + rowH + getPaddingBottom(), heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int x = getPaddingLeft(), y = getPaddingTop(), rowH = 0, gap = gap();
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x + w > width - getPaddingRight() && x > getPaddingLeft()) {
                x = getPaddingLeft();
                y += rowH + gap;
                rowH = 0;
            }
            ch.layout(x, y, x + w, y + h);
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
}
