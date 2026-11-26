package org.novaos.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * A video in Nova Galerie that can be zoomed (two fingers, double tap): the picture on a
 * TextureView (a VideoView cannot be zoomed), below play/pause, the time bar and the time.
 */
final class VideoPane extends FrameLayout {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ZoomLayout zoom;
    private final TextureView view;
    private final ImageButton play;
    private final SeekBar seek;
    private final TextView time;
    private final LinearLayout controls;
    private MediaPlayer player;
    private Surface surface;
    private boolean seeking;
    private int videoW, videoH;
    long from = -1, to = -1;  // VideoTrimActivity: play only this part (ms)

    VideoPane(Context c, Uri uri, boolean autoplay) {
        super(c);
        zoom = new ZoomLayout(c);
        view = new TextureView(c);
        zoom.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        addView(zoom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        controls = new LinearLayout(c);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(0xB30B0E1F);
        controls.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
        play = new ImageButton(c);
        play.setImageResource(R.drawable.ic_play_s);
        play.setBackgroundResource(R.drawable.round_bg);
        play.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 11);
        play.setPadding(p, p, p, p);
        play.setContentDescription(c.getString(R.string.music_play));
        play.setOnClickListener(v -> toggle());
        controls.addView(play, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));
        seek = new SeekBar(c);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int pr, boolean user) {
                if (user) time.setText(MusicCatalog.time(pr));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                if (player != null) player.seekTo(s.getProgress());
            }
        });
        controls.addView(seek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        time = Ui.text(c, "", 13, Color.WHITE, false);
        controls.addView(time);
        addView(controls, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                surface = new Surface(st);
                open(uri, autoplay);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                release();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
    }

    boolean zoomed() {
        return zoom.zoomed();
    }

    void showControls(boolean on) {
        controls.setVisibility(on ? VISIBLE : GONE);
    }

    long duration() {
        return player == null ? 0 : player.getDuration();
    }

    void seekTo(long ms) {
        if (player != null) player.seekTo((int) ms);
    }

    private void open(Uri uri, boolean autoplay) {
        try {
            player = new MediaPlayer();
            player.setDataSource(getContext(), uri);
            player.setSurface(surface);
            player.setOnVideoSizeChangedListener((mp, w, h) -> {
                videoW = w;
                videoH = h;
                fit();
            });
            player.setOnCompletionListener(mp -> showPlaying());
            player.setOnPreparedListener(mp -> {
                seek.setMax(mp.getDuration());
                if (from > 0 || !autoplay) mp.seekTo((int) Math.max(1, from));  // shows the first picture before playing
                if (autoplay) mp.start();
                showPlaying();
                ui.post(tick);
            });
            player.prepareAsync();
        } catch (Exception e) {
            time.setText(R.string.files_failed);
        }
    }

    /** The video as big as possible without distorting it. */
    private void fit() {
        int w = getWidth(), h = getHeight();
        if (videoW == 0 || videoH == 0 || w == 0 || h == 0) return;
        float s = Math.min(w / (float) videoW, h / (float) videoH);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(videoW * s), Math.round(videoH * s), Gravity.CENTER);
        view.setLayoutParams(lp);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        post(this::fit);
    }

    void toggle() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else {
            if (to > 0 && player.getCurrentPosition() >= to - 50) player.seekTo((int) Math.max(0, from));
            player.start();
        }
        showPlaying();
    }

    void pause() {
        if (player != null && player.isPlaying()) player.pause();
        showPlaying();
    }

    private void showPlaying() {
        boolean on = player != null && player.isPlaying();
        play.setImageResource(on ? R.drawable.ic_pause : R.drawable.ic_play_s);
        play.setContentDescription(getContext().getString(on ? R.string.music_pause : R.string.music_play));
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            try {
                int pos = player.getCurrentPosition();
                if (to > 0 && pos >= to && player.isPlaying()) {
                    player.pause();
                    showPlaying();
                }
                if (!seeking) {
                    seek.setProgress(pos);
                    time.setText(MusicCatalog.time(pos) + " / " + MusicCatalog.time(player.getDuration()));
                }
            } catch (IllegalStateException ignored) {
                // released
            }
            ui.postDelayed(this, 300);
        }
    };

    void release() {
        ui.removeCallbacks(tick);
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
                // already gone
            }
            player = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }
}
