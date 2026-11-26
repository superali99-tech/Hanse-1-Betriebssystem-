package org.novaos.mobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Shortening a video in Nova Galerie: drag the start and the end, play the part, optionally
 * without sound - saved as a copy ("…_gekürzt.mp4"), the original stays. The video is not
 * re-encoded, so it is quick and loses nothing; the start moves to the nearest key frame before.
 */
public class VideoTrimActivity extends Activity {
    static final String EXTRA_URI = "uri", EXTRA_NAME = "name";
    private Uri uri;
    private VideoPane pane;
    private Range range;
    private TextView times;
    private CheckBox mute;
    private long duration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        uri = getIntent().getParcelableExtra(EXTRA_URI);
        if (uri == null) {
            finish();
            return;
        }
        Ui.immersive(this, true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 8));
        TextView cancel = Ui.text(this, "✕", 22, Color.WHITE, false);
        cancel.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        cancel.setOnClickListener(v -> finish());
        top.addView(cancel);
        TextView title = Ui.text(this, getString(R.string.trim_title), 18, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView save = Ui.text(this, getString(R.string.edit_save), 16, 0xFFFFD36B, true);
        save.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        save.setOnClickListener(v -> save());
        top.addView(save);
        root.addView(top);
        pane = new VideoPane(this, uri, false);
        root.addView(pane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 22));
        times = Ui.text(this, "", 14, Color.WHITE, false);
        bottom.addView(times);
        range = new Range(this);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        rl.topMargin = Ui.dp(this, 8);
        bottom.addView(range, rl);
        mute = new CheckBox(this);
        mute.setText(R.string.trim_mute);
        mute.setTextColor(Color.WHITE);
        mute.setTypeface(Fonts.regular(this));
        bottom.addView(mute);
        root.addView(bottom);
        setContentView(root);
        new Thread(() -> {
            long d = 0;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, uri);
                String s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                d = s == null ? 0 : Long.parseLong(s);
            } catch (RuntimeException ignored) {
                // unknown length
            } finally {
                try {
                    r.release();
                } catch (Exception ignored) {
                    // nothing
                }
            }
            long len = d;
            runOnUiThread(() -> {
                duration = len;
                showTimes();
            });
        }).start();
    }

    @Override
    protected void onPause() {
        pane.pause();
        super.onPause();
    }

    private long startMs() {
        return Math.round(range.from * duration);
    }

    private long endMs() {
        return Math.round(range.to * duration);
    }

    private void showTimes() {
        times.setText(getString(R.string.trim_times, MusicCatalog.time(startMs()), MusicCatalog.time(endMs()),
                MusicCatalog.time(endMs() - startMs())));
        pane.from = startMs();
        pane.to = endMs();
    }

    private void save() {
        if (duration <= 0 || endMs() - startMs() < 500) {
            Toast.makeText(this, R.string.trim_short, Toast.LENGTH_SHORT).show();
            return;
        }
        pane.pause();
        long from = startMs() * 1000, to = endMs() * 1000;
        boolean noSound = mute.isChecked();
        String name = MediaSave.copyName(getIntent().getStringExtra(EXTRA_NAME), getString(R.string.trim_suffix), "mp4");
        Toast.makeText(this, R.string.trim_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String error = null;
            File tmp = new File(getCacheDir(), "trim.mp4");
            try {
                trim(from, to, noSound, tmp);
                MediaSave.save(getApplicationContext(), name, true, out -> {
                    try (java.io.FileInputStream in = new java.io.FileInputStream(tmp)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                });
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            String err = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                Toast.makeText(this, err == null ? getString(R.string.trim_saved) : getString(R.string.files_failed) + " " + err, Toast.LENGTH_LONG).show();
                if (err == null) {
                    setResult(RESULT_OK);
                    finish();
                }
            });
        }).start();
    }

    /** Copies the samples between from and to (µs) into a new MP4, without re-encoding. */
    private void trim(long from, long to, boolean noSound, File out) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(this, uri, null);
        MediaMuxer mux = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int n = ex.getTrackCount();
        int[] map = new int[n];
        int bufSize = 1 << 20;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            map[i] = -1;
            if (mime == null || (!mime.startsWith("video/") && !mime.startsWith("audio/"))) continue;
            if (noSound && mime.startsWith("audio/")) continue;
            ex.selectTrack(i);
            map[i] = mux.addTrack(f);
            if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufSize = Math.max(bufSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            any = true;
        }
        if (!any) throw new IllegalStateException("no tracks");
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(this, uri);
            String rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rot != null) mux.setOrientationHint(Integer.parseInt(rot));
            r.release();
        } catch (RuntimeException ignored) {
            // upright
        }
        mux.start();
        ex.seekTo(from, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        ByteBuffer buf = ByteBuffer.allocate(bufSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long first = -1;
        while (true) {
            info.size = ex.readSampleData(buf, 0);
            if (info.size < 0) break;
            long t = ex.getSampleTime();
            if (t > to) break;
            int track = ex.getSampleTrackIndex();
            if (track >= 0 && track < n && map[track] >= 0) {
                if (first < 0) first = t;
                info.offset = 0;
                info.presentationTimeUs = Math.max(0, t - first);
                info.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                mux.writeSampleData(map[track], buf, info);
            }
            ex.advance();
        }
        mux.stop();
        mux.release();
        ex.release();
    }

    /** Two handles on a bar: the start and the end of the part to keep. */
    final class Range extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float from = 0f, to = 1f;
        private int drag = -1;

        Range(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = Ui.dp(getContext(), 14), w = getWidth() - 2 * pad, cy = getHeight() / 2f, h = Ui.dp(getContext(), 8);
            p.setColor(0x40FFFFFF);
            c.drawRoundRect(pad, cy - h / 2, pad + w, cy + h / 2, h / 2, h / 2, p);
            p.setColor(0xFFFFD36B);
            c.drawRect(pad + from * w, cy - h / 2, pad + to * w, cy + h / 2, p);
            p.setColor(Color.WHITE);
            float r = Ui.dp(getContext(), 12);
            c.drawCircle(pad + from * w, cy, r, p);
            c.drawCircle(pad + to * w, cy, r, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float pad = Ui.dp(getContext(), 14), w = getWidth() - 2 * pad;
            float x = Zoom.clamp((e.getX() - pad) / Math.max(1, w), 0, 1);
            float minGap = duration > 0 ? Math.min(0.5f, 500f / duration) : 0.02f;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    drag = Math.abs(x - from) <= Math.abs(x - to) ? 0 : 1;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // fall through
                case MotionEvent.ACTION_MOVE:
                    if (drag == 0) from = Math.min(x, to - minGap);
                    else if (drag == 1) to = Math.max(x, from + minGap);
                    invalidate();
                    showTimes();
                    return true;
                default:
                    if (drag >= 0) pane.seekTo(drag == 0 ? startMs() : Math.max(startMs(), endMs() - 1500));
                    drag = -1;
                    return true;
            }
        }
    }
}
