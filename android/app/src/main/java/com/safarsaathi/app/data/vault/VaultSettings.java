package com.safarsaathi.app.data.vault;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The vault's settings, kept on the phone (SharedPreferences). They hold choices only, never document data.
 * Safe defaults: lock on, auto-lock after 5 minutes, screenshots blocked.
 */
public final class VaultSettings implements VaultSession.LockPolicy {

    /** The auto-lock choices offered on the settings screen, in minutes. */
    public static final int[] AUTO_LOCK_CHOICES = {1, 5, 15};
    public static final int DEFAULT_AUTO_LOCK_MINUTES = 5;

    /** File name of the app's real settings (tests use their own). */
    public static final String FILE = "onboard_vault_settings";

    private static final String KEY_LOCK = "lock_enabled";
    private static final String KEY_AUTO_LOCK = "auto_lock_minutes";
    private static final String KEY_BLOCK_SCREENSHOTS = "block_screenshots";

    private final SharedPreferences prefs;

    public VaultSettings(Context context, String fileName) {
        prefs = context.getApplicationContext().getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }

    /** Ask for fingerprint, face or PIN before showing the vault. */
    @Override
    public boolean lockEnabled() {
        return prefs.getBoolean(KEY_LOCK, true);
    }

    public void setLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCK, enabled).apply();
    }

    @Override
    public int autoLockMinutes() {
        int minutes = prefs.getInt(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK_MINUTES);
        for (int choice : AUTO_LOCK_CHOICES) {
            if (choice == minutes) {
                return minutes;
            }
        }
        return DEFAULT_AUTO_LOCK_MINUTES;
    }

    public void setAutoLockMinutes(int minutes) {
        prefs.edit().putInt(KEY_AUTO_LOCK, minutes).apply();
    }

    /**
     * Android's FLAG_SECURE on vault screens: screenshots and screen recordings come out black, and the vault does
     * not show in the "recent apps" preview.
     */
    public boolean blockScreenshots() {
        return prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, true);
    }

    public void setBlockScreenshots(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_SCREENSHOTS, block).apply();
    }

    /** Back to the safe defaults (after the whole vault is deleted). */
    public void reset() {
        prefs.edit().clear().apply();
    }
}
