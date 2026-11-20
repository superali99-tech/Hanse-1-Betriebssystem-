package org.novaos.mobile;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;

import java.util.List;

/** Nova listens (Android's speech recognition): for dictating chat messages and e-mails. */
final class Ears {
    interface Listener {
        void onReady();

        void onPartial(String text);

        void onResult(String text);

        /** nothingHeard: silence or not understood; otherwise a real error */
        void onFailed(boolean nothingHeard);

        void onLevel(float level);  // 0..1
    }

    private final Context context;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private boolean listening;

    Ears(Context c, Listener l) {
        context = c;
        listener = l;
    }

    static boolean available(Context c) {
        return SpeechRecognizer.isRecognitionAvailable(c);
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    boolean listening() {
        return listening;
    }

    void listen() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle b) {
                    listener.onReady();
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rms) {
                    listener.onLevel(Math.max(0f, Math.min(rms, 10f)) / 10f);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    listening = false;
                    listener.onFailed(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
                }

                @Override
                public void onResults(Bundle b) {
                    listening = false;
                    List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && !r.get(0).trim().isEmpty()) listener.onResult(r.get(0));
                    else listener.onFailed(true);
                }

                @Override
                public void onPartialResults(Bundle b) {
                    List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && !r.get(0).isEmpty()) listener.onPartial(r.get(0));
                }

                @Override
                public void onEvent(int type, Bundle b) {}
            });
        }
        listening = true;
        recognizer.startListening(NovaActivity.recognizerIntent(context));
    }

    void cancel() {
        listening = false;
        if (recognizer != null) recognizer.cancel();
    }

    void destroy() {
        listening = false;
        if (recognizer != null) recognizer.destroy();
        recognizer = null;
    }
}
