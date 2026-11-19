package org.novaos.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.novaos.tv.Video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Plays a Nova Filme video inside NovaOS Mobile with the official embedded YouTube player
 * (touch controls of the YouTube player). Portrait: player on top, NovaOS info below;
 * turned sideways: the player fills the screen. Nothing is drawn over the player.
 */
public class VideoActivity extends Activity {
    static final String EXTRA_ID = "id", EXTRA_TITLE = "title", EXTRA_SOURCE = "source";
    /** app ID as HTTP Referer: YouTube requires apps to identify themselves this way */
    private static final String BASE_URL = "https://org.novaos.mobile/";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private View playerBox, info;
    private TextView source, state;
    private String videoId, sourceName;
    private int loggedError;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (web == null) return;
            web.evaluateJavascript("typeof novaStatus==='function'?novaStatus():''", VideoActivity.this::onStatus);
            ui.postDelayed(this, 700);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        videoId = getIntent().getStringExtra(EXTRA_ID);
        sourceName = getIntent().getStringExtra(EXTRA_SOURCE);
        if (videoId == null || !Video.VIDEO_ID.matcher(videoId).matches()) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video);
        playerBox = findViewById(R.id.player_box);
        info = findViewById(R.id.info);
        source = findViewById(R.id.video_source);
        state = findViewById(R.id.video_state);
        ((TextView) findViewById(R.id.video_title)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        source.setText(getString(R.string.video_source, sourceName));
        findViewById(R.id.close).setOnClickListener(v -> finish());
        findViewById(R.id.open_youtube).setOnClickListener(v ->
                Launch.youtube(this, "https://www.youtube.com/watch?v=" + videoId));
        try {
            web = new WebView(this);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.video_no_webview, Toast.LENGTH_LONG).show();
            Launch.youtube(this, "https://www.youtube.com/watch?v=" + videoId);
            finish();
            return;
        }
        web.setBackgroundColor(0xFF000000);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);  // the user tapped the video
        ws.setSupportMultipleWindows(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();  // links in the player (YouTube logo) open in the YouTube app
                if (request.isForMainFrame() && u != null && u.toString().startsWith("http")) {
                    Launch.youtube(VideoActivity.this, u.toString());
                    return true;
                }
                return false;
            }
        });
        ((FrameLayout) playerBox).addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String html = page().replace("{{VIDEO_ID}}", videoId)
                .replace("{{ORIGIN}}", BASE_URL.substring(0, BASE_URL.length() - 1))
                .replace("{{LANG}}", Locale.getDefault().getLanguage().replaceAll("[^a-z]", ""));
        web.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null);
        layout();
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
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        layout();
    }

    /** Portrait: 16:9 player below the status bar, info below; landscape: player only. */
    private void layout() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        Ui.immersive(this, landscape);
        info.setVisibility(landscape ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) playerBox.getLayoutParams();
        if (landscape) {
            lp.height = 0;
            lp.weight = 1;
            playerBox.setPadding(0, 0, 0, 0);
        } else {
            int w = getResources().getDisplayMetrics().widthPixels;
            int top = statusBarHeight();
            lp.height = w * 9 / 16 + top;
            lp.weight = 0;
            playerBox.setPadding(0, top, 0, 0);
        }
        playerBox.setLayoutParams(lp);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
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
        // no playback in the background (YouTube rules)
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

    private void onStatus(String result) {
        if (result == null || result.length() < 3) return;
        int error;
        double t, d;
        try {
            Object v = new JSONTokener(result).nextValue();
            if (!(v instanceof String)) return;
            JSONObject o = new JSONObject((String) v);
            error = o.optInt("error", 0);
            t = o.optDouble("t", 0);
            d = o.optDouble("d", 0);
        } catch (Exception e) {
            return;
        }
        if (d > 0) source.setText(getString(R.string.video_source, sourceName) + "\n"
                + Video.formatTime(t) + " / " + Video.formatTime(d));
        if (error != 0 && error != loggedError) {
            loggedError = error;
            state.setText(error == 101 || error == 150 || error == 153
                    ? getString(R.string.video_blocked, error) : getString(R.string.video_error, error));
            state.setVisibility(View.VISIBLE);
            findViewById(R.id.open_youtube).setVisibility(View.VISIBLE);
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                info.setVisibility(View.VISIBLE);  // show the message also when turned
            }
        }
    }
}
