package org.novaos.mobile;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Zoom with two fingers, double tap in and out, move the zoomed picture with one finger -
 * for photos and videos (the first child is scaled). Not zoomed, touches go on as usual
 * (swiping to the next photo, the video buttons).
 */
final class ZoomLayout extends FrameLayout {
    private final ScaleGestureDetector scaler;
    private final GestureDetector taps;
    private float scale = 1f, tx, ty, lastX, lastY;
    private boolean moving;

    ZoomLayout(Context c) {
        super(c);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomTo(Zoom.clamp(scale * d.getScaleFactor(), 1f, Zoom.MAX_VIEW), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoomTo(Zoom.doubleTap(scale), e.getX(), e.getY());
                return true;
            }
        });
    }

    boolean zoomed() {
        return scale > 1.02f;
    }

    void reset() {
        scale = 1f;
        tx = ty = 0;
        apply();
    }

    /** New scale, keeping the point under (fx, fy) where it is. */
    private void zoomTo(float s, float fx, float fy) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        tx = fx - cx - (fx - cx - tx) * s / scale;
        ty = fy - cy - (fy - cy - ty) * s / scale;
        scale = s;
        if (scale <= 1.001f) {
            scale = 1f;
            tx = ty = 0;
        }
        apply();
    }

    private void apply() {
        if (getChildCount() == 0) return;
        View v = getChildAt(0);
        float cw = v.getWidth(), ch = v.getHeight();
        if (v instanceof ImageView && ((ImageView) v).getDrawable() != null) {
            // a photo is shown whole inside the view: moving stops at the photo's edge, not the view's
            Drawable d = ((ImageView) v).getDrawable();
            float dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
            if (dw > 0 && dh > 0 && cw > 0 && ch > 0) {
                float f = Math.min(cw / dw, ch / dh);
                cw = dw * f;
                ch = dh * f;
            }
        }
        float mx = Zoom.maxShift(scale, cw, v.getWidth()), my = Zoom.maxShift(scale, ch, v.getHeight());
        tx = Zoom.clamp(tx, -mx, mx);
        ty = Zoom.clamp(ty, -my, my);
        v.setScaleX(scale);
        v.setScaleY(scale);
        v.setTranslationX(tx);
        v.setTranslationY(ty);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        taps.onTouchEvent(e);
        boolean multi = e.getPointerCount() > 1 || scaler.isInProgress();
        if (!zoomed() && !multi) {
            moving = false;
            return super.dispatchTouchEvent(e) || true;
        }
        // zoomed (or two fingers): this view moves the picture
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                moving = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!multi && moving) {
                    tx += e.getX() - lastX;
                    ty += e.getY() - lastY;
                    apply();
                }
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                moving = false;  // a finger lifted: the next move starts fresh
                break;
            default:
                break;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && e.getPointerCount() == 1) moving = true;
        return true;
    }
}
