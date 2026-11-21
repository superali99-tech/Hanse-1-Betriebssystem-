package org.novaos.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Round progress (like the Play Store's circle around an app icon); the arc grows smoothly. */
final class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float shown, target;
    private boolean spinning;
    private float spin;

    RingView(Context c) {
        super(c);
        float w = Ui.dp(c, 10);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0x26FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
    }

    void setStroke(float dp) {
        track.setStrokeWidth(Ui.dp(getContext(), dp));
        arc.setStrokeWidth(Ui.dp(getContext(), dp));
    }

    /** 0..1; -1 = waiting (a turning piece of the ring). */
    void set(float fraction) {
        spinning = fraction < 0;
        target = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        arc.setShader(new LinearGradient(0, 0, w, h, new int[] {0xFF8E6CFF, 0xFF3D7BFF, 0xFF22C1E8}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float half = arc.getStrokeWidth() / 2 + 1;
        box.set(half, half, getWidth() - half, getHeight() - half);
        c.drawArc(box, 0, 360, false, track);
        if (spinning) {
            spin = (spin + 6) % 360;
            c.drawArc(box, spin - 90, 70, false, arc);
            postInvalidateOnAnimation();
            return;
        }
        shown += (target - shown) * 0.18f;  // glides to the new value
        if (Math.abs(target - shown) < 0.001f) shown = target;
        c.drawArc(box, -90, 360 * shown, false, arc);
        if (shown != target) postInvalidateOnAnimation();
    }
}
