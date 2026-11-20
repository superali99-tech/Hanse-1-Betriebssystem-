package org.novaos.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Nova Chat call (voice or video) with one person, compatible with Matrix clients like
 * Element (m.call.* events, version 1). Audio/video: WebRTC in a WebView; connection
 * help: the TURN servers of the Matrix server.
 */
public class CallActivity extends Activity implements ChatSync.CallListener {
    static final String EXTRA_ROOM = "room", EXTRA_VIDEO = "video", EXTRA_INCOMING = "incoming", EXTRA_CALL_ID = "call_id",
            EXTRA_OFFER = "offer", EXTRA_PARTY = "party", EXTRA_ACCEPT = "accept", EXTRA_NAME = "name";
    private static final int PERMISSIONS = 31;
    private static final long RING_MS = 60_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private String roomId, callId, partyId, offer, me, peerParty;
    private boolean video, incoming, answered, connected, ended, pageReady, muted, speaker;
    private long connectedAt;
    private WebView web;
    private TextView status, timer;
    private LinearLayout actions;
    private MatrixApi api;
    private Ringtone ringtone;
    private ToneGenerator ringback;
    private AudioManager audio;
    private int audioMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.immersive(this, true);
        roomId = getIntent().getStringExtra(EXTRA_ROOM);
        if (roomId == null || !ChatDb.loggedIn(this)) {
            finish();
            return;
        }
        video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        incoming = getIntent().getBooleanExtra(EXTRA_INCOMING, false);
        callId = incoming ? getIntent().getStringExtra(EXTRA_CALL_ID) : "nova" + UUID.randomUUID().toString().replace("-", "");
        offer = getIntent().getStringExtra(EXTRA_OFFER);
        peerParty = getIntent().getStringExtra(EXTRA_PARTY);
        partyId = "nova" + Long.toHexString(System.nanoTime());
        me = ChatDb.me(this);
        api = ChatDb.api(this);
        audio = getSystemService(AudioManager.class);
        Calls.cancelRinging(this);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        ChatDb.RoomRow room = ChatDb.get(this).room(roomId);
        if (name == null) name = room != null ? room.name : "";
        buildUi(name);
        ChatSync.get(this).addCallListener(this);
        ChatSync.get(this).start();
        for (MatrixSync.CallEvent e : ChatSync.get(this).recentCalls(callId)) onCallEvent(e);  // e.g. candidates sent while ringing

        if (incoming && getIntent().getBooleanExtra(EXTRA_ACCEPT, false)) {
            accept();
        } else if (incoming) {
            ring();
        } else {
            status.setText(R.string.call_calling);
            askPermissionsThenStart();
        }
        ui.postDelayed(() -> {
            if (!answered && !ended) end(getString(incoming ? R.string.call_missed : R.string.call_no_answer), "invite_timeout");
        }, RING_MS);
    }

    @Override
    protected void onDestroy() {
        ChatSync.get(this).removeCallListener(this);
        if (!ended && roomId != null) hangUp("user_hangup");
        stopSounds();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // a call is ended with the red button
    }

    // ------------------------------------------------------------ screen

    private void buildUi(String name) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0E1F);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setVisibility(video ? View.VISIBLE : View.INVISIBLE);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_HORIZONTAL);
        info.setPadding(0, Ui.dp(this, 70), 0, 0);
        View avatar = ChatActivity.avatar(this, name, 112);
        info.addView(avatar);
        TextView n = Ui.text(this, name, 28, Color.WHITE, true);
        n.setGravity(Gravity.CENTER);
        n.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), 0);
        n.setShadowLayer(Ui.dp(this, 4), 0, 0, 0xCC000000);
        info.addView(n);
        status = Ui.text(this, "", 16, 0xFFD5DAF5, false);
        status.setGravity(Gravity.CENTER);
        status.setShadowLayer(Ui.dp(this, 4), 0, 0, 0xCC000000);
        info.addView(status);
        timer = Ui.text(this, "", 15, 0xFFD5DAF5, false);
        timer.setGravity(Gravity.CENTER);
        info.addView(timer);
        if (video) avatar.setVisibility(View.GONE);
        root.addView(info, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, 0, 0, Ui.dp(this, 56));
        root.addView(actions, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(root);
        setupWebView();
    }

    private ImageButton round(int icon, int color, int label, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(color);
        b.setBackground(bg);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 18);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68));
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 16);
        b.setLayoutParams(lp);
        return b;
    }

    private void showRingingButtons() {
        actions.removeAllViews();
        ImageButton decline = round(R.drawable.ic_call, 0xFFE5484D, R.string.call_decline, v -> decline());
        decline.setRotation(135);
        actions.addView(decline);
        actions.addView(round(video ? R.drawable.ic_video : R.drawable.ic_call, 0xFF30A46C, R.string.call_accept, v -> accept()));
    }

    private void showCallButtons() {
        actions.removeAllViews();
        ImageButton mute = round(R.drawable.ic_mic, 0x33FFFFFF, R.string.call_mute, null);
        mute.setOnClickListener(v -> {
            muted = !muted;
            mute.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
            js("mute(" + muted + ")");
        });
        actions.addView(mute);
        ImageButton hang = round(R.drawable.ic_call, 0xFFE5484D, R.string.call_hangup, v -> end(getString(R.string.call_ended), "user_hangup"));
        hang.setRotation(135);
        actions.addView(hang);
        ImageButton loud = round(R.drawable.ic_speaker, speaker ? 0x88FFFFFF : 0x33FFFFFF, R.string.call_speaker, null);
        loud.setOnClickListener(v -> {
            setSpeaker(!speaker);
            ((android.graphics.drawable.GradientDrawable) loud.getBackground()).setColor(speaker ? 0x88FFFFFF : 0x33FFFFFF);
        });
        actions.addView(loud);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Nova");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // only our own call page may use microphone/camera
                if ("org.novaos.mobile".equals(request.getOrigin().getHost())) runOnUiThread(() -> request.grant(request.getResources()));
                else request.deny();
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
            }
        });
        web.loadDataWithBaseURL("https://org.novaos.mobile/", raw(), "text/html", "utf-8", null);
    }

    private String raw() {
        try (InputStream in = getResources().openRawResource(R.raw.call)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void js(String code) {
        runOnUiThread(() -> {
            if (web == null) return;
            if (!pageReady) {
                ui.postDelayed(() -> js(code), 200);
                return;
            }
            web.evaluateJavascript(code, null);
        });
    }

    // ------------------------------------------------------------ call flow

    private void askPermissionsThenStart() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
        if (video && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.CAMERA);
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), PERMISSIONS);
            return;
        }
        startMedia();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != PERMISSIONS) return;
        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                end(getString(R.string.call_need_mic), incoming ? "user_hangup" : null);
                return;
            }
        }
        startMedia();
    }

    private void startMedia() {
        audioMode = audio.getMode();
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        setSpeaker(video);
        showCallButtons();
        if (!incoming) {
            ringback = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70);
            ringback.startTone(ToneGenerator.TONE_SUP_RINGTONE);
        }
        new Thread(() -> {
            String ice = iceServers();
            if (incoming) js("answer(" + config(ice) + "," + MiniJson.write(offer) + ")");
            else js("call(" + config(ice) + ")");
        }).start();
    }

    private String config(String ice) {
        return "{\"ice\":" + ice + ",\"video\":" + video + "}";
    }

    /** TURN servers of the Matrix server (for calls through routers), as WebRTC configuration. */
    private String iceServers() {
        List<Object> servers = new ArrayList<>();
        try {
            Object t = MiniJson.parse(api.turnServer());
            Object uris = MiniJson.get(t, "uris");
            if (uris instanceof List && !((List<?>) uris).isEmpty()) {
                servers.add(MiniJson.obj("urls", uris, "username", MiniJson.get(t, "username"), "credential", MiniJson.get(t, "password")));
            }
        } catch (IOException | RuntimeException ignored) {
            // without TURN only direct connections work
        }
        String host = MatrixSync.server(me);
        servers.add(MiniJson.obj("urls", new Object[] {"stun:turn." + ("matrix.org".equals(host) ? "matrix.org" : host)}));
        return MiniJson.write(servers);
    }

    private void ring() {
        status.setText(video ? R.string.call_incoming_video : R.string.call_incoming);
        showRingingButtons();
        try {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone != null) ringtone.play();
        } catch (RuntimeException ignored) {
            // silent
        }
        Vibrator v = getSystemService(Vibrator.class);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(new long[] {0, 600, 900}, 0));
        }
    }

    private void accept() {
        if (answered || ended) return;
        answered = true;
        stopSounds();
        status.setText(R.string.call_connecting);
        askPermissionsThenStart();
    }

    private void decline() {
        if (ended) return;
        ended = true;
        send("m.call.reject", MiniJson.obj());
        stopSounds();
        finish();
    }

    /** Sends a call event (call_id, party_id and version are always added). */
    private void send(String type, Map<String, Object> extra) {
        Map<String, Object> content = MiniJson.obj("call_id", callId, "party_id", partyId, "version", "1");
        content.putAll(extra);
        new Thread(() -> {
            try {
                api.sendEvent(roomId, type, content);
            } catch (IOException e) {
                if (type.equals("m.call.invite")) ui.post(() -> end(getString(R.string.call_failed, e.getMessage()), null));
            }
        }).start();
    }

    @Override
    public void onCallEvent(MatrixSync.CallEvent e) {
        if (!roomId.equals(e.roomId) || e.sender.equals(me) || !callId.equals(e.content.get("call_id"))) return;
        switch (e.type) {
            case "m.call.answer":
                if (incoming || answered) return;
                answered = true;
                peerParty = e.content.get("party_id") instanceof String ? (String) e.content.get("party_id") : null;
                stopSounds();
                status.setText(R.string.call_connecting);
                Object sdp = MiniJson.get(e.content, "answer", "sdp");
                if (sdp instanceof String) js("remoteAnswer(" + MiniJson.write(sdp) + ")");
                if (peerParty != null) send("m.call.select_answer", MiniJson.obj("selected_party_id", peerParty));
                break;
            case "m.call.candidates":
                Object list = e.content.get("candidates");
                if (list instanceof List) js("remoteCandidates(" + MiniJson.write(list) + ")");
                break;
            case "m.call.select_answer":
                if (incoming && !partyId.equals(e.content.get("selected_party_id"))) end(getString(R.string.call_elsewhere), null);
                break;
            case "m.call.hangup":
            case "m.call.reject":
                end(getString(answered ? R.string.call_ended : R.string.call_declined), null);
                break;
            default:
                break;
        }
    }

    private void onState(String s) {
        if (ended) return;
        if (s.equals("connected") || s.equals("completed")) {
            if (!connected) {
                connected = true;
                connectedAt = SystemClock.elapsedRealtime();
                status.setText(R.string.call_connected);
                stopSounds();
                tick();
            }
        } else if (s.equals("failed")) {
            end(getString(R.string.call_lost), "ice_failed");
        } else if (s.startsWith("failed:")) {
            end(getString(R.string.call_failed, s.substring(7)), incoming ? "user_hangup" : null);
        } else if (s.equals("disconnected") && connected) {
            status.setText(R.string.call_reconnecting);
        }
    }

    private void tick() {
        if (ended || !connected) return;
        long sec = (SystemClock.elapsedRealtime() - connectedAt) / 1000;
        timer.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60, sec % 60));
        ui.postDelayed(this::tick, 1000);
    }

    private void end(String message, String reason) {
        if (ended) return;
        ended = true;
        if (reason != null) hangUp(reason);
        js("hangup()");
        stopSounds();
        status.setText(message);
        actions.removeAllViews();
        ui.postDelayed(this::finish, 1600);
    }

    private void hangUp(String reason) {
        Map<String, Object> content = MiniJson.obj("call_id", callId, "party_id", partyId, "version", "1", "reason", reason);
        MatrixApi a = api;
        String room = roomId;
        new Thread(() -> {
            try {
                a.sendEvent(room, "m.call.hangup", content);
            } catch (IOException ignored) {
                // the other side times out
            }
        }).start();
    }

    private void setSpeaker(boolean on) {
        speaker = on;
        if (audio != null) audio.setSpeakerphoneOn(on);
    }

    private void stopSounds() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
        if (ringback != null) {
            ringback.release();
            ringback = null;
        }
        Vibrator v = getSystemService(Vibrator.class);
        if (v != null) v.cancel();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(Calls.NOTIFICATION);
        if (ended && audio != null) {
            audio.setSpeakerphoneOn(false);
            audio.setMode(audioMode);
        }
    }

    /** Called from the call page (JavaScript). */
    private final class Bridge {
        @JavascriptInterface
        public void send(String type, String json) {
            Map<String, Object> extra = MiniJson.obj();
            Object o = MiniJson.parse(json);
            if (o instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) extra.put(String.valueOf(e.getKey()), e.getValue());
            if (type.equals("invite")) extra.put("lifetime", RING_MS);
            CallActivity.this.send("m.call." + type, extra);
        }

        @JavascriptInterface
        public void state(String s) {
            ui.post(() -> onState(s));
        }

        @JavascriptInterface
        public void log(String s) {
            android.util.Log.i("NovaCall", s);
        }
    }
}
