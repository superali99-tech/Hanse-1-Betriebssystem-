package org.novaos.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Plays a Nova Filme video inside NovaOS TV with the official embedded YouTube player
 * (IFrame Player API in a WebView). Remote control: OK / play-pause = pause or continue,
 * left/right = 10 seconds back/forward, up/down = info bar, Back = end. The NovaOS info
 * bar is shown below the player, never on top of it.
 */
public class VideoActivity extends Activity {
    static final String EXTRA_ID = "id", EXTRA_TITLE = "title", EXTRA_SOURCE = "source";
    /** app ID as HTTP Referer: YouTube requires apps to identify themselves this way */
    private static final String BASE_URL = "https://org.novaos.tv/";
    private static final long POLL_MS = 500, BAR_MS = 4000;
    private static final int SEEK_S = 10;
    private static final int ENDED = 0, PLAYING = 1, PAUSED = 2, BUFFERING = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private View bar;
    private TextView info, state;
    private ProgressBar progress;
    private String videoId, source;
    private int playerState = -2, playerError = 0, loggedError = 0;
    private double position, duration;
    private long barUntil;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (web == null) return;
            web.evaluateJavascript("typeof novaStatus==='function'?novaStatus():''", VideoActivity.this::onStatus);
            ui.postDelayed(this, POLL_MS);
        }
    };

    static void start(Activity a, Video v) {
        a.startActivity(new Intent(a, VideoActivity.class)
                .putExtra(EXTRA_ID, v.id)
                .putExtra(EXTRA_TITLE, Catalog.cleanTitle(v.title, v.source.name))
                .putExtra(EXTRA_SOURCE, v.source.name));
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        videoId = getIntent().getStringExtra(EXTRA_ID);
        source = getIntent().getStringExtra(EXTRA_SOURCE);
        if (videoId == null || !Video.VIDEO_ID.matcher(videoId).matches()) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video);
        bar = findViewById(R.id.bar);
        info = findViewById(R.id.video_info);
        state = findViewById(R.id.video_state);
        progress = findViewById(R.id.video_progress);
        ((TextView) findViewById(R.id.video_title)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        info.setText(source);
        state.setText(R.string.loading);

        try {
            web = new WebView(this);
        } catch (RuntimeException e) {
            // no WebView on this TV (Android System WebView missing or disabled)
            Toast.makeText(this, R.string.video_no_webview, Toast.LENGTH_LONG).show();
            Actions.youtube(this, "https://www.youtube.com/watch?v=" + videoId);
            finish();
            return;
        }
        // keys go to this activity (remote control), not into the web page
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setBackgroundColor(0xFF000000);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);  // the user started the video with OK
        ws.setSupportMultipleWindows(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);  // no grey play icon
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // links in the player (YouTube logo, title) open in the YouTube app
                Uri u = request.getUrl();
                if (request.isForMainFrame() && u != null && u.toString().startsWith("http")) {
                    Actions.youtube(VideoActivity.this, u.toString());
                    return true;
                }
                return false;
            }
        });
        ((FrameLayout) findViewById(R.id.player_box)).addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String html = page().replace("{{VIDEO_ID}}", videoId)
                .replace("{{ORIGIN}}", BASE_URL.substring(0, BASE_URL.length() - 1))
                .replace("{{LANG}}", Locale.getDefault().getLanguage().replaceAll("[^a-z]", ""));
        web.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null);
        showBar();
    }

    private String page() {
        try (InputStream in = getResources().openRawResource(R.raw.youtube_player)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            ui.removeCallbacks(poll);
            ui.post(poll);
        }
    }

    @Override
    protected void onPause() {
        // no playback in the background (YouTube rules): pause when the app is left
        if (web != null) {
            web.evaluateJavascript("typeof novaPause==='function'&&novaPause()", null);
            web.onPause();
        }
        ui.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------ status from the page
    private void onStatus(String result) {
        if (result == null || result.length() < 3) return;
        try {
            Object v = new JSONTokener(result).nextValue();  // evaluateJavascript returns a JSON string
            if (!(v instanceof String)) return;
            JSONObject o = new JSONObject((String) v);
            playerState = o.optInt("state", -2);
            playerError = o.optInt("error", 0);
            position = o.optDouble("t", 0);
            duration = o.optDouble("d", 0);
        } catch (Exception e) {
            return;
        }
        showStatus();
    }

    private void showStatus() {
        if (duration > 0) {
            info.setText(source + "  ·  " + Video.formatTime(position) + " / " + Video.formatTime(duration));
            progress.setProgress((int) Math.min(1000, position * 1000 / duration));
        }
        if (playerError != 0) {
            if (playerError != loggedError) {
                Log.w("NovaOS-TV", "YouTube player error " + playerError + " for " + videoId);
                loggedError = playerError;
            }
            // 101/150: not allowed here - usually "only in Germany" (embedding was checked
            // before), 153: player could not identify the app
            state.setText(playerError == 101 || playerError == 150 || playerError == 153
                    ? getString(R.string.video_blocked, playerError) : getString(R.string.video_error, playerError));
            bar.setVisibility(View.VISIBLE);
            return;
        }
        switch (playerState) {
            case PLAYING:
                state.setText("");
                if (System.currentTimeMillis() > barUntil) bar.setVisibility(View.GONE);
                break;
            case PAUSED:
                state.setText(R.string.video_paused);
                bar.setVisibility(View.VISIBLE);
                break;
            case BUFFERING:
                state.setText(R.string.loading);
                break;
            case ENDED:
                state.setText(R.string.video_ended);
                bar.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    /** Shows the info bar (the player becomes smaller, nothing covers it) for a few seconds. */
    private void showBar() {
        barUntil = System.currentTimeMillis() + BAR_MS;
        bar.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------ remote control
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (web == null) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (event.getRepeatCount() > 0) return true;
                if (playerError != 0) {
                    Actions.youtube(this, "https://www.youtube.com/watch?v=" + videoId);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    web.evaluateJavascript("player&&player.playVideo()", null);
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    web.evaluateJavascript("novaPause()", null);
                } else {
                    web.evaluateJavascript("novaToggle()", null);
                }
                showBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-SEEK_S);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(SEEK_S);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_MENU:
                showBar();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void seek(int seconds) {
        web.evaluateJavascript("novaSeek(" + seconds + ")", null);
        if (duration > 0) {  // show the new position right away
            position = Math.max(0, Math.min(duration, position + seconds));
            showStatus();
        }
        showBar();
    }
}
