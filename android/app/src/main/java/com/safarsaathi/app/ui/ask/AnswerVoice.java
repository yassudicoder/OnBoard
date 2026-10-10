package com.safarsaathi.app.ui.ask;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.ask.AskLanguage;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reads answers aloud with the phone's own text-to-speech voice (offline once the voice is installed), in the
 * answer's language with Indian pronunciation (en-IN, hi-IN, mr-IN).
 *
 * <p>Unlike the Navigate tab's VoiceGuide (one language for a whole walk), the language can change from one answer
 * to the next here, so it is set before each one. A language without a voice on this phone is reported once
 * ({@link Callback#onVoiceMissing}) and skipped, instead of reading Marathi with an English voice.
 */
final class AnswerVoice {

    interface Callback {
        void onVoiceMissing(AskLanguage language);
    }

    private final Callback callback;
    private final Set<AskLanguage> missing = EnumSet.noneOf(AskLanguage.class);
    @Nullable
    private TextToSpeech tts;
    private boolean ready;
    @Nullable
    private String pendingText;
    @Nullable
    private AskLanguage pendingLanguage;

    AnswerVoice(Context context, Callback callback) {
        this.callback = callback;
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (tts == null) {
                return; // shut down before it was ready
            }
            ready = status == TextToSpeech.SUCCESS;
            if (ready && pendingText != null) {
                speak(pendingText, pendingLanguage);
            }
            pendingText = null;
            pendingLanguage = null;
        });
    }

    /** Says this now, cutting off whatever was being said. */
    void speak(String text, AskLanguage language) {
        if (tts == null) {
            return;
        }
        if (!ready) {
            pendingText = text;
            pendingLanguage = language;
            return;
        }
        if (missing.contains(language)) {
            return;
        }
        int result = tts.setLanguage(Locale.forLanguageTag(language.voiceTag));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            missing.add(language);
            callback.onVoiceMissing(language);
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "onboard-ask");
    }

    void silence() {
        pendingText = null;
        if (tts != null && ready) {
            tts.stop();
        }
    }

    void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
