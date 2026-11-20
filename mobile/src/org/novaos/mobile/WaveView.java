package org.novaos.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** The wave of a voice message; the part already played is bright. */
final class WaveView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private List<Integer> wave = new ArrayList<>();
    private float progress;
    private int played = 0xFFFFFFFF, rest = 0x66FFFFFF;

    WaveView(Context c) {
        super(c);
    }

    void set(List<Integer> wave, int played, int rest) {
        this.wave = wave == null || wave.isEmpty() ? flat() : wave;
        this.played = played;
        this.rest = rest;
        invalidate();
    }

    void progress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    private static List<Integer> flat() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < 40; i++) l.add(180 + (int) (Math.abs(Math.sin(i * 0.9)) * 300));
        return l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float w = 2.6f * d, gap = 1.8f * d;
        int bars = Math.max(1, (int) ((getWidth() + gap) / (w + gap)));
        float h = getHeight(), mid = h / 2f;
        for (int i = 0; i < bars; i++) {
            float level = Waveform.at(wave, i, bars) / 1024f;
            float bh = Math.max(3 * d, level * h);
            float x = i * (w + gap);
            bar.set(x, mid - bh / 2, x + w, mid + bh / 2);
            paint.setColor((i + 0.5f) / bars <= progress ? played : rest);
            canvas.drawRoundRect(bar, w / 2, w / 2, paint);
        }
    }
}
