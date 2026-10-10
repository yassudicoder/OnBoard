package com.safarsaathi.app.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.databinding.FragmentHomeBinding;
import com.safarsaathi.app.ui.ask.AskFragment;
import com.safarsaathi.app.ui.navigate.GoogleMapsLink;
import com.safarsaathi.app.ui.trip.TripOverview;
import com.safarsaathi.app.ui.trip.TripTime;
import com.safarsaathi.app.ui.trip.TripUi;
import com.safarsaathi.app.ui.trip.TripViewModel;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Home tab (layout: res/layout/fragment_home.xml), the start screen of the Gate Navigation + Baggage Shield app.
 *
 * <p>The flight card shows the passenger's flight from the saved trip (the backend's data, kept in Room, so it also
 * opens offline): flight, from where, departure and arrival, and its status. GATE and BOARDING say
 * "Not announced yet": there is no gate data source until phase G4, and the app never invents a gate or a time
 * (CLAUDE.md). "Navigate to gate" opens the Navigate tab; "Register a bag" opens the Bags tab.
 *
 * <p>Refreshes like before: when the tab opens (unless another screen just did), on pull-down or Retry, and every
 * 10 seconds while it is on screen.
 */
public class HomeFragment extends Fragment {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            viewModel.refreshQuietly();
            mainHandler.postDelayed(this, AppConfig.AUTO_REFRESH_INTERVAL_MS);
        }
    };

    // Only valid between onViewCreated and onDestroyView (the fragment can outlive its views).
    private FragmentHomeBinding binding;

    private TripViewModel viewModel;

    private boolean tripLoaded;   // false until Room has answered once (null trip = "no saved copy")
    private boolean blocksLoaded;
    @Nullable
    private TripEntity trip;
    private List<BlockEntity> blocks = Collections.emptyList();
    private SyncState syncState = SyncState.idle();

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentHomeBinding.bind(view);
        // The saved trip (from the backend, kept in Room) is where the flight comes from.
        viewModel = new ViewModelProvider(this, TripViewModel.factory(AppConfig.DEMO_TRIP_ID))
                .get(TripViewModel.class);

        binding.homeAvatar.setText(HomeGreeting.initial(getString(R.string.sample_traveller_name)));
        ViewCompat.setAccessibilityHeading(binding.homeGreeting, true);
        binding.homeRoot.setOnRefreshListener(() -> viewModel.refresh());
        binding.homeRetry.setOnClickListener(button -> viewModel.refresh());
        binding.homeNavigate.setOnClickListener(button -> openTab(R.id.navigation_navigate));
        binding.homeDirectionsToAirport.setOnClickListener(button -> {
            if (!GoogleMapsLink.openDirectionsToTerminal(requireContext())) {
                Snackbar.make(binding.getRoot(), R.string.nav_notice_maps_missing, Snackbar.LENGTH_LONG).show();
            }
        });
        binding.homeRegisterBag.setOnClickListener(button -> openTab(R.id.navigation_bags));
        // Ask OnBoard: the bar opens the chat; its microphone opens it listening (AskFragment.ARG_START_VOICE).
        binding.homeAskBar.setOnClickListener(bar -> openAsk(false));
        binding.homeAskVoice.setOnClickListener(button -> openAsk(true));

        LifecycleOwner owner = getViewLifecycleOwner();
        viewModel.getTrip().observe(owner, newTrip -> {
            tripLoaded = true;
            trip = newTrip;
            render();
        });
        viewModel.getBlocks().observe(owner, newBlocks -> {
            blocksLoaded = true;
            blocks = newBlocks == null ? Collections.emptyList() : newBlocks;
            render();
        });
        viewModel.getSyncState().observe(owner, newState -> {
            syncState = newState;
            render();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.refreshOnOpen();
        mainHandler.removeCallbacks(autoRefresh);
        mainHandler.postDelayed(autoRefresh, AppConfig.AUTO_REFRESH_INTERVAL_MS);
    }

    @Override
    public void onStop() {
        mainHandler.removeCallbacks(autoRefresh);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render() {
        if (binding == null) {
            return;
        }
        binding.homeGreeting.setText(getString(greetingText(), getString(R.string.sample_traveller_name)));
        if (syncState.kind != SyncState.Kind.REFRESHING) {
            binding.homeRoot.setRefreshing(false);
        }
        String syncLine = TripUi.syncLine(requireContext(), syncState, trip == null ? null : trip.lastSyncedAt);
        binding.homeSync.setText(syncLine);
        binding.homeSync.setVisibility(syncLine.isEmpty() ? View.GONE : View.VISIBLE);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(binding.homeSync,
                syncState.kind == SyncState.Kind.OFFLINE ? R.drawable.ic_offline_24 : 0, 0, 0, 0);

        boolean showContent = trip != null && blocksLoaded;
        binding.homeContent.setVisibility(showContent ? View.VISIBLE : View.GONE);
        binding.homeStateBox.setVisibility(showContent ? View.GONE : View.VISIBLE);
        if (showContent) {
            BlockEntity flight = TripOverview.of(blocks, Instant.now()).flight;
            renderFlight(flight);
        } else {
            renderNoSavedPlan();
        }
    }

    /** No plan saved on the phone yet: "Loading…" while it is fetched, otherwise why it failed + Retry. */
    private void renderNoSavedPlan() {
        boolean failed = tripLoaded && syncState.kind == SyncState.Kind.OFFLINE;
        binding.homeProgress.setVisibility(failed ? View.GONE : View.VISIBLE);
        binding.homeStateIcon.setVisibility(failed ? View.VISIBLE : View.GONE);
        binding.homeStateTitle.setText(failed ? R.string.trip_empty_error_title : R.string.trip_empty_loading_title);
        binding.homeStateMessage.setText(failed
                ? TripUi.emptyStateMessage(syncState.problem)
                : R.string.trip_empty_loading_message);
        binding.homeRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
    }

    /** The flight card. Gate and boarding stay "Not announced yet" (set in the layout) until phase G4. */
    private void renderFlight(@Nullable BlockEntity flight) {
        if (flight == null) {
            binding.homeFlightTitle.setText(R.string.home_no_flight);
            binding.homeFlightFrom.setVisibility(View.GONE);
            binding.homeDepartsTime.setText("-");
            binding.homeDepartsDay.setText("");
            binding.homeArrivesTime.setText("-");
            binding.homeArrivesDay.setText("");
            binding.homeFlightStatus.setVisibility(View.GONE);
            binding.homeFlightPlanned.setVisibility(View.GONE);
            return;
        }
        binding.homeFlightTitle.setText(flight.title);
        boolean hasLocation = flight.locationName != null && !flight.locationName.trim().isEmpty();
        binding.homeFlightFrom.setVisibility(hasLocation ? View.VISIBLE : View.GONE);
        binding.homeFlightFrom.setText(hasLocation ? getString(R.string.home_flight_from, flight.locationName) : "");
        binding.homeDepartsTime.setText(TripTime.time(flight.startTime));
        binding.homeDepartsDay.setText(day(flight.startTime));
        binding.homeArrivesTime.setText(TripTime.time(flight.endTime));
        binding.homeArrivesDay.setText(day(flight.endTime));
        binding.homeFlightStatus.setVisibility(View.VISIBLE);
        TripUi.styleStatusChip(binding.homeFlightStatus, flight);
        String planned = TripUi.plannedText(requireContext(), flight);
        binding.homeFlightPlanned.setVisibility(planned == null ? View.GONE : View.VISIBLE);
        binding.homeFlightPlanned.setText(planned == null ? "" : planned);
    }

    private int greetingText() {
        switch (HomeGreeting.at(Instant.now().atZone(AppConfig.TRIP_ZONE).toLocalTime())) {
            case MORNING:
                return R.string.home_greeting_morning;
            case AFTERNOON:
                return R.string.home_greeting_afternoon;
            case EVENING:
            default:
                return R.string.home_greeting_evening;
        }
    }

    private String day(Instant instant) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        return TripTime.dayHeader(TripTime.localDate(instant), locale);
    }

    /** Opens Ask OnBoard on top of Home (Back returns here). */
    private void openAsk(boolean startVoice) {
        Bundle arguments = new Bundle();
        arguments.putBoolean(AskFragment.ARG_START_VOICE, startVoice);
        NavHostFragment.findNavController(this).navigate(R.id.navigation_ask, arguments);
    }

    /** Switches tab exactly as if the traveller tapped it in the bottom bar. */
    private void openTab(int tabId) {
        BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(tabId);
        }
    }
}
