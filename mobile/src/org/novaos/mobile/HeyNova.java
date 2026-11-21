package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;

import java.util.List;

/**
 * "Hey Nova": while the NovaOS home screen is visible, Nova listens for its name (with
 * Android's speech recognition) and opens with what was said ("Hey Nova, wie spät ist es").
 * Only when switched on in the Nova app and the microphone is allowed; it stops as soon as
 * the home screen is left, so it does not listen in other apps or with the screen off.
 */
final class HeyNova {
    private static final String PREFS = "nova";
    private final Activity home;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean running;
    private int failures;

    HeyNova(Activity home) {
        this.home = home;
    }

    static boolean enabled(Context c) {
        // off unless switched on: Android plays a short sound every time the recognizer starts
        // listening, and on the home screen that happened after every tap (0.1.1.58 and before: on)
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("hey_nova2", false);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("hey_nova2", on).apply();
    }

    private boolean possible() {
        return enabled(home) && SpeechRecognizer.isRecognitionAvailable(home)
                && home.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        if (running || !possible()) return;
        running = true;
        failures = 0;
        listen();
    }

    void stop() {
        running = false;
        ui.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }

    private void listen() {
        if (!running) return;
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(home);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle b) {}

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rms) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        stop();
                        return;
                    }
                    // silence and "nothing understood" are normal: listen again; real errors slow down
                    boolean normal = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                    failures = normal ? 0 : failures + 1;
                    if (failures > 8) {
                        stop();
                        return;
                    }
                    again(normal ? 250 : 1500L * failures);
                }

                @Override
                public void onResults(Bundle b) {
                    if (!check(b)) again(250);
                }

                @Override
                public void onPartialResults(Bundle b) {
                    // waits for the whole sentence ("Hey Nova, wie spät ist es") in onResults
                }

                @Override
                public void onEvent(int type, Bundle b) {}
            });
        }
        try {
            recognizer.startListening(NovaActivity.recognizerIntent(home));
        } catch (RuntimeException e) {
            again(2000);
        }
    }

    /** Final result: "Hey Nova ..." opens Nova with the command. */
    private boolean check(Bundle b) {
        List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r == null) return false;
        for (String s : r) {
            if (NovaBrain.hasWakeWord(s)) {
                wake(s);
                return true;
            }
        }
        return false;
    }

    private void wake(String spoken) {
        stop();
        home.startActivity(new Intent(home, NovaActivity.class).putExtra(NovaActivity.EXTRA_QUERY, spoken)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        home.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void again(long delayMs) {
        if (running) ui.postDelayed(this::listen, delayMs);
    }
}
