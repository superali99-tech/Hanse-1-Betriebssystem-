package org.novaos.mobile;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/** Nova's voice (Android's own text to speech, works offline with most phones). */
final class Speaker {
    interface Done {
        void done();
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready, failed;
    private String pendingText;
    private Done pendingDone, current;
    private int id;

    Speaker(Context c) {
        tts = new TextToSpeech(c, status -> {
            if (status != TextToSpeech.SUCCESS) {
                failed = true;
                if (pendingDone != null) ui.post(pendingDone::done);
                pendingDone = null;
                return;
            }
            tts.setLanguage(Locale.getDefault());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    finished(utteranceId);
                }

                @Override
                @SuppressWarnings("deprecation")
                public void onError(String utteranceId) {
                    finished(utteranceId);
                }
            });
            ready = true;
            if (pendingText != null) say(pendingText, pendingDone);
            pendingText = null;
            pendingDone = null;
        });
    }

    private void finished(String utteranceId) {
        ui.post(() -> {
            if (String.valueOf(id).equals(utteranceId) && current != null) {
                Done d = current;
                current = null;
                d.done();
            }
        });
    }

    /** Says text (instead of what is being said now); done runs afterwards on the main thread. */
    void say(String text, Done done) {
        if (failed || tts == null) {
            if (done != null) ui.post(done::done);
            return;
        }
        if (!ready) {
            pendingText = text;
            pendingDone = done;
            return;
        }
        current = done;
        id++;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), String.valueOf(id));
    }

    boolean speaking() {
        return ready && tts != null && tts.isSpeaking();
    }

    void stop() {
        current = null;
        pendingText = null;
        pendingDone = null;
        if (ready && tts != null) tts.stop();
    }

    void shutdown() {
        stop();
        if (tts != null) tts.shutdown();
        tts = null;
    }
}
