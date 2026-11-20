package org.novaos.mobile;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Records a voice message for Nova Chat (AAC in .m4a, like most chat apps; at most 5 minutes). */
final class VoiceRecorder {
    static final long MAX_MS = 5 * 60 * 1000L;

    static final class Result {
        File file;
        long duration;
        List<Integer> waveform;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> levels = new ArrayList<>();
    private MediaRecorder rec;
    private File file;
    private long start;
    private Runnable sampler;

    boolean recording() {
        return rec != null;
    }

    long elapsed() {
        return rec == null ? 0 : System.currentTimeMillis() - start;
    }

    /** The newest loudness 0..1 (for the red dot). */
    float level() {
        return levels.isEmpty() ? 0f : (float) Math.sqrt(levels.get(levels.size() - 1) / 32767.0);
    }

    @SuppressWarnings("deprecation")
    boolean start(Context c) {
        stopRecorder();
        file = new File(ChatMedia.dir(c), "voice-" + System.currentTimeMillis() + ".m4a");
        levels.clear();
        try {
            rec = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(c) : new MediaRecorder();
            rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            rec.setAudioChannels(1);
            rec.setAudioSamplingRate(44100);
            rec.setAudioEncodingBitRate(64000);
            rec.setMaxDuration((int) MAX_MS);
            rec.setOutputFile(file.getPath());
            rec.prepare();
            rec.start();
        } catch (Exception e) {
            stopRecorder();
            return false;
        }
        start = System.currentTimeMillis();
        sampler = new Runnable() {
            @Override
            public void run() {
                if (rec == null) return;
                try {
                    levels.add(rec.getMaxAmplitude());
                } catch (RuntimeException ignored) {
                    // recorder already stopped
                }
                handler.postDelayed(this, 100);
            }
        };
        handler.post(sampler);
        return true;
    }

    /** Finishes the recording; null if it was too short or failed. */
    Result stop() {
        if (rec == null) return null;
        long duration = System.currentTimeMillis() - start;
        boolean ok = true;
        try {
            rec.stop();
        } catch (RuntimeException e) {
            ok = false;  // nothing recorded (too short)
        }
        stopRecorder();
        if (!ok || duration < 700 || !file.isFile() || file.length() == 0) {
            delete();
            return null;
        }
        Result r = new Result();
        r.file = file;
        r.duration = Math.min(duration, MAX_MS);
        r.waveform = Waveform.of(levels, 64);
        return r;
    }

    void cancel() {
        if (rec != null) {
            try {
                rec.stop();
            } catch (RuntimeException ignored) {
                // too short
            }
        }
        stopRecorder();
        delete();
    }

    private void delete() {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }

    private void stopRecorder() {
        if (sampler != null) handler.removeCallbacks(sampler);
        sampler = null;
        if (rec != null) {
            try {
                rec.release();
            } catch (RuntimeException ignored) {
                // already released
            }
        }
        rec = null;
    }
}
