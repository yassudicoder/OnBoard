package com.safarsaathi.app.ui.vault;

import android.app.Activity;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;

import com.safarsaathi.app.ServiceLocator;

/**
 * Android's FLAG_SECURE for the Document Vault: while it is set on a window, screenshots and screen recordings of
 * that window come out black, and the "recent apps" preview shows a blank card instead of a passport.
 *
 * <p>MainActivity sets it whenever the current screen is inside the vault (nav_graph's vault_graph) and clears it
 * everywhere else, unless the traveller switched "Block screenshots" off in the vault settings.
 *
 * <p>Dialogs are separate windows and do NOT inherit the flag from the screen behind them, so every vault dialog
 * goes through {@link #secure(Dialog)} too.
 */
public final class VaultWindow {

    private VaultWindow() {
    }

    /** Sets or clears FLAG_SECURE on the activity's window. */
    public static void update(Activity activity, boolean showingVault) {
        apply(activity.getWindow(), showingVault && blockScreenshots());
    }

    /** Call after {@code create()} and before {@code show()} on any dialog shown by a vault screen. */
    static void secure(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            apply(window, blockScreenshots());
        }
    }

    private static boolean blockScreenshots() {
        return ServiceLocator.get().getVault().settings().blockScreenshots();
    }

    private static void apply(Window window, boolean secure) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
