package com.safarsaathi.app.data.ask;

/**
 * The languages Ask OnBoard can reply in. Plain Java (no Android), so the unit tests can use it.
 *
 * <p>"Hinglish" is Hindi written in English letters ("Mera gate kahan hai?"). It gets its own answers in English letters,
 * because a traveller who types that way usually reads that way too. Marathi typed in English letters ("Majhi bag
 * kuthe aahe?") is answered in Marathi script: there is no common way of writing Marathi in English letters to answer in.
 */
public enum AskLanguage {

    EN("en", "en-IN", "en-IN", "en"),
    HI("hi", "hi-IN", "hi-IN", "hi"),
    MR("mr", "mr-IN", "mr-IN", "mr"),
    /**
     * Speech: en-IN, because the English (India) recognizer writes what it hears in English letters, which is how a
     * Hinglish speaker expects to see it. Voice: en-IN too, a Hindi voice reads English letters less reliably.
     */
    HINGLISH("hinglish", "en-IN", "en-IN", "en");

    /** File suffix of the answer and lexicon files, e.g. answers_hinglish.properties. Also stored in settings. */
    public final String code;
    /** BCP-47 tag for the phone's speech recognizer (SpeechRecognizer). */
    public final String speechTag;
    /** BCP-47 tag for the phone's text-to-speech voice. */
    public final String voiceTag;
    /** Which language to use for names on the terminal map (the map has en, hi and mr names). */
    public final String mapLanguage;

    AskLanguage(String code, String speechTag, String voiceTag, String mapLanguage) {
        this.code = code;
        this.speechTag = speechTag;
        this.voiceTag = voiceTag;
        this.mapLanguage = mapLanguage;
    }

    /** The language with this {@link #code}, or null (also for null or "auto"). */
    public static AskLanguage fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AskLanguage language : values()) {
            if (language.code.equals(code)) {
                return language;
            }
        }
        return null;
    }

    /** The reply language for the app's own language ("hi", "mr", anything else = English). */
    public static AskLanguage forAppLanguage(String appLanguage) {
        if ("hi".equals(appLanguage)) {
            return HI;
        }
        if ("mr".equals(appLanguage)) {
            return MR;
        }
        return EN;
    }
}
