package com.safarsaathi.app.ui.vault;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.vault.Vault;
import com.safarsaathi.app.databinding.ViewVaultLockCoverBinding;

/**
 * Base class of every Document Vault screen. It puts the screen's own layout under a lock cover
 * (view_vault_lock_cover.xml) and shows the cover whenever the vault is locked ({@link
 * com.safarsaathi.app.data.vault.VaultSession}): on opening, after the auto-lock time, and after "Lock now".
 *
 * <p>Unlocking uses androidx.biometric's BiometricPrompt: fingerprint or face, with the phone's PIN, pattern or
 * password as the fallback. The prompt opens by itself once when a locked screen appears; after that the traveller
 * taps Unlock (so cancelling never traps them in a loop of prompts).
 *
 * <p>Each screen has exactly ONE BiometricPrompt, owned here. androidx.biometric keeps the callback in a shared
 * ViewModel, so a second prompt in the same screen would steal the first one's answers. Screens that need to confirm
 * the traveller for something else (turning the lock off) use {@link #confirmIdentity} instead.
 */
public abstract class VaultScreen extends Fragment {

    /** How often a visible vault screen checks whether the auto-lock time has run out. */
    private static final long LOCK_CHECK_MILLIS = 15_000;

    @LayoutRes
    private final int contentLayout;

    // Only valid between onCreateView and onDestroyView.
    @Nullable
    private View content;
    @Nullable
    private ViewVaultLockCoverBinding cover;
    @Nullable
    private BiometricPrompt prompt;

    /** What to do when the prompt that is showing succeeds or fails; null = it is the unlock prompt. */
    @Nullable
    private Runnable pendingSuccess;
    @Nullable
    private Runnable pendingFailure;
    private boolean promptShowing;
    /** The unlock prompt already opened by itself during this locked period. */
    private boolean autoPrompted;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable lockCheck = new Runnable() {
        @Override
        public void run() {
            refreshLock(false);
            handler.postDelayed(this, LOCK_CHECK_MILLIS);
        }
    };

    protected VaultScreen(@LayoutRes int contentLayout) {
        this.contentLayout = contentLayout;
    }

    /** Like onViewCreated, for the screen's own layout. */
    protected abstract void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState);

    @NonNull
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                   @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(inflater.getContext());
        content = inflater.inflate(contentLayout, root, false);
        root.addView(content);
        cover = ViewVaultLockCoverBinding.inflate(inflater, root, true); // on top of the content
        return root;
    }

    @Override
    public final void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // BiometricPrompt must be created here (before the screen is resumed), with a main-thread executor.
        prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()), promptCallback);
        ViewVaultLockCoverBinding lockCover = requireCover();
        lockCover.vaultCoverUnlock.setOnClickListener(button -> showUnlockPrompt());
        lockCover.vaultCoverSettings.setOnClickListener(button -> openSecuritySettings());
        lockCover.vaultCoverSkip.setOnClickListener(button -> {
            vault().session().unlock();
            refreshLock(false);
        });
        onContentCreated(requireContent(), savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Before the first frame is drawn, so a locked vault never flashes its documents.
        refreshLock(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLock(true);
        handler.postDelayed(lockCheck, LOCK_CHECK_MILLIS);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(lockCheck);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(lockCheck);
        prompt = null;
        cover = null;
        content = null;
        // A prompt that was open belongs to the old view; the next view starts with a clean slate.
        promptShowing = false;
        pendingSuccess = null;
        pendingFailure = null;
        super.onDestroyView();
    }

    protected final Vault vault() {
        return ServiceLocator.get().getVault();
    }

    /** True while this screen's view exists (async answers check this before touching views). */
    protected final boolean hasView() {
        return content != null;
    }

    /** Shows or hides the lock cover. {@code mayPrompt}: open the unlock prompt by itself if it has not yet. */
    protected final void refreshLock(boolean mayPrompt) {
        if (cover == null || content == null) {
            return;
        }
        boolean unlocked = vault().session().isUnlocked();
        if (unlocked) {
            autoPrompted = false;
        }
        cover.getRoot().setVisibility(unlocked ? View.GONE : View.VISIBLE);
        // INVISIBLE, not GONE: the screen keeps its scroll position, but nothing of it is drawn or read aloud.
        content.setVisibility(unlocked ? View.VISIBLE : View.INVISIBLE);
        if (unlocked) {
            return;
        }
        boolean phoneHasLock = phoneHasLock(requireContext());
        cover.vaultCoverTitle.setText(phoneHasLock ? R.string.vault_locked_title : R.string.vault_no_lock_title);
        cover.vaultCoverBody.setText(phoneHasLock ? R.string.vault_locked_body : R.string.vault_no_lock_body);
        cover.vaultCoverUnlock.setVisibility(phoneHasLock ? View.VISIBLE : View.GONE);
        cover.vaultCoverSettings.setVisibility(phoneHasLock ? View.GONE : View.VISIBLE);
        cover.vaultCoverSkip.setVisibility(phoneHasLock ? View.GONE : View.VISIBLE);
        if (phoneHasLock && mayPrompt && !autoPrompted && !promptShowing) {
            autoPrompted = true;
            showUnlockPrompt();
        }
    }

    /**
     * Asks for fingerprint, face or PIN before something sensitive (e.g. switching the lock off). When the phone has
     * no screen lock at all there is nothing to ask, so {@code onSuccess} runs straight away.
     */
    protected final void confirmIdentity(@StringRes int title, Runnable onSuccess, Runnable onFailure) {
        if (!phoneHasLock(requireContext())) {
            onSuccess.run();
            return;
        }
        pendingSuccess = onSuccess;
        pendingFailure = onFailure;
        authenticate(title);
    }

    /** A short message at the bottom of the screen. Attached to the activity, so it survives leaving the screen. */
    protected final void say(@StringRes int message) {
        if (getActivity() == null) {
            return; // the screen is already gone
        }
        View anchor = requireActivity().findViewById(android.R.id.content);
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();
    }

    private void showUnlockPrompt() {
        pendingSuccess = null;
        pendingFailure = null;
        authenticate(R.string.vault_prompt_title);
    }

    private void authenticate(@StringRes int title) {
        if (prompt == null || promptShowing) {
            return;
        }
        promptShowing = true;
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(title))
                .setSubtitle(getString(R.string.vault_prompt_subtitle))
                // With DEVICE_CREDENTIAL allowed, Android shows "Use PIN" itself; a cancel button is not allowed.
                .setAllowedAuthenticators(VaultAuth.authenticators())
                .build());
    }

    private final BiometricPrompt.AuthenticationCallback promptCallback =
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    promptShowing = false;
                    Runnable success = pendingSuccess;
                    pendingSuccess = null;
                    pendingFailure = null;
                    if (success != null) {
                        success.run();
                    } else {
                        vault().session().unlock();
                        refreshLock(false);
                    }
                }

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence message) {
                    promptShowing = false;
                    Runnable failure = pendingFailure;
                    pendingSuccess = null;
                    pendingFailure = null;
                    if (failure != null) {
                        failure.run();
                    }
                    if (!hasView()) {
                        return;
                    }
                    boolean cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED
                            || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            || errorCode == BiometricPrompt.ERROR_CANCELED;
                    if (!cancelled) {
                        // e.g. "Too many attempts". The cover (and its Unlock button) simply stays.
                        Snackbar.make(requireContent(), getString(R.string.vault_unlock_failed, message),
                                Snackbar.LENGTH_LONG).show();
                    }
                    refreshLock(false);
                }

                // onAuthenticationFailed (a finger that did not match) needs nothing: the prompt stays open.
            };

    /** True when the phone has a screen lock (PIN, pattern, password or biometric) the prompt can ask for. */
    static boolean phoneHasLock(Context context) {
        return VaultAuth.phoneHasLock(context);
    }

    private void openSecuritySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private View requireContent() {
        if (content == null) {
            throw new IllegalStateException("The vault screen has no view");
        }
        return content;
    }

    private ViewVaultLockCoverBinding requireCover() {
        if (cover == null) {
            throw new IllegalStateException("The vault screen has no view");
        }
        return cover;
    }
}
