package com.safarsaathi.app.data.ask;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Google ML Kit's language identification, run ON THE PHONE (the "bundled" model ships inside the app, about 1 MB, so it
 * works offline and without Google Play services). It tells "hi" from "mr" in Devanagari and spots Hindi written in
 * English letters ("hi-Latn"). {@link LanguagePicker} uses it only to break a tie between its own marker words.
 *
 * <p>Call {@link #hint} on a background thread: it waits for ML Kit's answer (a few milliseconds). Any problem gives
 * {@link LanguageHint#NONE}, never an error: the picker then decides without it.
 */
public final class MlKitLanguageHints {

    private static final String TAG = "AskOnBoard";
    /** Languages below this confidence are not even listed (ML Kit's default for "possible languages" is 0.01). */
    private static final float MIN_CONFIDENCE = 0.1f;
    private static final long TIMEOUT_MS = 1500;

    private final LanguageIdentifier identifier = LanguageIdentification.getClient(
            new LanguageIdentificationOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build());

    /** ML Kit's guess for this text. Blocks: never call on the main thread. */
    public LanguageHint hint(String text) {
        try {
            List<IdentifiedLanguage> languages =
                    Tasks.await(identifier.identifyPossibleLanguages(text), TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Map<String, Float> confidences = new HashMap<>();
            for (IdentifiedLanguage language : languages) {
                confidences.put(language.getLanguageTag(), language.getConfidence());
            }
            return LanguageHint.of(confidences);
        } catch (Exception e) {
            // InterruptedException, a timeout or a model problem: decide without ML Kit.
            Log.w(TAG, "ML Kit language identification gave no answer", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return LanguageHint.NONE;
        }
    }

    /** Frees the model. */
    public void close() {
        identifier.close();
    }
}
