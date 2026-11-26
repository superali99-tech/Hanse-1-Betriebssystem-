package org.novaos.mobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Editing a photo in Nova Galerie: crop (free, 1:1, 4:3, 16:9), rotate, mirror, brightness,
 * contrast, saturation, warmth and filters. The original stays - the result is saved as a
 * copy ("…_bearbeitet.jpg").
 */
public class PhotoEditActivity extends Activity {
    static final String EXTRA_URI = "uri", EXTRA_NAME = "name";
    private Bitmap work;
    private ImageView preview;
    private CropView crop;
    private FrameLayout tools;
    private final float[] adjust = new float[4];  // brightness, contrast, saturation, warmth (-1..1)
    private int filter;
    private final LinearLayout[] tabs = new LinearLayout[1];
    private final Runnable openCrop = () -> {
        mark(this.openCrop);
        cropTools();
    }, openRotate = () -> {
        mark(this.openRotate);
        rotateTools();
    }, openAdjust = () -> {
        mark(this.openAdjust);
        adjustTools();
    }, openFilter = () -> {
        mark(this.openFilter);
        filterTools();
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.immersive(this, true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        // top: cancel, title, save
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 8));
        TextView cancel = Ui.text(this, "✕", 22, Color.WHITE, false);
        cancel.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        cancel.setContentDescription(getString(R.string.cancel));
        cancel.setOnClickListener(v -> finish());
        top.addView(cancel);
        TextView title = Ui.text(this, getString(R.string.edit_title), 18, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView save = Ui.text(this, getString(R.string.edit_save), 16, 0xFFFFD36B, true);
        save.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        save.setOnClickListener(v -> save());
        top.addView(save);
        root.addView(top);

        FrameLayout stage = new FrameLayout(this);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stage.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        crop = new CropView(this);
        crop.setVisibility(View.GONE);
        stage.addView(crop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        sl.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        root.addView(stage, sl);

        tools = new FrameLayout(this);
        tools.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 4));
        root.addView(tools, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)));
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(0, 0, 0, Ui.dp(this, 18));
        tabs[0] = tabRow;
        tab(tabRow, "✂", R.string.edit_crop, openCrop);
        tab(tabRow, "↻", R.string.edit_rotate, openRotate);
        tab(tabRow, "☀", R.string.edit_adjust, openAdjust);
        tab(tabRow, "✨", R.string.edit_filter, openFilter);
        root.addView(tabRow);
        setContentView(root);

        Uri uri = getIntent().getParcelableExtra(EXTRA_URI);
        if (uri == null) {
            finish();
            return;
        }
        new Thread(() -> {
            Bitmap b = Media.picture(getApplicationContext(), uri, 2560);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (b == null) {
                    Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                work = b.isMutable() ? b : b.copy(Bitmap.Config.ARGB_8888, true);
                showWork();
                openAdjust.run();
            });
        }).start();
    }

    private void tab(LinearLayout row, String icon, int label, Runnable open) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        TextView i = Ui.text(this, icon, 20, Color.WHITE, false), l = Ui.text(this, getString(label), 12, 0xCCFFFFFF, false);
        i.setGravity(Gravity.CENTER);
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(true);
        t.addView(i, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setTag(open);
        t.setOnClickListener(v -> {
            if (work != null) open.run();
        });
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    /** The open tool's tab in yellow, the others white. */
    private void mark(Runnable open) {
        LinearLayout row = tabs[0];
        for (int k = 0; k < row.getChildCount(); k++) {
            LinearLayout t = (LinearLayout) row.getChildAt(k);
            boolean on = t.getTag() == open;
            ((TextView) t.getChildAt(0)).setTextColor(on ? 0xFFFFD36B : Color.WHITE);
            ((TextView) t.getChildAt(1)).setTextColor(on ? 0xFFFFD36B : 0xCCFFFFFF);
        }
    }

    private float[] colours() {
        float[] m = Zoom.filter(filter);
        m = Zoom.concat(m, Zoom.brightness(adjust[0]));
        m = Zoom.concat(m, Zoom.contrast(adjust[1]));
        m = Zoom.concat(m, Zoom.saturation(adjust[2]));
        return Zoom.concat(m, Zoom.warmth(adjust[3]));
    }

    private void showWork() {
        preview.setImageBitmap(work);
        preview.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(colours())));
        preview.setVisibility(View.VISIBLE);
        crop.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------ tools

    private LinearLayout panel() {
        tools.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        tools.addView(box);
        return box;
    }

    private TextView chip(String text, boolean on, View.OnClickListener click) {
        TextView c = Ui.text(this, text, 14, on ? Color.BLACK : Color.WHITE, true);
        c.setBackgroundResource(R.drawable.pill_bg);
        if (on) c.getBackground().setTint(0xFFFFD36B);
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
        c.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        c.setLayoutParams(lp);
        return c;
    }

    private void cropTools() {
        showWork();
        preview.setVisibility(View.INVISIBLE);
        crop.setVisibility(View.VISIBLE);
        crop.set(work, colours());
        LinearLayout box = panel();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        String[] names = {getString(R.string.edit_free), "1:1", "4:3", "16:9", "9:16"};
        float[] ratios = {0, 1, 4 / 3f, 16 / 9f, 9 / 16f};
        for (int i = 0; i < names.length; i++) {
            float r = ratios[i];
            row.addView(chip(names[i], crop.ratio == r, v -> {
                crop.setRatio(r);
                cropTools();
            }));
        }
        hs.addView(row);
        box.addView(hs);
        TextView apply = chip("✓  " + getString(R.string.edit_apply), true, v -> {
            RectF r = crop.bitmapRect();
            if (r != null && r.width() >= 8 && r.height() >= 8) {
                work = Bitmap.createBitmap(work, Math.round(r.left), Math.round(r.top), Math.round(r.width()), Math.round(r.height()));
            }
            showWork();
            openAdjust.run();
        });
        LinearLayout.LayoutParams al = (LinearLayout.LayoutParams) apply.getLayoutParams();
        al.topMargin = Ui.dp(this, 14);
        box.addView(apply);
    }

    private void rotateTools() {
        showWork();
        LinearLayout box = panel();
        LinearLayout row = new LinearLayout(this);
        String[] labels = {"↻  " + getString(R.string.edit_rotate_right), "↺  " + getString(R.string.edit_rotate_left), "⇋  " + getString(R.string.edit_mirror)};
        View.OnClickListener[] does = {v -> transform(90, false), v -> transform(-90, false), v -> transform(0, true)};
        for (int i = 0; i < 3; i++) {
            TextView c = chip(labels[i], false, does[i]);
            c.setSingleLine(true);
            c.setGravity(Gravity.CENTER);
            c.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = i < 2 ? Ui.dp(this, 8) : 0;
            row.addView(c, lp);
        }
        box.addView(row);
    }

    private void transform(int degrees, boolean mirror) {
        Matrix m = new Matrix();
        if (degrees != 0) m.postRotate(degrees);
        if (mirror) m.preScale(-1, 1);
        work = Bitmap.createBitmap(work, 0, 0, work.getWidth(), work.getHeight(), m, true);
        showWork();
    }

    private void adjustTools() {
        showWork();
        LinearLayout box = panel();
        int[] names = {R.string.edit_brightness, R.string.edit_contrast, R.string.edit_saturation, R.string.edit_warmth};
        for (int i = 0; i < 4; i++) {
            int k = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.text(this, getString(names[i]), 13, Color.WHITE, false), new LinearLayout.LayoutParams(Ui.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
            SeekBar s = new SeekBar(this);
            s.setMax(200);
            s.setProgress(Math.round(adjust[i] * 100) + 100);
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar b, int p, boolean user) {
                    adjust[k] = (p - 100) / 100f;
                    preview.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(colours())));
                }

                @Override
                public void onStartTrackingTouch(SeekBar b) {}

                @Override
                public void onStopTrackingTouch(SeekBar b) {}
            });
            row.addView(s, new LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1));
            box.addView(row);
        }
    }

    private void filterTools() {
        showWork();
        LinearLayout box = panel();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        int[] names = {R.string.edit_f0, R.string.edit_f1, R.string.edit_f2, R.string.edit_f3, R.string.edit_f4, R.string.edit_f5, R.string.edit_f6};
        Bitmap thumb = Bitmap.createScaledBitmap(work, Ui.dp(this, 64), Math.max(1, Ui.dp(this, 64) * work.getHeight() / Math.max(1, work.getWidth())), true);
        for (int i = 0; i < names.length; i++) {
            int f = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(thumb);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(Zoom.filter(i))));
            if (i == filter) iv.setBackgroundColor(0xFFFFD36B);
            int pad = Ui.dp(this, 2);
            iv.setPadding(pad, pad, pad, pad);
            cell.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
            cell.addView(Ui.text(this, getString(names[i]), 12, i == filter ? 0xFFFFD36B : Color.WHITE, i == filter));
            cell.setOnClickListener(v -> {
                filter = f;
                filterTools();
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.rightMargin = Ui.dp(this, 10);
            row.addView(cell, cl);
        }
        hs.addView(row);
        box.addView(hs);
    }

    /** The result with all changes, as a copy next to the camera's photos. */
    private void save() {
        if (work == null) return;
        Bitmap out = Bitmap.createBitmap(work.getWidth(), work.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(colours())));
        new Canvas(out).drawBitmap(work, 0, 0, p);
        String name = MediaSave.copyName(getIntent().getStringExtra(EXTRA_NAME), getString(R.string.edit_suffix), "jpg");
        new Thread(() -> {
            String error = null;
            try {
                MediaSave.save(getApplicationContext(), name, false, o -> out.compress(Bitmap.CompressFormat.JPEG, 95, o));
            } catch (java.io.IOException | RuntimeException e) {
                error = e.getMessage();
            }
            String err = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                Toast.makeText(this, err == null ? getString(R.string.edit_saved) : getString(R.string.files_failed) + " " + err, Toast.LENGTH_LONG).show();
                if (err == null) {
                    setResult(RESULT_OK);
                    finish();
                }
            });
        }).start();
    }

    /** The crop frame: drag a corner or the whole frame; the outside is darkened. */
    static final class CropView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bmp;
        private ColorMatrixColorFilter colours;
        private final RectF image = new RectF(), frame = new RectF();
        float ratio;
        private int drag = -1;  // 0..3 corners, 4 = move
        private float lx, ly;

        CropView(Context c) {
            super(c);
        }

        void set(Bitmap b, float[] m) {
            bmp = b;
            colours = new ColorMatrixColorFilter(new ColorMatrix(m));
            frame.setEmpty();
            invalidate();
        }

        void setRatio(float r) {
            ratio = r;
            frame.setEmpty();
            invalidate();
        }

        private void layoutImage() {
            if (bmp == null || getWidth() == 0) return;
            float s = Math.min(getWidth() / (float) bmp.getWidth(), getHeight() / (float) bmp.getHeight());
            float w = bmp.getWidth() * s, h = bmp.getHeight() * s;
            image.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
            if (frame.isEmpty()) {
                frame.set(image);
                if (ratio > 0) {
                    float fw = image.width(), fh = fw / ratio;
                    if (fh > image.height()) {
                        fh = image.height();
                        fw = fh * ratio;
                    }
                    frame.set(image.centerX() - fw / 2, image.centerY() - fh / 2, image.centerX() + fw / 2, image.centerY() + fh / 2);
                }
            }
        }

        /** The frame in the bitmap's pixels. */
        RectF bitmapRect() {
            if (bmp == null || image.isEmpty()) return null;
            float s = bmp.getWidth() / image.width();
            RectF r = new RectF((frame.left - image.left) * s, (frame.top - image.top) * s, (frame.right - image.left) * s, (frame.bottom - image.top) * s);
            r.intersect(0, 0, bmp.getWidth(), bmp.getHeight());
            return r;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (bmp == null) return;
            layoutImage();
            p.setColorFilter(colours);
            c.drawBitmap(bmp, null, image, p);
            p.setColorFilter(null);
            p.setColor(0x99000000);
            c.drawRect(0, 0, getWidth(), frame.top, p);
            c.drawRect(0, frame.bottom, getWidth(), getHeight(), p);
            c.drawRect(0, frame.top, frame.left, frame.bottom, p);
            c.drawRect(frame.right, frame.top, getWidth(), frame.bottom, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.WHITE);
            p.setStrokeWidth(Ui.dp(getContext(), 2));
            c.drawRect(frame, p);
            p.setStrokeWidth(Ui.dp(getContext(), 1) * 0.6f);
            p.setColor(0x80FFFFFF);
            for (int i = 1; i < 3; i++) {  // the rule of thirds
                float x = frame.left + frame.width() * i / 3, y = frame.top + frame.height() * i / 3;
                c.drawLine(x, frame.top, x, frame.bottom, p);
                c.drawLine(frame.left, y, frame.right, y, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            float r = Ui.dp(getContext(), 7);
            for (float[] pt : corners()) c.drawCircle(pt[0], pt[1], r, p);
        }

        private float[][] corners() {
            return new float[][] {{frame.left, frame.top}, {frame.right, frame.top}, {frame.left, frame.bottom}, {frame.right, frame.bottom}};
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY(), grab = Ui.dp(getContext(), 32), min = Ui.dp(getContext(), 48);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    drag = -1;
                    float[][] cs = corners();
                    for (int i = 0; i < 4; i++) if (Math.abs(x - cs[i][0]) < grab && Math.abs(y - cs[i][1]) < grab) drag = i;
                    if (drag < 0 && frame.contains(x, y)) drag = 4;
                    lx = x;
                    ly = y;
                    return drag >= 0;
                case MotionEvent.ACTION_MOVE:
                    float dx = x - lx, dy = y - ly;
                    lx = x;
                    ly = y;
                    if (drag == 4) {
                        dx = Zoom.clamp(dx, image.left - frame.left, image.right - frame.right);
                        dy = Zoom.clamp(dy, image.top - frame.top, image.bottom - frame.bottom);
                        frame.offset(dx, dy);
                    } else if (drag >= 0) {
                        boolean left = drag == 0 || drag == 2, topSide = drag < 2;
                        if (left) frame.left = Zoom.clamp(frame.left + dx, image.left, frame.right - min);
                        else frame.right = Zoom.clamp(frame.right + dx, frame.left + min, image.right);
                        if (ratio > 0) {
                            float h = frame.width() / ratio;
                            if (topSide) frame.top = Math.max(image.top, frame.bottom - h);
                            else frame.bottom = Math.min(image.bottom, frame.top + h);
                            float w = frame.height() * ratio;  // fits again after hitting an edge
                            if (left) frame.left = frame.right - w;
                            else frame.right = frame.left + w;
                        } else {
                            if (topSide) frame.top = Zoom.clamp(frame.top + dy, image.top, frame.bottom - min);
                            else frame.bottom = Zoom.clamp(frame.bottom + dy, frame.top + min, image.bottom);
                        }
                    }
                    invalidate();
                    return true;
                default:
                    drag = -1;
                    return true;
            }
        }
    }
}
