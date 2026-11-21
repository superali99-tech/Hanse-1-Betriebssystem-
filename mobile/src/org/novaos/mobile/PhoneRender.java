package org.novaos.mobile;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Product pictures of the Nova phones, drawn (no photos): the front with the NovaOS screen
 * and the back with the camera module, metal frame, glass reflections and a soft shadow.
 * "both" shows the back slightly turned behind the front, like in a shop.
 */
final class PhoneRender extends View {
    static final int FRONT = 0, BACK = 1, BOTH = 2;
    private PhoneModels.Model model;
    private PhoneModels.Colour colour;
    private int view = BOTH;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    PhoneRender(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);  // blurred shadow
    }

    void set(PhoneModels.Model m, PhoneModels.Colour c, int how) {
        model = m;
        colour = c;
        view = how;
        invalidate();
    }

    int view() {
        return view;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (model == null) return;
        float w = getWidth(), h = getHeight();
        // the phone's proportions: about 1 : 2.1
        float ph = h * 0.88f, pw = ph / 2.1f;
        if (view == BOTH) {
            pw = Math.min(pw, w * 0.36f);
            ph = pw * 2.1f;
            float cx = w / 2f, top = (h - ph) / 2f;
            c.save();
            c.rotate(-9, cx + pw * 0.38f, top + ph / 2);
            drawPhone(c, cx - pw * 0.05f, top - ph * 0.02f, pw, ph, true);
            c.restore();
            c.save();
            c.rotate(6, cx - pw * 0.4f, top + ph / 2);
            drawPhone(c, cx - pw * 0.95f, top + ph * 0.03f, pw, ph, false);
            c.restore();
        } else {
            pw = Math.min(pw, w * 0.62f);
            ph = pw * 2.1f;
            drawPhone(c, (w - pw) / 2f, (h - ph) / 2f, pw, ph, view == BACK);
        }
    }

    private void drawPhone(Canvas c, float x, float y, float w, float h, boolean back) {
        float rad = w * 0.15f;
        // soft shadow under the phone
        p.reset();
        p.setAntiAlias(true);
        p.setColor(0x66000000);
        p.setMaskFilter(new BlurMaskFilter(w * 0.09f, BlurMaskFilter.Blur.NORMAL));
        r.set(x + w * 0.06f, y + h * 0.04f, x + w * 1.02f, y + h * 1.015f);
        c.drawRoundRect(r, rad, rad, p);
        p.setMaskFilter(null);
        // metal frame: light on the left edge, darker on the right
        int frame = colour.frame;
        p.setShader(new LinearGradient(x, y, x + w, y, new int[] {lighter(frame, 0.35f), frame, darker(frame, 0.75f), lighter(frame, 0.2f)},
                new float[] {0f, 0.15f, 0.85f, 1f}, Shader.TileMode.CLAMP));
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        // side buttons
        p.setColor(darker(frame, 0.8f));
        float bx = back ? x - w * 0.012f : x + w - w * 0.002f;
        c.drawRoundRect(new RectF(bx, y + h * 0.2f, bx + w * 0.014f, y + h * 0.27f), 3, 3, p);
        c.drawRoundRect(new RectF(bx, y + h * 0.3f, bx + w * 0.014f, y + h * 0.42f), 3, 3, p);
        float inset = w * 0.022f;
        r.set(x + inset, y + inset, x + w - inset, y + h - inset);
        float irad = rad - inset;
        if (back) drawBack(c, r, irad, w);
        else drawFront(c, r, irad, w);
        // glass reflection over everything
        p.reset();
        p.setAntiAlias(true);
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.top + r.height() * 0.6f,
                new int[] {0x30FFFFFF, 0x08FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, irad, irad, p);
        p.setShader(null);
    }

    private void drawFront(Canvas c, RectF r, float rad, float w) {
        // black bezel, then the screen with the NovaOS wallpaper
        p.reset();
        p.setAntiAlias(true);
        p.setColor(0xFF050507);
        c.drawRoundRect(r, rad, rad, p);
        float b = w * 0.03f;
        RectF s = new RectF(r.left + b, r.top + b, r.right - b, r.bottom - b);
        float srad = rad - b;
        p.setShader(new LinearGradient(s.left, s.top, s.right, s.bottom, new int[] {0xFF2A1B6E, 0xFF3D2FA8, 0xFF1C5AC9, 0xFF0D1A3F},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(s, srad, srad, p);
        p.setShader(null);
        // soft light blobs of the wallpaper
        int save = c.save();
        Path clip = new Path();
        clip.addRoundRect(s, srad, srad, Path.Direction.CW);
        c.clipPath(clip);
        blob(c, s.left + s.width() * 0.2f, s.top + s.height() * 0.62f, s.width() * 0.7f, 0x99FF5FA2);
        blob(c, s.right - s.width() * 0.1f, s.top + s.height() * 0.3f, s.width() * 0.6f, 0x8822C1E8);
        blob(c, s.left + s.width() * 0.6f, s.bottom, s.width() * 0.8f, 0x887C3AED);
        // clock and date
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(s.width() * 0.3f);
        c.drawText("9:41", s.centerX(), s.top + s.height() * 0.27f, p);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(s.width() * 0.065f);
        p.setColor(0xDDFFFFFF);
        c.drawText(model.name, s.centerX(), s.top + s.height() * 0.33f, p);
        // dock with four app icons
        float d = s.width() * 0.13f, gap = (s.width() - 4 * d) / 5f, dy = s.bottom - s.height() * 0.1f;
        int[] icons = {0xFF34C759, 0xFF7C5CFF, 0xFFFF5FA2, 0xFF22C1E8};
        for (int i = 0; i < 4; i++) {
            float ix = s.left + gap + i * (d + gap);
            p.setShader(new LinearGradient(ix, dy - d, ix + d, dy, lighter(icons[i], 0.25f), icons[i], Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(ix, dy - d, ix + d, dy), d * 0.28f, d * 0.28f, p);
        }
        p.setShader(null);
        // status bar and camera hole
        p.setColor(0xCCFFFFFF);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(s.width() * 0.05f);
        c.drawText("9:41", s.left + s.width() * 0.08f, s.top + s.width() * 0.09f, p);
        c.drawRoundRect(new RectF(s.right - s.width() * 0.2f, s.top + s.width() * 0.05f, s.right - s.width() * 0.08f, s.top + s.width() * 0.085f), 4, 4, p);
        p.setColor(0xFF000000);
        c.drawCircle(s.centerX(), s.top + s.width() * 0.07f, s.width() * 0.032f, p);
        p.setColor(0xFF1B2440);
        c.drawCircle(s.centerX() - s.width() * 0.008f, s.top + s.width() * 0.066f, s.width() * 0.01f, p);
        c.restoreToCount(save);
    }

    private void blob(Canvas c, float x, float y, float radius, int color) {
        p.setShader(new RadialGradient(x, y, radius, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, radius, p);
        p.setShader(null);
    }

    private void drawBack(Canvas c, RectF r, float rad, float w) {
        int body = colour.body;
        p.reset();
        p.setAntiAlias(true);
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, new int[] {lighter(body, 0.18f), body, darker(body, 0.82f)},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        boolean light = luminance(body) > 0.6f;
        // Nova star in the middle and the name at the bottom
        float cx = r.centerX(), cy = r.top + r.height() * 0.55f, s = w * 0.11f;
        Path star = new Path();
        star.moveTo(cx, cy - s);
        star.quadTo(cx + s * 0.12f, cy - s * 0.12f, cx + s, cy);
        star.quadTo(cx + s * 0.12f, cy + s * 0.12f, cx, cy + s);
        star.quadTo(cx - s * 0.12f, cy + s * 0.12f, cx - s, cy);
        star.quadTo(cx - s * 0.12f, cy - s * 0.12f, cx, cy - s);
        p.setColor(light ? 0x55000000 : 0x55FFFFFF);
        c.drawPath(star, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(w * 0.055f);
        p.setLetterSpacing(0.25f);
        c.drawText("NOVA", cx, r.bottom - r.height() * 0.08f, p);
        p.setLetterSpacing(0f);
        // camera module
        float m = w * 0.07f;
        switch (model.camera) {
            case PhoneModels.CAM_TRIPLE: {
                float size = w * 0.5f;
                RectF mod = new RectF(r.left + m, r.top + m, r.left + m + size, r.top + m + size);
                module(c, mod, size * 0.28f, body);
                float lr = size * 0.19f;
                lens(c, mod.left + size * 0.29f, mod.top + size * 0.28f, lr);
                lens(c, mod.left + size * 0.29f, mod.top + size * 0.72f, lr);
                lens(c, mod.left + size * 0.72f, mod.top + size * 0.5f, lr);
                flash(c, mod.left + size * 0.74f, mod.top + size * 0.17f, size * 0.06f);
                p.setColor(0xFF111111);
                c.drawCircle(mod.left + size * 0.74f, mod.top + size * 0.84f, size * 0.045f, p);  // depth sensor
                break;
            }
            case PhoneModels.CAM_DUAL: {
                float mw = w * 0.24f, mh = w * 0.46f;
                RectF mod = new RectF(r.left + m, r.top + m, r.left + m + mw, r.top + m + mh);
                module(c, mod, mw / 2f, body);
                float lr = mw * 0.36f;
                lens(c, mod.centerX(), mod.top + mw / 2f, lr);
                lens(c, mod.centerX(), mod.bottom - mw / 2f, lr);
                flash(c, mod.right + w * 0.06f, mod.top + mw * 0.4f, w * 0.025f);
                break;
            }
            case PhoneModels.CAM_BAR: {
                float bh = w * 0.25f;
                RectF mod = new RectF(r.left - 1, r.top + r.height() * 0.12f, r.right + 1, r.top + r.height() * 0.12f + bh);
                p.setShader(new LinearGradient(0, mod.top, 0, mod.bottom, 0xFF2A2B30, 0xFF0E0F12, Shader.TileMode.CLAMP));
                c.drawRoundRect(mod, bh / 2f, bh / 2f, p);
                p.setShader(null);
                float lr = bh * 0.3f;
                lens(c, r.left + w * 0.22f, mod.centerY(), lr);
                lens(c, r.left + w * 0.42f, mod.centerY(), lr);
                lens(c, r.left + w * 0.62f, mod.centerY(), lr * 0.8f);
                flash(c, r.left + w * 0.8f, mod.centerY(), lr * 0.35f);
                break;
            }
            default: {
                lens(c, r.left + m + w * 0.12f, r.top + m + w * 0.12f, w * 0.1f);
                flash(c, r.left + m + w * 0.32f, r.top + m + w * 0.08f, w * 0.03f);
                break;
            }
        }
    }

    /** Raised glass plate that holds the lenses. */
    private void module(Canvas c, RectF mod, float rad, int body) {
        p.setColor(0x55000000);
        p.setMaskFilter(new BlurMaskFilter(mod.width() * 0.06f, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new RectF(mod.left + 3, mod.top + 6, mod.right + 3, mod.bottom + 6), rad, rad, p);
        p.setMaskFilter(null);
        p.setShader(new LinearGradient(mod.left, mod.top, mod.right, mod.bottom, lighter(body, 0.28f), darker(body, 0.88f), Shader.TileMode.CLAMP));
        c.drawRoundRect(mod, rad, rad, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, mod.width() * 0.012f));
        p.setColor(0x40FFFFFF);
        c.drawRoundRect(mod, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A camera lens: metal ring, dark glass, blue and violet reflections. */
    private void lens(Canvas c, float x, float y, float rr) {
        p.setShader(new LinearGradient(x - rr, y - rr, x + rr, y + rr, 0xFFCFD3DA, 0xFF4A4E57, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr, p);
        p.setShader(null);
        p.setColor(0xFF0A0B0F);
        c.drawCircle(x, y, rr * 0.84f, p);
        p.setShader(new RadialGradient(x, y, rr * 0.62f, new int[] {0xFF0B1230, 0xFF13204F, 0xFF050608}, new float[] {0f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr * 0.62f, p);
        p.setShader(null);
        p.setColor(0x887C8CFF);
        c.drawCircle(x - rr * 0.2f, y - rr * 0.22f, rr * 0.16f, p);
        p.setColor(0x66C084FC);
        c.drawCircle(x + rr * 0.18f, y + rr * 0.16f, rr * 0.08f, p);
        p.setColor(0xCCFFFFFF);
        c.drawCircle(x - rr * 0.28f, y - rr * 0.3f, rr * 0.06f, p);
    }

    private void flash(Canvas c, float x, float y, float rr) {
        p.setShader(new RadialGradient(x, y, rr, 0xFFFFF4D6, 0xFFE0C98A, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr, p);
        p.setShader(null);
    }

    static int lighter(int color, float f) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.argb(Color.alpha(color), (int) (r + (255 - r) * f), (int) (g + (255 - g) * f), (int) (b + (255 - b) * f));
    }

    static int darker(int color, float f) {
        return Color.argb(Color.alpha(color), (int) (Color.red(color) * f), (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }

    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
    }
}
