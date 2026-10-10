package com.safarsaathi.app.ui.vault;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.VaultRepository;
import com.safarsaathi.app.data.vault.VaultUnavailableException;
import com.safarsaathi.app.data.vault.db.TripFolderRow;
import com.safarsaathi.app.data.wallet.IdDocuments;
import com.safarsaathi.app.databinding.DialogVaultNewTripBinding;
import com.safarsaathi.app.databinding.FragmentVaultHomeBinding;
import com.safarsaathi.app.databinding.ItemVaultTripBinding;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Document Vault home (layout: fragment_vault_home.xml): the trip folders, "New trip folder" and "Load sample trip".
 * Opened from Profile > More. Everything shown here comes from the encrypted vault database on the phone.
 */
public class VaultHomeFragment extends VaultScreen {

    /** Argument name for the trip and document screens (see nav_graph.xml). */
    static final String ARG_TRIP_ID = "tripId";

    // Only valid between onContentCreated and onDestroyView.
    @Nullable
    private FragmentVaultHomeBinding binding;
    @Nullable
    private VaultRepository repository;
    private boolean loadingSample;
    private int sampleTrips;

    public VaultHomeFragment() {
        super(R.layout.fragment_vault_home);
    }

    @Override
    protected void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState) {
        binding = FragmentVaultHomeBinding.bind(content);
        ViewCompat.setAccessibilityHeading(binding.vaultHeadline, true);
        ViewCompat.setAccessibilityHeading(binding.vaultTripsTitle, true);
        requireActivity().addMenuProvider(toolbarMenu, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        try {
            repository = vault().repository();
        } catch (VaultUnavailableException e) {
            binding.vaultContent.setVisibility(View.GONE);
            binding.vaultUnavailable.setVisibility(View.VISIBLE);
            binding.vaultReset.setOnClickListener(button -> confirmReset());
            return;
        }

        // Ask Google Play services to fetch its document scanner now, so "Scan" also works offline later.
        DocumentScanner.prepare(requireContext());
        binding.vaultNewTrip.setOnClickListener(button -> showNewTripDialog());
        binding.vaultLoadSample.setOnClickListener(button -> loadSample());
        repository.observeTripFolders().observe(getViewLifecycleOwner(), this::renderTrips);
        repository.observeSampleTripCount().observe(getViewLifecycleOwner(), count -> {
            sampleTrips = count == null ? 0 : count;
            renderSampleButton();
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderTrips(@Nullable List<TripFolderRow> rows) {
        if (binding == null) {
            return;
        }
        List<TripFolderRow> trips = rows == null ? Collections.emptyList() : rows;
        binding.vaultTripsList.removeAllViews();
        binding.vaultTripsEmpty.setVisibility(trips.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (TripFolderRow row : trips) {
            ItemVaultTripBinding item = ItemVaultTripBinding.inflate(inflater, binding.vaultTripsList, false);
            item.tripTitle.setText(row.trip.title);
            item.tripDates.setText(VaultText.dateRange(requireContext(), row.trip.startDate, row.trip.endDate));
            item.tripCount.setText(getResources().getQuantityString(R.plurals.vault_document_count,
                    row.documentCount, row.documentCount));
            item.tripSample.setVisibility(row.trip.sample ? View.VISIBLE : View.GONE);
            String tripId = row.trip.id;
            item.getRoot().setOnClickListener(card -> openTrip(tripId));
            binding.vaultTripsList.addView(item.getRoot());
        }
    }

    private void renderSampleButton() {
        if (binding == null) {
            return;
        }
        binding.vaultLoadSample.setVisibility(sampleTrips == 0 && !loadingSample ? View.VISIBLE : View.GONE);
        binding.vaultProgress.setVisibility(loadingSample ? View.VISIBLE : View.GONE);
        binding.vaultProgress.setText(R.string.vault_sample_loading);
    }

    private void loadSample() {
        if (repository == null || loadingSample) {
            return;
        }
        loadingSample = true;
        renderSampleButton();
        repository.loadSample(new SamplePages(), new VaultRepository.Callback<String>() {
            @Override
            public void onDone(String tripId) {
                loadingSample = false;
                if (hasView()) {
                    renderSampleButton();
                    say(R.string.vault_sample_loaded);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                loadingSample = false;
                if (hasView()) {
                    renderSampleButton();
                    say(R.string.vault_error_generic);
                }
            }
        });
    }

    private void showNewTripDialog() {
        if (repository == null) {
            return;
        }
        DialogVaultNewTripBinding form = DialogVaultNewTripBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vault_new_trip)
                .setView(form.getRoot())
                .setPositiveButton(R.string.vault_create, null) // set below, so mistakes keep the dialog open
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name = text(form.newTripName.getText());
            String startText = text(form.newTripStart.getText());
            String endText = text(form.newTripEnd.getText());
            // Empty = no date; anything else must be a real DD/MM/YYYY date.
            LocalDate start = startText.isEmpty() ? null : IdDocuments.parseDate(startText);
            LocalDate end = endText.isEmpty() ? null : IdDocuments.parseDate(endText);
            boolean startBad = !startText.isEmpty() && start == null;
            boolean endBad = !endText.isEmpty() && end == null;
            boolean orderBad = start != null && end != null && end.isBefore(start);

            form.newTripNameLayout.setError(name.isEmpty() ? getString(R.string.vault_new_trip_name_error) : null);
            form.newTripStartLayout.setError(startBad ? getString(R.string.vault_new_trip_date_error) : null);
            form.newTripEndLayout.setError(endBad ? getString(R.string.vault_new_trip_date_error)
                    : orderBad ? getString(R.string.vault_new_trip_order_error) : null);
            if (name.isEmpty() || startBad || endBad || orderBad) {
                return;
            }
            repository.createTrip(name, start, end, new VaultRepository.Callback<String>() {
                @Override
                public void onDone(String tripId) {
                    if (hasView()) {
                        openTrip(tripId);
                    }
                }

                @Override
                public void onError(@NonNull Exception error) {
                    if (hasView()) {
                        say(R.string.vault_error_generic);
                    }
                }
            });
            dialog.dismiss();
        }));
        VaultWindow.secure(dialog);
        dialog.show();
    }

    /** The vault cannot be opened on this phone: delete it and start an empty one. */
    private void confirmReset() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vault_settings_delete_title)
                .setMessage(R.string.vault_settings_delete_body)
                .setPositiveButton(R.string.vault_settings_delete_confirm, (shown, which) ->
                        vault().deleteEverything(new VaultRepository.Callback<Void>() {
                            @Override
                            public void onDone(Void nothing) {
                                if (hasView()) {
                                    say(R.string.vault_settings_deleted);
                                    reopenVault(VaultHomeFragment.this);
                                }
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                if (hasView()) {
                                    say(R.string.vault_error_generic);
                                }
                            }
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        VaultWindow.secure(dialog);
        dialog.show();
    }

    private void openTrip(String tripId) {
        Bundle args = new Bundle();
        args.putString(ARG_TRIP_ID, tripId);
        NavHostFragment.findNavController(this).navigate(R.id.vault_trip, args);
    }

    /**
     * Leaves every vault screen and opens a fresh vault home, e.g. after the whole vault was deleted. The old screens
     * (and the old, closed database they read from) are gone; the new home opens the new, empty vault.
     */
    static void reopenVault(Fragment from) {
        NavOptions replaceVault = new NavOptions.Builder().setPopUpTo(R.id.vault_graph, true).build();
        NavHostFragment.findNavController(from).navigate(R.id.vault_graph, null, replaceVault);
    }

    private final MenuProvider toolbarMenu = new MenuProvider() {
        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            inflater.inflate(R.menu.vault_home_menu, menu);
            // "Lock vault" only makes sense while the lock is on.
            menu.findItem(R.id.vault_menu_lock).setVisible(vault().settings().lockEnabled());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item) {
            // No switch on R.id values (AGP 9: not compile-time constants), see CLAUDE.md.
            if (item.getItemId() == R.id.vault_menu_lock) {
                vault().session().lockNow();
                refreshLock(false);
                return true;
            }
            if (item.getItemId() == R.id.vault_menu_settings) {
                NavHostFragment.findNavController(VaultHomeFragment.this).navigate(R.id.vault_settings);
                return true;
            }
            return false;
        }
    };

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
