package com.safarsaathi.app.ui.vault;

import android.content.Context;
import android.os.Build;

import androidx.biometric.BiometricManager;

/** The vault's rules for "prove it is you", shared by the vault's own screens and {@link VaultUnlockPrompt}. */
final class VaultAuth {

    private VaultAuth() {
    }

    /**
     * Which proofs the vault accepts: a strong biometric or the phone's PIN/pattern/password. Android 9 and 10 do not
     * support "strong biometric OR PIN" in one prompt, so there it is "any biometric OR PIN" (androidx.biometric docs).
     */
    static int authenticators() {
        int biometric = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? BiometricManager.Authenticators.BIOMETRIC_STRONG
                : BiometricManager.Authenticators.BIOMETRIC_WEAK;
        return biometric | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    }

    /** True when the phone has a screen lock (PIN, pattern, password or biometric) the prompt can ask for. */
    static boolean phoneHasLock(Context context) {
        return BiometricManager.from(context).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS;
    }
}
