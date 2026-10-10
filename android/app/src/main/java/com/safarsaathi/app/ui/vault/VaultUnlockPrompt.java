package com.safarsaathi.app.ui.vault;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.vault.VaultSession;

/**
 * Lets a screen OUTSIDE the vault (e.g. Ask OnBoard) unlock the vault before it shows vault details from
 * VaultSummaryReader: the same fingerprint / face / PIN prompt as the vault's own lock.
 *
 * <p>Use: in the screen's onViewCreated, {@code unlock = VaultUnlockPrompt.attach(this, this::showAnswer);} and later,
 * when the traveller taps "Unlock", {@code unlock.show();}. androidx.biometric allows ONE prompt per screen
 * (it keeps the answer in a shared ViewModel), so create it once per view, not per tap.
 *
 * <p>A phone without a screen lock cannot prompt: the tap itself then counts as the traveller's choice (as on the
 * vault's own lock cover, "Open without a lock this time").
 */
public final class VaultUnlockPrompt {

    private final Fragment screen;
    private final Runnable onUnlocked;
    private final BiometricPrompt prompt;

    private VaultUnlockPrompt(Fragment screen, Runnable onUnlocked) {
        this.screen = screen;
        this.onUnlocked = onUnlocked;
        this.prompt = new BiometricPrompt(screen, ContextCompat.getMainExecutor(screen.requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        unlockAndContinue();
                    }
                    // Errors and cancels: nothing happens; the screen keeps its "Unlock" button.
                });
    }

    /** Call in onViewCreated. {@code onUnlocked} runs on the main thread once the vault is unlocked. */
    public static VaultUnlockPrompt attach(Fragment screen, Runnable onUnlocked) {
        return new VaultUnlockPrompt(screen, onUnlocked);
    }

    /** Shows the prompt, or continues at once when the vault is already unlocked. */
    public void show() {
        VaultSession session = ServiceLocator.get().getVault().session();
        if (session.isUnlocked()) {
            onUnlocked.run();
            return;
        }
        if (!VaultAuth.phoneHasLock(screen.requireContext())) {
            unlockAndContinue();
            return;
        }
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(screen.getString(R.string.vault_prompt_title))
                .setSubtitle(screen.getString(R.string.vault_unlock_for_other_screen))
                .setAllowedAuthenticators(VaultAuth.authenticators())
                .build());
    }

    private void unlockAndContinue() {
        ServiceLocator.get().getVault().session().unlock();
        onUnlocked.run();
    }
}
