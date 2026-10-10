package com.safarsaathi.app.ui.ask;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * Voice questions with the phone's own speech recognizer (Android's SpeechRecognizer, usually Google's), shown inside
 * our screen rather than in a pop-up.
 *
 * <p>Offline first: every request sets {@code EXTRA_PREFER_OFFLINE}, so a phone with the offline speech pack for that
 * language (English India, Hindi, Marathi; Settings > Google > Voice / Speech) never needs the internet. Without the
 * pack the recognizer goes online if it can; offline and without the pack it fails, and the screen says so and offers
 * typing instead ({@link Problem#NEEDS_PACK_OR_INTERNET}).
 *
 * <p>Needs the RECORD_AUDIO permission (the screen asks first). Main thread only: Android requires that.
 */
final class SpeechInput {

    /** Why listening stopped without a question. */
    enum Problem {
        /** Silence, or nothing recognisable: "Didn't catch that". */
        NOT_HEARD,
        /** No offline speech pack for that language and no internet (or the language is not supported at all). */
        NEEDS_PACK_OR_INTERNET,
        NO_PERMISSION,
        MICROPHONE,
        BUSY
    }

    interface Listener {
        /** The microphone is open: show "Listening…". */
        void onListening();

        /** Words so far, while the traveller is still speaking. */
        void onPartial(String text);

        /** The final words: ask them. */
        void onHeard(String text);

        void onProblem(Problem problem);

        /** Listening ended, for any reason (after onHeard / onProblem too). */
        void onStopped();
    }

    private final Context context;
    private final Listener listener;
    @Nullable
    private SpeechRecognizer recognizer;
    private boolean listening;

    SpeechInput(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    /** True when the phone has any speech recognizer at all (some phones and emulators have none). */
    static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    boolean isListening() {
        return listening;
    }

    /** @param languageTag e.g. "hi-IN" */
    void start(String languageTag) {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new Callbacks());
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        listening = true;
        recognizer.startListening(intent);
    }

    /** Stop listening and use what was heard so far. */
    void stop() {
        if (recognizer != null && listening) {
            recognizer.stopListening();
        }
    }

    /** Free the recognizer (screen closing). */
    void destroy() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        listening = false;
    }

    private void finish() {
        if (listening) {
            listening = false;
            listener.onStopped();
        }
    }

    private static String firstResult(@Nullable Bundle results) {
        ArrayList<String> texts = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return texts == null || texts.isEmpty() || texts.get(0) == null ? "" : texts.get(0);
    }

    private final class Callbacks implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            listener.onListening();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            String text = firstResult(partialResults);
            if (!text.isEmpty()) {
                listener.onPartial(text);
            }
        }

        @Override
        public void onResults(Bundle results) {
            String text = firstResult(results);
            if (text.trim().isEmpty()) {
                listener.onProblem(Problem.NOT_HEARD);
            } else {
                listener.onHeard(text);
            }
            finish();
        }

        @Override
        public void onError(int error) {
            Problem problem = problemFor(error);
            if (problem != null) {
                listener.onProblem(problem);
            }
            finish();
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    /** SpeechRecognizer's error codes in plain words; null = nothing to tell the traveller (we cancelled). */
    @Nullable
    static Problem problemFor(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return Problem.NOT_HEARD;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                return Problem.NEEDS_PACK_OR_INTERNET;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return Problem.NO_PERMISSION;
            case SpeechRecognizer.ERROR_AUDIO:
                return Problem.MICROPHONE;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                return Problem.BUSY;
            case SpeechRecognizer.ERROR_CLIENT:
            default:
                return null;
        }
    }
}
