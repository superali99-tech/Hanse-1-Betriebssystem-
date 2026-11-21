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
 * The picture of "Letztes erfolgreiches Update": the number of the release (NovaOS 1.5) big
 * and blue in front of a glowing sun, on a pale grey-blue sky - in the soft colours of the
 * One UI pictures. Drawn, so it is sharp on every screen.
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

        // the number of the release ("1.5") in front of the sun, blue with a light edge on top
        String number = UpdateFacts.RELEASE;
        p.setTypeface(Fonts.bold(getContext()));
        p.setTextAlign(Paint.Align.CENTER);
        float size = h * 0.62f;
        p.setTextSize(size);
        float tw = p.measureText(number);
        if (tw > w * 0.82f) {
            size *= w * 0.82f / tw;
            p.setTextSize(size);
        }
        float base = sy + size * 0.36f;
        p.setShader(null);
        p.setColor(0x33001A66);
        c.drawText(number, sx + size * 0.03f, base + size * 0.04f, p);  // soft shadow
        p.setColor(0xFFFFFFFF);  // full strength again: the shadow's transparency would dim the number
        p.setShader(new LinearGradient(0, base - size * 0.75f, 0, base, new int[] {0xFF8DB2EE, 0xFF3F7BE0, 0xFF1352D6},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawText(number, sx, base, p);
        p.setShader(null);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(null);

        // fade into the dark page below
        p.setShader(new LinearGradient(0, h * 0.86f, 0, h, 0x0017171B, 0x6617171B, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.86f, w, h, p);
        p.setShader(null);
    }
}
