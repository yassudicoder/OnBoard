package com.safarsaathi.app.data.ask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * What Google ML Kit's on-device language identification thinks a question is written in: language tags ("hi",
 * "mr", "en", "hi-Latn" = Hindi in English letters) with a confidence from 0 to 1. Filled on the phone by
 * MlKitLanguageHints; unit tests build it by hand. {@link #NONE} when ML Kit was not asked or gave no answer.
 */
public final class LanguageHint {

    public static final LanguageHint NONE = new LanguageHint(Collections.<String, Float>emptyMap());

    private final Map<String, Float> confidences;

    private LanguageHint(Map<String, Float> confidences) {
        this.confidences = confidences;
    }

    /** A hint from tag/confidence pairs. "und" (undetermined) is left out. */
    public static LanguageHint of(Map<String, Float> confidences) {
        Map<String, Float> copy = new HashMap<>(confidences);
        copy.remove("und");
        return copy.isEmpty() ? NONE : new LanguageHint(Collections.unmodifiableMap(copy));
    }

    /** The confidence for a tag, 0 when ML Kit did not list it. */
    public float confidence(String tag) {
        Float value = confidences.get(tag);
        return value == null ? 0f : value;
    }

    @Override
    public String toString() {
        return confidences.toString();
    }
}
