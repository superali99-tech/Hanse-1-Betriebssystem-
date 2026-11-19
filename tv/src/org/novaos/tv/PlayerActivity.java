package org.novaos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
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

import java.util.List;
import java.util.Locale;

/**
 * Live TV over the internet (HLS streams of the broadcasters), operated like a TV:
 * up/down or P+/P- change the channel, digits select a number, OK opens the
 * channel list, Back ends.
 */
public class PlayerActivity extends Activity implements Player.Listener {
    static final String EXTRA_INDEX = "index";
    /** how long the channel badge stays; how long to wait for a second digit */
    private static final long BADGE_MS = 4500, DIGITS_MS = 2500;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder digits = new StringBuilder();
    private List<Channel> channels;
    private int index, previous = -1;
    private ExoPlayer player;
    private SurfaceView surface;
    private View videoBox, shutter, badge, messageBox, listPanel;
    private TextView badgeNumber, badgeName, message;
    private ProgressBar spinner;
    private LinearLayout list;
    private ScrollView listScroll;
    private boolean error, audioOff;
    private int videoW, videoH;
    private float pixelRatio = 1f;

    private final Runnable hideBadge = () -> badge.setVisibility(View.GONE);
    private final Runnable applyDigits = this::applyDigits;

    /** Starts the player with the channel at this position of the list. */
    static void start(Activity a, int index) {
        a.startActivity(new Intent(a, PlayerActivity.class).putExtra(EXTRA_INDEX, index));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        messageBox = findViewById(R.id.message_box);
        message = findViewById(R.id.message);
        spinner = findViewById(R.id.spinner);
        listPanel = findViewById(R.id.list_panel);
        list = findViewById(R.id.list);
        listScroll = findViewById(R.id.list_scroll);
        videoBox.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitVideo());
        buildList();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (player != null) tune(Channel.step(channels.size(), intent.getIntExtra(EXTRA_INDEX, index), 0));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (channels.isEmpty()) return;
        HttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("NovaOS-TV")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new HlsMediaSource.Factory(http))
                .build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        // German main sound (not "Klare Sprache" / audio description), no subtitles
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

    private void tune(int i) {
        if (i != index) previous = index;
        index = i;
        Channel ch = channels.get(i);
        error = false;
        shutter.setVisibility(View.VISIBLE);
        messageBox.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        showBadge(String.valueOf(ch.number), ch.name);
        markInList();
        if (player == null) return;
        player.setMediaItem(MediaItem.fromUri(ch.url));
        player.prepare();
        player.play();
    }

    private void zap(int delta) {
        hideList();
        tune(Channel.step(channels.size(), index, delta));
    }

    private void showBadge(String number, String name) {
        badgeNumber.setText(number);
        badgeName.setText(name);
        badge.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideBadge);
        ui.postDelayed(hideBadge, BADGE_MS);
    }

    // ------------------------------------------------------------ player events
    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_BUFFERING) {
            if (!error) spinner.setVisibility(View.VISIBLE);
        } else {
            spinner.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPlayerError(PlaybackException e) {
        if (player != null && e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition();  // fell behind the live stream: jump to "now"
            player.prepare();
            return;
        }
        if (player != null && !audioOff && (e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                || e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED)) {
            // the TV's sound output refused the stream: keep the picture, without sound
            audioOff = true;
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
            player.prepare();
            player.play();
            Channel ch = channels.get(index);
            showBadge(String.valueOf(ch.number), ch.name + "  ·  " + getString(R.string.player_no_sound));
            return;
        }
        error = true;
        spinner.setVisibility(View.GONE);
        String name = channels.get(index).name;
        int code = httpStatus(e);
        String text;
        if (code == 403 || code == 451) {
            text = getString(R.string.player_geo, name);
        } else if (e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
            text = getString(R.string.player_offline);
        } else {
            text = getString(R.string.player_error, name, code > 0 ? "HTTP " + code : e.getErrorCodeName());
        }
        message.setText(text);
        messageBox.setVisibility(View.VISIBLE);
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
    public void onRenderedFirstFrame() {
        shutter.setVisibility(View.GONE);
    }

    @Override
    public void onVideoSizeChanged(VideoSize size) {
        videoW = size.width;
        videoH = size.height;
        pixelRatio = size.pixelWidthHeightRatio > 0 ? size.pixelWidthHeightRatio : 1f;
        fitVideo();
    }

    /** Keeps the picture's aspect ratio (black bars instead of stretching). */
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
            // numbers right-aligned with figure spaces, so the names line up
            t.setText(String.format(Locale.ROOT, "%2d", ch.number).replace(' ', '\u2007') + "   " + ch.name);
            t.setTextColor(getColor(R.color.text));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            t.setSingleLine(true);
            t.setBackgroundResource(R.drawable.list_item_bg);
            t.setFocusable(true);
            t.setClickable(true);
            int p = Tiles.dp(this, 14);
            t.setPadding(p, Tiles.dp(this, 9), p, Tiles.dp(this, 9));
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
        View current = list.getChildAt(index);
        if (current != null) {
            current.requestFocus();
            listScroll.post(() -> listScroll.smoothScrollTo(0,
                    Math.max(0, current.getTop() - listScroll.getHeight() / 2)));
        }
    }

    private void hideList() {
        listPanel.setVisibility(View.GONE);
    }

    private boolean listShown() {
        return listPanel.getVisibility() == View.VISIBLE;
    }

    // ------------------------------------------------------------ remote control
    private void applyDigits() {
        if (digits.length() == 0) return;
        int n = Integer.parseInt(digits.toString());
        digits.setLength(0);
        int i = Channel.indexOfNumber(channels, n);
        if (i >= 0) {
            hideList();
            tune(i);
        } else {
            showBadge(String.valueOf(n), getString(R.string.player_no_channel));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            if (digits.length() < 3) digits.append((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
            showBadge(digits.toString(), "");
            ui.removeCallbacks(applyDigits);
            ui.postDelayed(applyDigits, digits.length() >= 2 ? DIGITS_MS / 3 : DIGITS_MS);
            return true;
        }
        if (listShown()) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                hideList();
                return true;
            }
            return super.onKeyDown(keyCode, event);  // the list handles up/down/OK
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                zap(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                zap(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (error) tune(index); else showList();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_TV:
                showList();
                return true;
            case KeyEvent.KEYCODE_LAST_CHANNEL:
                if (previous >= 0) tune(previous);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_INFO:
                Channel ch = channels.get(index);
                showBadge(String.valueOf(ch.number), ch.name);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_INDEX, index);
    }
}
