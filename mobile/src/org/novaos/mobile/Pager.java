package org.novaos.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

/**
 * Pages side by side, changed by swiping left and right (like the home screen and the app
 * overview of Samsung phones). Each child is one page as wide as the pager. Vertical swipes
 * are left to the screen (up: all apps, down: notifications).
 */
final class Pager extends ViewGroup {
    interface Listener {
        void pageChanged(int page);
    }

    private final OverScroller scroller;
    private final int slop, minFling;
    private VelocityTracker velocity;
    private float downX, downY, lastX;
    private boolean dragging;
    private int page;
    private Listener listener;

    Pager(Context c) {
        super(c);
        scroller = new OverScroller(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
    }

    void setListener(Listener l) {
        listener = l;
    }

    int page() {
        return page;
    }

    int pages() {
        return getChildCount();
    }

    void setPage(int p, boolean animate) {
        p = Math.max(0, Math.min(getChildCount() - 1, p));
        int to = p * getWidth();
        if (animate && getWidth() > 0) {
            scroller.startScroll(getScrollX(), 0, to - getScrollX(), 0, 320);
            postInvalidateOnAnimation();
        } else {
            scroller.forceFinished(true);
            scrollTo(to, 0);
        }
        if (p != page) {
            page = p;
            if (listener != null) listener.pageChanged(p);
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w), height = MeasureSpec.getSize(h);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        if (page >= getChildCount()) page = Math.max(0, getChildCount() - 1);
        if (scroller.isFinished()) scrollTo(page * w, 0);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = !scroller.isFinished();
                track(e);
                return dragging;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                if (getChildCount() > 1 && dx > slop && dx > dy * 1.2f) {
                    dragging = true;
                    lastX = e.getX();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                track(e);
                return dragging;
            default:
                return dragging;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished()) scroller.abortAnimation();
                downX = lastX = e.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = e.getX();
                int max = Math.max(0, (getChildCount() - 1) * getWidth());
                float dx = lastX - x;
                // a little resistance beyond the first and the last page
                if (getScrollX() + dx < 0 || getScrollX() + dx > max) dx /= 3;
                scrollBy((int) dx, 0);
                lastX = x;
                dragging = true;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                int target = page;
                if (velocity != null && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    velocity.computeCurrentVelocity(1000);
                    float v = velocity.getXVelocity();
                    if (v < -minFling) target = page + 1;
                    else if (v > minFling) target = page - 1;
                    else target = Math.round(getScrollX() / (float) Math.max(1, getWidth()));
                }
                dragging = false;
                if (velocity != null) {
                    velocity.recycle();
                    velocity = null;
                }
                setPage(target, true);
                return true;
            default:
                return true;
        }
    }

    private void track(MotionEvent e) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    /** The dots under the pages: the current one white. */
    static final class Dots extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int count, current;

        Dots(Context c) {
            super(c);
        }

        void set(int count, int current) {
            this.count = count;
            this.current = current;
            setVisibility(count > 1 ? VISIBLE : INVISIBLE);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (count < 2) return;
            float d = getResources().getDisplayMetrics().density, r = 3.5f * d, gap = 13 * d;
            float x = getWidth() / 2f - (count - 1) * gap / 2, y = getHeight() / 2f;
            for (int i = 0; i < count; i++) {
                p.setColor(i == current ? 0xFFFFFFFF : 0x80FFFFFF);
                c.drawCircle(x + i * gap, y, i == current ? r * 1.15f : r, p);
            }
        }
    }
}
