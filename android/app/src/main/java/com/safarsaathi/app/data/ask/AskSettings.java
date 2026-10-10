package com.safarsaathi.app.data.ask;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Ask OnBoard's own choices, kept on the phone (SharedPreferences, its own small file so it never mixes with other
 * features' settings). Phase A4 adds the settings screen; A1 already remembers the reply language chosen with the
 * language chip.
 */
public final class AskSettings {

    private static final String FILE = "onboard_ask";
    private static final String KEY_LANGUAGE = "reply_language";

    private AskSettings() {
    }

    /** The reply language the traveller chose, or null for automatic (reply in the language of the question). */
    @Nullable
    public static AskLanguage replyLanguage(Context context) {
        return AskLanguage.fromCode(prefs(context).getString(KEY_LANGUAGE, null));
    }

    /** @param language a fixed reply language, or null for automatic */
    public static void setReplyLanguage(Context context, @Nullable AskLanguage language) {
        prefs(context).edit().putString(KEY_LANGUAGE, language == null ? null : language.code).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
