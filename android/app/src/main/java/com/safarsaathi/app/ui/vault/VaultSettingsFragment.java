package com.safarsaathi.app.ui.vault;

import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.FileCheckReport;
import com.safarsaathi.app.data.vault.VaultRepository;
import com.safarsaathi.app.data.vault.VaultSettings;
import com.safarsaathi.app.data.vault.VaultUnavailableException;
import com.safarsaathi.app.databinding.FragmentVaultSettingsBinding;

/**
 * Vault settings (layout: fragment_vault_settings.xml): the lock, auto-lock time, screenshot blocking, a check of
 * every file, "Lock now", and deleting the whole vault.
 *
 * <p>Switching the lock OFF asks for fingerprint/face/PIN first, so someone who picks up an unlocked phone cannot
 * simply turn the protection off.
 */
public class VaultSettingsFragment extends VaultScreen {

    @Nullable
    private FragmentVaultSettingsBinding binding;
    /** True while the code itself moves the lock switch, so that move is not treated as the traveller's choice. */
    private boolean settingSwitch;

    public VaultSettingsFragment() {
        super(R.layout.fragment_vault_settings);
    }

    @Override
    protected void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState) {
        binding = FragmentVaultSettingsBinding.bind(content);
        VaultSettings settings = vault().settings();
        ViewCompat.setAccessibilityHeading(binding.settingsSecurityHeading, true);
        ViewCompat.setAccessibilityHeading(binding.settingsFilesHeading, true);
        ViewCompat.setAccessibilityHeading(binding.settingsPrivacyHeading, true);

        // Lock: set the saved value BEFORE listening, so showing it does not count as a change.
        setLockSwitch(settings.lockEnabled());
        binding.settingsLock.setOnCheckedChangeListener((toggle, checked) -> {
            if (!settingSwitch) {
                onLockSwitched(checked);
            }
        });

        // Auto-lock: one button per choice (1, 5, 15 minutes).
        MaterialButton[] buttons = {binding.settingsAutoLock1, binding.settingsAutoLock5, binding.settingsAutoLock15};
        for (int i = 0; i < buttons.length; i++) {
            int minutes = VaultSettings.AUTO_LOCK_CHOICES[i];
            buttons[i].setText(getResources().getQuantityString(R.plurals.vault_settings_minutes, minutes, minutes));
            if (minutes == settings.autoLockMinutes()) {
                binding.settingsAutoLock.check(buttons[i].getId());
            }
            buttons[i].setOnClickListener(button -> settings.setAutoLockMinutes(minutes));
        }

        binding.settingsScreenshots.setChecked(settings.blockScreenshots());
        binding.settingsScreenshots.setOnCheckedChangeListener((toggle, block) -> {
            settings.setBlockScreenshots(block);
            VaultWindow.update(requireActivity(), true); // this screen is a vault screen
        });

        binding.settingsCheck.setOnClickListener(button -> checkFiles());
        binding.settingsLockNow.setOnClickListener(button -> {
            vault().session().lockNow();
            refreshLock(false);
        });
        binding.settingsDelete.setOnClickListener(button -> confirmDeleteEverything());
        renderLockDependent();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void onLockSwitched(boolean on) {
        VaultSettings settings = vault().settings();
        if (on) {
            settings.setLockEnabled(true);
            vault().session().unlock(); // the traveller is here already; the lock applies from now on
            renderLockDependent();
            return;
        }
        // Turning it off: prove it is you first. Cancelled = the switch goes back on.
        confirmIdentity(R.string.vault_settings_lock_off_title, () -> {
            settings.setLockEnabled(false);
            renderLockDependent();
        }, () -> setLockSwitch(true));
    }

    private void setLockSwitch(boolean on) {
        if (binding == null) {
            return;
        }
        settingSwitch = true;
        binding.settingsLock.setChecked(on);
        settingSwitch = false;
    }

    /** Auto-lock and "Lock now" only mean something while the lock is on. */
    private void renderLockDependent() {
        if (binding == null) {
            return;
        }
        boolean lockOn = vault().settings().lockEnabled();
        int visibility = lockOn ? View.VISIBLE : View.GONE;
        binding.settingsAutoLockLabel.setVisibility(visibility);
        binding.settingsAutoLock.setVisibility(visibility);
        binding.settingsLockNow.setVisibility(visibility);
    }

    /** The decrypt-test: every file is decrypted in memory and compared with its SHA-256 fingerprint. */
    private void checkFiles() {
        if (binding == null) {
            return;
        }
        VaultRepository repository;
        try {
            repository = vault().repository();
        } catch (VaultUnavailableException e) {
            showCheckResult(getString(R.string.vault_unavailable_title));
            return;
        }
        binding.settingsCheck.setEnabled(false);
        showCheckResult(getString(R.string.vault_settings_checking));
        repository.checkAllFiles(new VaultRepository.Callback<FileCheckReport>() {
            @Override
            public void onDone(FileCheckReport report) {
                if (binding == null) {
                    return;
                }
                binding.settingsCheck.setEnabled(true);
                if (report.checked() == 0) {
                    showCheckResult(getString(R.string.vault_settings_check_none));
                } else if (report.allOk()) {
                    showCheckResult(getString(R.string.vault_settings_check_ok, report.checked(),
                            Formatter.formatShortFileSize(requireContext(), report.totalBytes())));
                } else {
                    showCheckResult(getString(R.string.vault_settings_check_problems, report.problems().size(),
                            report.checked()));
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding != null) {
                    binding.settingsCheck.setEnabled(true);
                    showCheckResult(getString(R.string.vault_error_generic));
                }
            }
        });
    }

    private void showCheckResult(String text) {
        if (binding == null) {
            return;
        }
        binding.settingsCheckResult.setVisibility(View.VISIBLE);
        binding.settingsCheckResult.setText(text);
    }

    private void confirmDeleteEverything() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vault_settings_delete_title)
                .setMessage(R.string.vault_settings_delete_body)
                .setPositiveButton(R.string.vault_settings_delete_confirm, (shown, which) -> deleteEverything())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        VaultWindow.secure(dialog);
        dialog.show();
    }

    private void deleteEverything() {
        if (binding != null) {
            binding.settingsDelete.setEnabled(false);
        }
        vault().deleteEverything(new VaultRepository.Callback<Void>() {
            @Override
            public void onDone(Void nothing) {
                if (hasView()) {
                    say(R.string.vault_settings_deleted);
                    VaultHomeFragment.reopenVault(VaultSettingsFragment.this);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding != null) {
                    binding.settingsDelete.setEnabled(true);
                    say(R.string.vault_error_generic);
                }
            }
        });
    }
}
