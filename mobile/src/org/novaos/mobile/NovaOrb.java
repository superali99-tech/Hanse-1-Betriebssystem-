package org.novaos.mobile;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * "Nova denkt nach …": a glowing sphere of light lines and points that slowly turns, with
 * rings around it - drawn, so it moves smoothly and is sharp on every screen.
 */
final class NovaOrb extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long started = System.currentTimeMillis();
    private final float[][] dots = new float[34][2];

    NovaOrb(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);  // the glow
        java.util.Random r = new java.util.Random(7);
        for (float[] d : dots) {
            d[0] = (float) (r.nextDouble() * Math.PI * 2);        // longitude
            d[1] = (float) (Math.acos(2 * r.nextDouble() - 1));   // latitude
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = Math.min(w, h) * 0.36f;
        double t = (System.currentTimeMillis() - started) / 1000.0;
        // glow behind
        p.setShader(new RadialGradient(cx, cy, r * 1.5f, new int[] {0x664B7BFF, 0x338E6CFF, 0x00000000}, new float[] {0f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.5f, p);
        p.setShader(null);
        // the points of the sphere, turning; lines between near ones
        float[][] pts = new float[dots.length][3];
        for (int i = 0; i < dots.length; i++) {
            double lon = dots[i][0] + t * 0.35, lat = dots[i][1];
            double x = Math.sin(lat) * Math.cos(lon), y = Math.cos(lat), z = Math.sin(lat) * Math.sin(lon);
            pts[i][0] = cx + (float) x * r;
            pts[i][1] = cy + (float) y * r;
            pts[i][2] = (float) z;
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r / 90));
        for (int i = 0; i < pts.length; i++) {
            for (int j = i + 1; j < pts.length; j++) {
                float dx = pts[i][0] - pts[j][0], dy = pts[i][1] - pts[j][1];
                if (dx * dx + dy * dy > r * r * 0.55f) continue;
                float depth = (pts[i][2] + pts[j][2]) / 2;
                int alpha = (int) (60 + 110 * (depth + 1) / 2);
                p.setColor((alpha << 24) | (i % 2 == 0 ? 0x7CD8FF : 0xC48CFF));
                c.drawLine(pts[i][0], pts[i][1], pts[j][0], pts[j][1], p);
            }
        }
        // two rings around the sphere
        p.setColor(0x806FB8FF);
        c.save();
        c.rotate((float) (t * 12), cx, cy);
        c.drawOval(cx - r * 1.25f, cy - r * 0.38f, cx + r * 1.25f, cy + r * 0.38f, p);
        c.restore();
        p.setColor(0x80B98CFF);
        c.save();
        c.rotate((float) (-t * 9 + 60), cx, cy);
        c.drawOval(cx - r * 1.18f, cy - r * 0.3f, cx + r * 1.18f, cy + r * 0.3f, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
        for (float[] q : pts) {
            float s = r / 30 * (1.3f + q[2]);
            p.setColor(q[2] > 0 ? 0xFFFFFFFF : 0x99B9D8FF);
            p.setMaskFilter(new BlurMaskFilter(s * 1.5f, BlurMaskFilter.Blur.SOLID));
            c.drawCircle(q[0], q[1], s, p);
        }
        p.setMaskFilter(null);
        if (isShown()) postInvalidateDelayed(33);
    }
}
