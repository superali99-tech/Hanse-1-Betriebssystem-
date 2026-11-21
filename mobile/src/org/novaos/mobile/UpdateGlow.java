package org.novaos.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The background of "Deine Software ist auf dem neuesten Stand": a dark page with a soft glow
 * in the middle - blue above, warm brown below, like on Samsung phones. While NovaOS looks for
 * updates, the two lights slowly circle.
 */
final class UpdateGlow extends View {
    static final int BACKGROUND = 0xFF17171B;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean busy;
    private long started = System.currentTimeMillis();

    UpdateGlow(Context c) {
        super(c);
    }

    void setBusy(boolean on) {
        if (on && !busy) started = System.currentTimeMillis();
        busy = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawColor(BACKGROUND);
        if (w <= 0 || h <= 0) return;
        float r = Math.min(w, h) * 0.48f;
        float cx = w / 2f, cy = h * 0.47f;
        double a = busy ? (System.currentTimeMillis() - started) / 1400.0 : 0;
        float dx = (float) Math.sin(a) * r * 0.10f, dy = (float) (1 - Math.cos(a)) * r * 0.05f;
        // warm light below on the left
        p.setShader(new RadialGradient(cx - r * 0.18f - dx, cy + r * 0.32f + dy, r * 0.95f,
                new int[] {0x995C3626, 0x4D4A2C22, 0x00171717}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx - r * 0.18f - dx, cy + r * 0.32f + dy, r * 0.95f, p);
        // blue light above on the right
        p.setShader(new RadialGradient(cx + r * 0.10f + dx, cy - r * 0.22f - dy, r * 1.0f,
                new int[] {0xB31F3C8C, 0x59203470, 0x00171717}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx + r * 0.10f + dx, cy - r * 0.22f - dy, r * 1.0f, p);
        p.setShader(null);
        if (busy) postInvalidateDelayed(40);
    }
}
