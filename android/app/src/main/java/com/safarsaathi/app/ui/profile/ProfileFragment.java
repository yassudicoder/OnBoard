package com.safarsaathi.app.ui.profile;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.AppSettings;
import com.safarsaathi.app.BuildConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.WalletRepository;
import com.safarsaathi.app.data.local.SavedCardEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.data.ride.RidePlacement;
import com.safarsaathi.app.data.ride.RideProvider;
import com.safarsaathi.app.data.ride.RideTapCounter;
import com.safarsaathi.app.databinding.DialogAddCardBinding;
import com.safarsaathi.app.databinding.FragmentProfileBinding;
import com.safarsaathi.app.databinding.ItemSavedCardBinding;
import com.safarsaathi.app.ui.home.HomeGreeting;
import com.safarsaathi.app.ui.trip.TripUi;
import com.safarsaathi.app.ui.trip.TripViewModel;

import java.util.Collections;
import java.util.List;

/**
 * Profile tab (layout: res/layout/fragment_profile.xml):
 * <ul>
 *   <li>the traveller: the demo's sample traveller, Aditi;</li>
 *   <li>"This trip": purpose, traveller type, language and number of travellers, read from the saved trip (the
 *       server set them when the trip was created; the same Room data as Home and Trip);</li>
 *   <li>"Display": large text for the whole app ({@link AppSettings}); switching it restarts the screen;</li>
 *   <li>"Saved cards": bank, credit/debit and the last 4 digits ONLY, kept on this phone, for card offers later;</li>
 *   <li>"More": the Document Vault, the Wallet, and the parked trip-timeline screens when
 *       feature.tripTimeline=true;</li>
 *   <li>"Connection": the server the app talks to and when it last heard from it.</li>
 * </ul>
 */
public class ProfileFragment extends Fragment {

    // Only valid between onViewCreated and onDestroyView.
    private FragmentProfileBinding binding;

    private WalletRepository wallet;
    @Nullable
    private TripEntity trip;
    private SyncState syncState = SyncState.idle();

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentProfileBinding.bind(view);
        wallet = ServiceLocator.get().getWalletRepository();
        TripViewModel tripViewModel = new ViewModelProvider(this, TripViewModel.factory(AppConfig.DEMO_TRIP_ID))
                .get(TripViewModel.class);
        Context context = requireContext();

        String name = getString(R.string.sample_traveller_name);
        binding.profileName.setText(name);
        binding.profileAvatar.setText(HomeGreeting.initial(name));
        ViewCompat.setAccessibilityHeading(binding.profileName, true);
        ViewCompat.setAccessibilityHeading(binding.profileTripHeading, true);
        ViewCompat.setAccessibilityHeading(binding.profileDisplayHeading, true);
        ViewCompat.setAccessibilityHeading(binding.profileCardsHeading, true);
        ViewCompat.setAccessibilityHeading(binding.profileConnectionHeading, true);

        // Large text: set the switch BEFORE listening, so showing the saved choice does not count as a change.
        binding.profileLargeText.setChecked(AppSettings.isLargeText(context));
        binding.profileLargeText.setOnCheckedChangeListener((toggle, checked) -> {
            if (checked != AppSettings.isLargeText(context)) {
                AppSettings.setLargeText(context, checked);
                requireActivity().recreate(); // MainActivity starts again with the new text size, on this tab
            }
        });

        binding.profileServer.setText(ServiceLocator.get().getAppConfig().getBackendBaseUrl());
        binding.profileVersion.setText(getString(R.string.profile_version_value, BuildConfig.VERSION_NAME));
        RideProvider rides = ServiceLocator.get().getRideProvider();
        binding.profileRides.setText(rides.isMock() ? getString(R.string.profile_rides_mock)
                : getString(R.string.profile_rides_real, getString(BuildConfig.UBER_CLIENT_ID.isEmpty()
                        ? R.string.profile_rides_client_missing : R.string.profile_rides_client_set)));
        binding.profileRideTaps.setText(getString(R.string.profile_ride_taps_value,
                RideTapCounter.taps(context, RidePlacement.DEPARTURE),
                RideTapCounter.taps(context, RidePlacement.ARRIVAL),
                RideTapCounter.taps(context, RidePlacement.RECOVERY)));
        binding.profileAddCard.setOnClickListener(button -> showAddCardDialog());

        // More: the Document Vault and the Wallet are always there; the parked trip-timeline screens only with feature.tripTimeline=true.
        ViewCompat.setAccessibilityHeading(binding.profileMoreHeading, true);
        binding.profileOpenVault.setOnClickListener(button -> open(R.id.vault_graph));
        binding.profileOpenWallet.setOnClickListener(button -> open(R.id.navigation_wallet));
        int parked = BuildConfig.FEATURE_TRIP_TIMELINE ? View.VISIBLE : View.GONE;
        binding.profileOpenTripStatus.setVisibility(parked);
        binding.profileOpenTrip.setVisibility(parked);
        binding.profileOpenTripStatus.setOnClickListener(button -> open(R.id.navigation_trip_status));
        binding.profileOpenTrip.setOnClickListener(button -> open(R.id.navigation_trip));

        LifecycleOwner owner = getViewLifecycleOwner();
        tripViewModel.getTrip().observe(owner, newTrip -> {
            trip = newTrip;
            renderTrip();
            renderSync();
        });
        tripViewModel.getSyncState().observe(owner, newState -> {
            syncState = newState;
            renderSync();
        });
        wallet.observeCards().observe(owner, this::renderCards);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderTrip() {
        if (binding == null) {
            return;
        }
        boolean hasTrip = trip != null;
        binding.profileTripCard.setVisibility(hasTrip ? View.VISIBLE : View.GONE);
        binding.profileTripMissing.setVisibility(hasTrip ? View.GONE : View.VISIBLE);
        if (!hasTrip) {
            return;
        }
        binding.profileTripTitle.setText(trip.title);
        binding.profilePurpose.setText(TripUi.purposeLabel(trip.purpose));
        binding.profilePersona.setText(personaLabel(trip.persona));
        binding.profileLanguage.setText(languageLabel(trip.language));
        binding.profileTravellers.setText(String.valueOf(trip.travellers));
    }

    private void renderSync() {
        if (binding == null) {
            return;
        }
        String line = TripUi.syncLine(requireContext(), syncState, trip == null ? null : trip.lastSyncedAt);
        binding.profileSync.setText(line.isEmpty() ? getString(R.string.profile_sync_never) : line);
    }

    private void renderCards(@Nullable List<SavedCardEntity> cards) {
        if (binding == null) {
            return;
        }
        binding.profileCardsList.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (SavedCardEntity card : cards == null ? Collections.<SavedCardEntity>emptyList() : cards) {
            ItemSavedCardBinding row = ItemSavedCardBinding.inflate(inflater, binding.profileCardsList, false);
            String type = getString(SavedCardEntity.TYPE_DEBIT.equals(card.cardType)
                    ? R.string.profile_card_debit : R.string.profile_card_credit);
            row.cardName.setText(getString(R.string.profile_card_name, card.bank, type));
            row.cardDigits.setText(getString(R.string.profile_card_digits, card.last4));
            long id = card.id;
            row.cardRemove.setOnClickListener(button -> wallet.deleteCard(id));
            binding.profileCardsList.addView(row.getRoot());
        }
    }

    private void showAddCardDialog() {
        DialogAddCardBinding dialogBinding = DialogAddCardBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_add_card)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.profile_card_save, null) // set below, so mistakes keep the dialog open
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String bank = text(dialogBinding.cardBank.getText());
            String last4 = text(dialogBinding.cardLast4.getText());
            dialogBinding.cardBankLayout.setError(bank.isEmpty() ? getString(R.string.profile_card_bank_error) : null);
            dialogBinding.cardLast4Layout.setError(SavedCardEntity.isValidLast4(last4)
                    ? null : getString(R.string.profile_card_last4_error));
            if (bank.isEmpty() || !SavedCardEntity.isValidLast4(last4)) {
                return;
            }
            // No switch on R.id values (AGP 9: not compile-time constants), see CLAUDE.md.
            String type = dialogBinding.cardType.getCheckedRadioButtonId() == R.id.card_type_debit
                    ? SavedCardEntity.TYPE_DEBIT : SavedCardEntity.TYPE_CREDIT;
            wallet.addCard(bank, type, last4);
            dialog.dismiss();
        }));
        dialog.show();
    }

    /** Opens a screen that is not a tab (it gets a back arrow). */
    private void open(int destinationId) {
        Navigation.findNavController(requireView()).navigate(destinationId);
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @StringRes
    private static int personaLabel(String persona) {
        switch (persona) {
            case "FIRST_TIME": return R.string.persona_first_time;
            case "FAMILY": return R.string.persona_family;
            case "BUSINESS": return R.string.persona_business;
            case "ELDERLY": return R.string.persona_elderly;
            default: return R.string.persona_other;
        }
    }

    @StringRes
    private static int languageLabel(String language) {
        switch (language) {
            case "hi": return R.string.language_hi;
            case "mr": return R.string.language_mr;
            case "ta": return R.string.language_ta;
            case "bn": return R.string.language_bn;
            case "en":
            default:
                return R.string.language_en;
        }
    }
}
