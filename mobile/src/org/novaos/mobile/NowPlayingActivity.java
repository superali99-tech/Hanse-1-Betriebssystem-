package org.novaos.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The music playing, big (glass design): a background in the colours of the cover, the cover,
 * title and artist, the time bar and big back / play-pause / next buttons on glass.
 */
public class NowPlayingActivity extends Activity implements Music.Listener {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Music music;
    private FrameLayout root;
    private ImageView cover;
    private TextView title, artist, pos, len;
    private SeekBar seek;
    private ImageButton toggle;
    private boolean seeking;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        music = Music.get(this);
        root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            col.setPadding(insets.getSystemWindowInsetLeft() + Ui.dp(this, 28), insets.getSystemWindowInsetTop() + Ui.dp(this, 12),
                    insets.getSystemWindowInsetRight() + Ui.dp(this, 28), insets.getSystemWindowInsetBottom() + Ui.dp(this, 24));
            return insets.consumeSystemWindowInsets();
        });

        // close: the handle at the top (like pulling the player down)
        View handle = new View(this);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0x80FFFFFF);
        hb.setCornerRadius(Ui.dp(this, 3));
        handle.setBackground(hb);
        handle.setContentDescription(getString(R.string.back));
        handle.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 5));
        hl.bottomMargin = Ui.dp(this, 22);
        col.addView(handle, hl);

        int side = Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 56), Ui.dp(this, 340));
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable cb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFFFF5E8A, 0xFF8E6CFF});
        cb.setCornerRadius(Ui.dp(this, 26));
        cover.setBackground(cb);
        cover.setClipToOutline(true);
        cover.setElevation(Ui.dp(this, 16));
        col.addView(cover, new LinearLayout.LayoutParams(side, side));
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout glass = new LinearLayout(this);
        glass.setOrientation(LinearLayout.VERTICAL);
        glass.setBackground(HomeWidgets.glass(this, 0xFFFFFF));
        glass.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 16));
        title = Ui.text(this, "", 22, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        glass.addView(title);
        artist = Ui.text(this, "", 16, 0xCCFFFFFF, false);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        glass.addView(artist);
        seek = new SeekBar(this);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) pos.setText(MusicCatalog.time(p));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                music.seekTo(s.getProgress());
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 32));
        sl.topMargin = Ui.dp(this, 10);
        glass.addView(seek, sl);
        LinearLayout times = new LinearLayout(this);
        pos = Ui.text(this, "", 12, 0xB3FFFFFF, false);
        times.addView(pos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        len = Ui.text(this, "", 12, 0xB3FFFFFF, false);
        times.addView(len);
        times.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        glass.addView(times);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(button(R.drawable.ic_skip_prev, R.string.music_previous, 56, false, v -> music.previous()));
        toggle = button(R.drawable.ic_play_s, R.string.music_play, 76, true, v -> music.toggle());
        controls.addView(toggle);
        controls.addView(button(R.drawable.ic_skip_next, R.string.music_next, 56, false, v -> music.next()));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(this, 12);
        glass.addView(controls, cl);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32),
                Ui.dp(this, 520)), ViewGroup.LayoutParams.WRAP_CONTENT);
        col.addView(glass, gl);
        setContentView(root);
    }

    private ImageButton button(int icon, int label, int sizeDp, boolean big, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(big ? 0x59FFFFFF : 0x00000000);
        if (big) g.setStroke(Math.max(1, Ui.dp(this, 1)), 0x59FFFFFF);
        b.setBackground(g);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, big ? 20 : 14);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp));
        lp.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        music.addListener(this);
        musicChanged();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        music.removeListener(this);
        ui.removeCallbacks(tick);
        super.onPause();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            position();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    public void musicChanged() {
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            MusicCatalog.Track t = music.current();
            if (t == null) {
                finish();
                return;
            }
            String now = music.nowPlaying();
            title.setText(!now.isEmpty() ? now : t.title);
            artist.setText(!now.isEmpty() ? t.title : t.artist);
            Bitmap c = music.cover();
            if (c != null) {
                cover.setPadding(0, 0, 0, 0);
                cover.setImageBitmap(c);
            } else {
                int ip = Ui.dp(this, 70);
                cover.setPadding(ip, ip, ip, ip);
                cover.setImageResource(t.live() ? R.drawable.ic_radio : R.drawable.ic_music);
            }
            root.setBackground(backdrop(c));
            toggle.setImageResource(music.playing() ? R.drawable.ic_pause : R.drawable.ic_play_s);
            toggle.setContentDescription(getString(music.playing() ? R.string.music_pause : R.string.music_play));
            seek.setVisibility(t.live() ? View.INVISIBLE : View.VISIBLE);
            position();
        });
    }

    private void position() {
        MusicCatalog.Track t = music.current();
        if (t == null || seeking) return;
        if (t.live()) {
            pos.setText("● LIVE");
            len.setText("");
            return;
        }
        long d = music.duration();
        seek.setMax((int) Math.max(1, d));
        seek.setProgress((int) music.position());
        pos.setText(MusicCatalog.time(music.position()));
        len.setText(d > 0 ? MusicCatalog.time(d) : "");
    }

    /** The background: the cover's average colour, darker towards the bottom; without a cover purple to blue. */
    private static GradientDrawable backdrop(Bitmap c) {
        int base = 0xFF5B3FA8;
        if (c != null) {
            Bitmap small = Bitmap.createScaledBitmap(c, 8, 8, true);
            long r = 0, g = 0, b = 0;
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int px = small.getPixel(x, y);
                    r += Color.red(px);
                    g += Color.green(px);
                    b += Color.blue(px);
                }
            }
            base = Color.rgb((int) (r / 64), (int) (g / 64), (int) (b / 64));
        }
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.2f + 0.1f);
        hsv[2] = Math.max(0.35f, Math.min(0.75f, hsv[2]));
        int top = Color.HSVToColor(hsv);
        hsv[2] *= 0.35f;
        int bottom = Color.HSVToColor(hsv);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {top, bottom});
    }
}
