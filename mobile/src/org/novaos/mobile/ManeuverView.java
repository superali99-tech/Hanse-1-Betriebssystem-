package org.novaos.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** The big arrow in the navigation banner: straight, left, right, slight, sharp, U-turn, roundabout, destination. */
final class ManeuverView extends View {
    private String type = "", modifier = "";
    private int exit;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    ManeuverView(Context c) {
        super(c);
        p.setColor(Color.WHITE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(NavRoute.Step s) {
        type = s == null ? "" : s.type;
        modifier = s == null ? "" : s.modifier;
        exit = s == null ? 0 : s.exit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), u = Math.min(w, h) / 10f;
        float cx = w / 2, bottom = h - u;
        p.setStrokeWidth(u * 1.3f);
        p.setStyle(Paint.Style.STROKE);
        if ("arrive".equals(type)) {
            // destination flag
            p.setStyle(Paint.Style.FILL);
            Path flag = new Path();
            flag.moveTo(cx - u * 2, u * 1.5f);
            flag.lineTo(cx + u * 3, u * 3);
            flag.lineTo(cx - u * 2, u * 4.5f);
            flag.close();
            c.drawPath(flag, p);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(cx - u * 2, u * 1.5f, cx - u * 2, bottom, p);
            return;
        }
        if (type.startsWith("roundabout") || type.startsWith("rotary")) {
            float r = u * 2.2f, cy = h * 0.45f;
            c.drawCircle(cx, cy, r, p);
            c.drawLine(cx, bottom, cx, cy + r, p);
            // the exit: counter-clockwise exits as numbered (right-hand traffic), one per 60 degrees
            double a = Math.toRadians(-90 + 60 * Math.max(1, exit) - 60);
            float ex = cx + (float) Math.cos(a) * r, ey = cy + (float) Math.sin(a) * r;
            float tx = cx + (float) Math.cos(a) * r * 2.1f, ty = cy + (float) Math.sin(a) * r * 2.1f;
            c.drawLine(ex, ey, tx, ty, p);
            head(c, tx, ty, (float) Math.toDegrees(a) + 90, u);
            return;
        }
        float angle;  // 0 = straight, negative = left
        switch (modifier) {
            case "left": angle = -90; break;
            case "right": angle = 90; break;
            case "slight left": angle = -45; break;
            case "slight right": angle = 45; break;
            case "sharp left": angle = -135; break;
            case "sharp right": angle = 135; break;
            case "uturn": angle = 180; break;
            default: angle = 0; break;
        }
        float stemTop = h * 0.5f;
        Path path = new Path();
        path.moveTo(cx, bottom);
        path.lineTo(cx, stemTop);
        float tx, ty;
        if (angle == 180) {
            RectF arc = new RectF(cx - u * 3, stemTop - u * 3, cx + u * 0, stemTop + u * 0);
            path.arcTo(new RectF(cx - u * 3, stemTop - u * 1.5f, cx, stemTop + u * 1.5f), 0, -180);
            tx = cx - u * 3;
            ty = stemTop + u * 2;
            path.lineTo(tx, ty);
            c.drawPath(path, p);
            head(c, tx, ty, 180, u);
            return;
        }
        double rad = Math.toRadians(angle);
        float len = u * 3.6f;
        tx = cx + (float) Math.sin(rad) * len;
        ty = stemTop - (float) Math.cos(rad) * len;
        path.lineTo(tx, ty);
        c.drawPath(path, p);
        head(c, tx, ty, angle, u);
    }

    /** Arrow head at (x, y), pointing in the compass direction deg (0 = up). */
    private void head(Canvas c, float x, float y, float deg, float u) {
        c.save();
        c.rotate(deg, x, y);
        Path h = new Path();
        h.moveTo(x, y - u * 1.6f);
        h.lineTo(x + u * 1.7f, y + u * 0.6f);
        h.lineTo(x - u * 1.7f, y + u * 0.6f);
        h.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(h, p);
        p.setStyle(Paint.Style.STROKE);
        c.restore();
    }
}
