package org.novaos.mobile;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The picture of "Letztes erfolgreiches Update": the big "1" of NovaOS 1.0 in soft evening
 * colours - an orange flag and a blue stroke in front of a glowing sun, on a pale grey-blue
 * sky. Drawn, so it is sharp on every screen.
 */
final class ReleaseArt extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    ReleaseArt(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);  // blurred glow
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        p.reset();
        p.setAntiAlias(true);
        // sky: warm grey on the left, light blue on the right
        p.setShader(new LinearGradient(0, 0, w, h * 0.6f, new int[] {0xFFD8D1CC, 0xFFC9CED8, 0xFFAFC6E6},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        float r = Math.min(w, h * 1.6f) * 0.30f;
        float sx = w * 0.50f, sy = h * 0.44f;

        // a soft sun behind the 1: warm orange on the left, light in the middle, blue on the right
        p.setShader(null);
        p.setColor(0x55FFFFFF);
        p.setMaskFilter(new BlurMaskFilter(r * 0.4f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(sx, sy, r * 1.05f, p);
        p.setMaskFilter(null);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(sx - r, sy, sx + r, sy, new int[] {0xFFE6A27C, 0xFFEBD3C4, 0xFFCAD9EE, 0xFF97B8EC},
                new float[] {0f, 0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, r, p);
        p.setShader(new RadialGradient(sx - r * 0.2f, sy - r * 0.35f, r * 0.9f, 0x80FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, r, p);

        // the 1: the flag (orange, round end) and the stroke (blue) down out of the picture
        float sw = r * 0.46f, cx = w * 0.56f, top = h * 0.13f;
        float dx = -0.8f, dy = 0.6f, len = r * 0.78f, t = sw * 0.86f;
        p.setShader(new LinearGradient(cx + dx * len, top + dy * len, cx, top, 0xFFE07A45, 0xFFF2B48E, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(t);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx, top + t / 2, cx + dx * len, top + t / 2 + dy * len, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
        // shadow of the flag on the stroke, then the stroke itself
        android.graphics.RectF stem = new android.graphics.RectF(cx - sw / 2, top, cx + sw / 2, h + sw);
        p.setShader(new LinearGradient(0, top, 0, h, new int[] {0xFF8DB2EE, 0xFF3F7BE0, 0xFF1352D6}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRoundRect(stem, sw * 0.18f, sw * 0.18f, p);
        p.setShader(new LinearGradient(cx - sw / 2, 0, cx + sw / 2, 0, new int[] {0x55FFFFFF, 0x00FFFFFF, 0x33001A66},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(stem, sw * 0.18f, sw * 0.18f, p);
        p.setShader(null);

        // fade into the dark page below
        p.setShader(new LinearGradient(0, h * 0.86f, 0, h, 0x0017171B, 0x6617171B, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.86f, w, h, p);
        p.setShader(null);
    }
}
