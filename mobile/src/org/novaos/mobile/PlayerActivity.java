package org.novaos.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;

import org.novaos.tv.Channel;

import java.util.List;
import java.util.Locale;

/**
 * Live TV on the phone (same streams as NovaOS TV), operated by touch: tap = show
 * controls, swipe left/right = next/previous channel, list button = all channels.
 */
public class PlayerActivity extends Activity implements Player.Listener {
    static final String EXTRA_INDEX = "index";
    private static final long CONTROLS_MS = 4000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Channel> channels;
    private int index;
    private ExoPlayer player;
    private SurfaceView surface;
    private View videoBox, shutter, badge, controls, messageBox, listPanel;
    private TextView badgeNumber, badgeName, message;
    private ProgressBar spinner;
    private LinearLayout list;
    private ScrollView listScroll;
    private boolean error, audioOff;
    private int videoW, videoH;
    private float pixelRatio = 1f;
    private GestureDetector gestures;

    private final Runnable hideControls = () -> {
        badge.setVisibility(View.GONE);
        controls.setVisibility(View.GONE);
        Ui.immersive(this, true);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.immersive(this, true);
        setContentView(R.layout.activity_player);
        channels = Channels.get(this);
        if (channels.isEmpty()) {
            finish();
            return;
        }
        int start = state != null ? state.getInt(EXTRA_INDEX) : getIntent().getIntExtra(EXTRA_INDEX, 0);
        index = Channel.step(channels.size(), start, 0);
        videoBox = findViewById(R.id.video_box);
        surface = findViewById(R.id.surface);
        shutter = findViewById(R.id.shutter);
        badge = findViewById(R.id.badge);
        badgeNumber = findViewById(R.id.badge_number);
        badgeName = findViewById(R.id.badge_name);
        controls = findViewById(R.id.controls);
        messageBox = findViewById(R.id.message_box);
        message = findViewById(R.id.message);
        spinner = findViewById(R.id.spinner);
        listPanel = findViewById(R.id.list_panel);
        list = findViewById(R.id.list);
        listScroll = findViewById(R.id.list_scroll);
        videoBox.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitVideo());
        findViewById(R.id.prev).setOnClickListener(v -> zap(-1));
        findViewById(R.id.next).setOnClickListener(v -> zap(1));
        findViewById(R.id.list_button).setOnClickListener(v -> showList());
        findViewById(R.id.close).setOnClickListener(v -> finish());
        findViewById(R.id.retry).setOnClickListener(v -> tune(index));
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (listPanel.getVisibility() == View.VISIBLE) hideList(); else toggleControls();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vx) < Math.abs(vy) * 1.5f || Math.abs(vx) < 800) return false;
                zap(e2.getX() < e1.getX() ? 1 : -1);  // swipe left = next channel
                return true;
            }
        });
        // consume the whole gesture (also ACTION_DOWN), or taps and swipes never arrive
        View.OnTouchListener touch = (v, ev) -> {
            gestures.onTouchEvent(ev);
            return true;
        };
        videoBox.setOnTouchListener(touch);
        shutter.setOnTouchListener(touch);
        buildList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (channels.isEmpty()) return;
        HttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("NovaOS-Mobile")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new HlsMediaSource.Factory(http)).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguage("de")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build());
        player.setVideoSurfaceView(surface);
        player.addListener(this);
        audioOff = false;
        tune(index);
    }

    @Override
    protected void onStop() {
        ui.removeCallbacksAndMessages(null);
        if (player != null) {
            player.removeListener(this);
            player.release();
            player = null;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_INDEX, index);
    }

    @Override
    public void onBackPressed() {
        if (listPanel.getVisibility() == View.VISIBLE) hideList(); else super.onBackPressed();
    }

    private void tune(int i) {
        index = Channel.step(channels.size(), i, 0);
        Channel ch = channels.get(index);
        error = false;
        shutter.setVisibility(View.VISIBLE);
        messageBox.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        badgeNumber.setText(String.valueOf(ch.number));
        badgeName.setText(ch.name);
        showControls();
        markInList();
        if (player == null) return;
        player.setMediaItem(MediaItem.fromUri(ch.url));
        player.prepare();
        player.play();
    }

    private void zap(int delta) {
        hideList();
        tune(index + delta);
    }

    private void showControls() {
        badge.setVisibility(View.VISIBLE);
        controls.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideControls);
        ui.postDelayed(hideControls, CONTROLS_MS);
    }

    private void toggleControls() {
        if (controls.getVisibility() == View.VISIBLE) hideControls.run(); else showControls();
    }

    // ------------------------------------------------------------ player events
    @Override
    public void onPlaybackStateChanged(int state) {
        spinner.setVisibility(state == Player.STATE_BUFFERING && !error ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onRenderedFirstFrame() {
        shutter.setVisibility(View.GONE);
    }

    @Override
    public void onPlayerError(PlaybackException e) {
        if (player == null) return;
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition();
            player.prepare();
            return;
        }
        if (!audioOff && (e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                || e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED)) {
            audioOff = true;  // keep the picture if the sound output refuses the stream
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
            player.prepare();
            player.play();
            badgeName.setText(channels.get(index).name + "  ·  " + getString(R.string.player_no_sound));
            showControls();
            return;
        }
        error = true;
        spinner.setVisibility(View.GONE);
        String name = channels.get(index).name;
        int code = httpStatus(e);
        if (code == 403 || code == 451) {
            message.setText(getString(R.string.player_geo, name));
        } else if (e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
            message.setText(R.string.player_offline);
        } else {
            message.setText(getString(R.string.player_error, name, code > 0 ? "HTTP " + code : e.getErrorCodeName()));
        }
        messageBox.setVisibility(View.VISIBLE);
        showControls();
    }

    private static int httpStatus(Throwable t) {
        for (int i = 0; t != null && i < 6; i++, t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
            }
        }
        return 0;
    }

    @Override
    public void onVideoSizeChanged(VideoSize size) {
        videoW = size.width;
        videoH = size.height;
        pixelRatio = size.pixelWidthHeightRatio > 0 ? size.pixelWidthHeightRatio : 1f;
        fitVideo();
    }

    private void fitVideo() {
        int bw = videoBox.getWidth(), bh = videoBox.getHeight();
        if (bw == 0 || bh == 0 || videoW == 0 || videoH == 0) return;
        float aspect = videoW * pixelRatio / videoH;
        int w = bw, h = Math.round(bw / aspect);
        if (h > bh) {
            h = bh;
            w = Math.round(bh * aspect);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            lp.gravity = Gravity.CENTER;
            surface.setLayoutParams(lp);
        }
    }

    // ------------------------------------------------------------ channel list
    private void buildList() {
        for (int i = 0; i < channels.size(); i++) {
            Channel ch = channels.get(i);
            TextView t = new TextView(this);
            t.setText(String.format(Locale.ROOT, "%2d", ch.number).replace(' ', ' ') + "   " + ch.name);
            t.setTextColor(getColor(R.color.text));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            t.setSingleLine(true);
            t.setBackgroundResource(R.drawable.list_item_bg);
            int p = Ui.dp(this, 14);
            t.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
            final int pos = i;
            t.setOnClickListener(v -> {
                hideList();
                if (pos != index || error) tune(pos);
            });
            list.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void markInList() {
        for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).setSelected(i == index);
    }

    private void showList() {
        listPanel.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideControls);
        View current = list.getChildAt(index);
        if (current != null) {
            listScroll.post(() -> listScroll.scrollTo(0, Math.max(0, current.getTop() - listScroll.getHeight() / 2)));
        }
    }

    private void hideList() {
        listPanel.setVisibility(View.GONE);
    }
}
