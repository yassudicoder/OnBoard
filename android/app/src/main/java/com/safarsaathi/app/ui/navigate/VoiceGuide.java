package com.safarsaathi.app.ui.navigate;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Speaks navigation instructions with the phone's own text-to-speech voice (works offline once the voice is
 * installed), in the app's language with Indian pronunciation (en-IN, hi-IN, mr-IN).
 *
 * <p>When the phone has no voice for that language, it says so once ({@link Callback#onVoiceMissing}) instead of
 * reading Marathi text with an English voice.
 */
public final class VoiceGuide {

    public interface Callback {
        void onVoiceMissing(Locale locale);
    }

    private final Locale locale;
    private final Callback callback;
    @Nullable
    private TextToSpeech tts;
    private boolean ready;
    private boolean missing;
    @Nullable
    private String pending;

    public VoiceGuide(Context context, String language, Callback callback) {
        this.locale = new Locale(language, "IN");
        this.callback = callback;
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (tts == null) {
                return; // shut down before it was ready
            }
            if (status != TextToSpeech.SUCCESS) {
                missing = true;
                return;
            }
            int result = tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                missing = true;
                callback.onVoiceMissing(locale);
                return;
            }
            ready = true;
            if (pending != null) {
                speak(pending);
                pending = null;
            }
        });
    }

    /** Says this now, cutting off whatever was being said. */
    public void speak(String text) {
        if (missing || tts == null) {
            return;
        }
        if (!ready) {
            pending = text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "onboard-nav");
    }

    public void silence() {
        pending = null;
        if (tts != null && ready) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
