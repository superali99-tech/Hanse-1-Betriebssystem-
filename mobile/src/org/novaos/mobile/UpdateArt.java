package org.novaos.mobile;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

/**
 * The picture at the top of the software update: a light-blue sky, a glowing sphere with a
 * warm rim and a blue ribbon sweeping out of it - drawn, so it is sharp on every screen.
 * While the update runs, the sphere breathes slowly.
 */
final class UpdateArt extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean moving;
    private long started = System.currentTimeMillis();

    UpdateArt(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);  // blurred glow
    }

    void setMoving(boolean on) {
        if (on && !moving) started = System.currentTimeMillis();
        moving = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // sky (the paint is reset: an alpha left from the glow would dim everything)
        p.reset();
        p.setAntiAlias(true);
        p.setShader(new LinearGradient(0, 0, w, h, new int[] {0xFFE3F3FD, 0xFFA9D8F8, 0xFF62B4F0}, new float[] {0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        float breathe = moving ? (float) Math.sin((System.currentTimeMillis() - started) / 900.0) * 0.015f : 0f;
        float r = Math.min(w, h * 1.25f) * (0.33f + breathe);
        float cx = w * 0.56f, cy = h * 0.46f;
        // soft glow behind the sphere
        p.setShader(null);
        p.setColor(0x66FFFFFF);
        p.setMaskFilter(new BlurMaskFilter(r * 0.35f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx - r * 0.08f, cy - r * 0.05f, r * 1.02f, p);
        p.setMaskFilter(null);
        p.setColor(0xFFFFFFFF);  // full strength for the gradients below
        // ribbon: out of the lower right of the sphere down to the bottom edge
        Path ribbon = new Path();
        ribbon.moveTo(cx + r * 0.98f, cy + r * 0.15f);
        ribbon.cubicTo(cx + r * 1.02f, cy + r * 0.9f, cx + r * 0.55f, h * 0.9f, cx + r * 0.05f, h + 2);
        ribbon.lineTo(cx - r * 0.55f, h + 2);
        ribbon.cubicTo(cx - r * 0.15f, h * 0.88f, cx + r * 0.45f, cy + r * 1.1f, cx + r * 0.62f, cy + r * 0.78f);
        ribbon.close();
        p.setShader(new LinearGradient(cx + r, cy, cx - r * 0.3f, h, new int[] {0xFF8CCBF6, 0xFF2F8FE6, 0xFF1666D0},
                null, Shader.TileMode.CLAMP));
        c.drawPath(ribbon, p);
        // the sphere: light in the middle, blue at the edge
        p.setShader(new RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.3f, new int[] {0xFFF4FBFF, 0xFFC7E7FB, 0xFF8DC9F4},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        // warm rim on the left, like evening light
        p.setShader(new SweepGradient(cx, cy, new int[] {0x00F7B08A, 0x00F7B08A, 0xCCF7A57C, 0xFFF4B596, 0x66F7B08A, 0x00F7B08A},
                new float[] {0f, 0.35f, 0.47f, 0.55f, 0.72f, 1f}));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.06f);
        c.drawCircle(cx, cy, r * 0.97f, p);
        p.setStyle(Paint.Style.FILL);
        // gloss
        p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 0.6f, 0x88FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx - r * 0.35f, cy - r * 0.45f, r * 0.6f, p);
        p.setShader(null);
        // fade into the dark page below
        p.setShader(new LinearGradient(0, h * 0.82f, 0, h, 0x000E1430, 0x660E1430, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.82f, w, h, p);
        p.setShader(null);
        if (moving) postInvalidateDelayed(50);
    }
}
