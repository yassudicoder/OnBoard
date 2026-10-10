package com.safarsaathi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * The traveller's display choices, kept on the phone (SharedPreferences: a small key-value file).
 *
 * <p>Large text: every text in the app is drawn 30% bigger, on top of the phone's own font size setting, for
 * travellers who find small text hard to read (CLAUDE.md: elderly mode). MainActivity applies it when it starts
 * ({@link #applyTo}); the Profile tab switches it and restarts the screen.
 */
public final class AppSettings {

    /** How much bigger large text is. */
    public static final float LARGE_TEXT_SCALE = 1.3f;

    private static final String FILE = "onboard_settings";
    private static final String KEY_LARGE_TEXT = "large_text";

    private AppSettings() {
    }

    public static boolean isLargeText(Context context) {
        return prefs(context).getBoolean(KEY_LARGE_TEXT, false);
    }

    public static void setLargeText(Context context, boolean largeText) {
        prefs(context).edit().putBoolean(KEY_LARGE_TEXT, largeText).apply();
    }

    /**
     * The context MainActivity should use: the same one with bigger text when large text is on.
     * Called from MainActivity.attachBaseContext, before any view exists.
     */
    public static Context applyTo(Context base) {
        if (!isLargeText(base)) {
            return base;
        }
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.fontScale = configuration.fontScale * LARGE_TEXT_SCALE;
        return base.createConfigurationContext(configuration);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
