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
import com.google.android.material.color.MaterialColors;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.data.ride.RidePlace;
import com.safarsaathi.app.data.ride.RidePlacement;
import com.safarsaathi.app.data.ride.RidePoints;
import com.safarsaathi.app.data.ride.RideProvider;
import com.safarsaathi.app.data.ride.RideRequest;
import com.safarsaathi.app.databinding.FragmentHomeBinding;
import com.safarsaathi.app.ui.ask.AskFragment;
import com.safarsaathi.app.ui.navigate.GoogleMapsLink;
import com.safarsaathi.app.ui.ride.RidePlaceDialog;
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
 *
 * <p>Ride handoff (Uber deep links, see {@link #renderRides}): "Leave by … (estimate)" + a ride to the terminal on
 * the flight card, and "After you land" with a ride to the hotel. (The ride to another airport for a Disruption
 * Recovery alternative is added once those screens exist, after C3 merges.)
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
    /** A place the traveller typed or picked for the "After you land" ride; null = the hotel from the trip. */
    @Nullable
    private RidePlace arrivalChoice;

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
        binding.homePlanJourney.setOnClickListener(button ->
                androidx.navigation.Navigation.findNavController(view).navigate(R.id.navigation_journey));
        // The departure ride sits on the coloured flight card, so its notes use that card's text colour.
        binding.homeRideDeparture.setTextColor(MaterialColors.getColor(binding.homeFlightCard,
                com.google.android.material.R.attr.colorOnPrimaryContainer));
        binding.homeArrivalChoose.setOnClickListener(button -> RidePlaceDialog.show(requireContext(), address -> {
            arrivalChoice = RidePlace.typed(address);
            render();
        }));
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
            renderRides(flight);
            // "After you land" belongs to a trip: hidden until one is picked (Plan a journey).
            binding.homeArrivalCard.setVisibility(
                    com.safarsaathi.app.data.journey.ChosenJourney.hasTrip(requireContext()) ? View.VISIBLE : View.GONE);
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
        // No ticket until the traveller picks a journey or finds a booking ("Plan a journey").
        boolean hasTrip = com.safarsaathi.app.data.journey.ChosenJourney.hasTrip(requireContext());
        binding.homeFlightCard.setVisibility(hasTrip ? View.VISIBLE : View.GONE);
        binding.homePlanJourney.setText(hasTrip ? R.string.journey_home_button : R.string.journey_home_first);
        if (!hasTrip) {
            return;
        }
        // A SAMPLE journey picked in "Plan a journey" replaces the server's demo flight on the card.
        com.safarsaathi.app.data.journey.ChosenJourney chosen =
                com.safarsaathi.app.data.journey.ChosenJourney.load(requireContext());
        if (chosen != null) {
            java.time.format.DateTimeFormatter hhmm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            java.time.format.DateTimeFormatter dayFormat =
                    java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.ENGLISH);
            binding.homeFlightTitle.setText(getString(R.string.journey_home_title, chosen.flightNo,
                    chosen.from.name, chosen.to.name));
            binding.homeFlightFrom.setVisibility(View.VISIBLE);
            binding.homeFlightFrom.setText(getString(R.string.home_flight_from,
                    chosen.from.airport + " (" + chosen.from.code + ")"));
            binding.homeDepartsTime.setText(hhmm.format(chosen.departs));
            binding.homeDepartsDay.setText(dayFormat.format(chosen.departs));
            binding.homeArrivesTime.setText(hhmm.format(chosen.arrives));
            binding.homeArrivesDay.setText(dayFormat.format(chosen.arrives));
            binding.homeFlightStatus.setVisibility(View.GONE);
            binding.homeFlightPlanned.setVisibility(View.VISIBLE);
            binding.homeFlightPlanned.setText(R.string.journey_home_note);
            return;
        }
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

    /**
     * The two ride buttons (Uber deep links, data/ride). Pickup is always "my location": Uber finds the phone
     * itself, so OnBoard needs no location permission. Places and times come from ride-points.json (airport points,
     * labelled "approximate" until the team checks them) and from the trip (the hotel); times say "estimate".
     */
    private void renderRides(@Nullable BlockEntity flight) {
        RideProvider provider = ServiceLocator.get().getRideProvider();
        RidePoints points = ServiceLocator.get().getRidePoints();
        boolean cancelled = flight != null && BlockEntity.STATUS_CANCELLED.equals(flight.status);

        // a. Departure: "Leave by 06:10 (estimate)" and a ride to the pilot terminal's drop-off point. Hidden when
        //    the flight is cancelled: there is no point riding to it.
        RidePoints.Point terminal = points.dropoff(AppConfig.PILOT_AIRPORT, AppConfig.PILOT_TERMINAL);
        String terminalName = terminal != null ? terminal.buttonName()
                : AppConfig.PILOT_AIRPORT + " " + AppConfig.PILOT_TERMINAL;
        boolean showDeparture = flight != null && !cancelled;
        binding.homeRideDeparture.setVisibility(showDeparture ? View.VISIBLE : View.GONE);
        binding.homeRideDeparture.bind(provider, new RideRequest(RidePlacement.DEPARTURE, RidePlace.myLocation(),
                terminal == null ? null : terminal.toPlace()), getString(R.string.ride_get_to, terminalName));
        renderLeaveBy(showDeparture ? flight : null,
                points.leaveByRule(AppConfig.PILOT_AIRPORT, AppConfig.PILOT_TERMINAL));

        // b. After you land: a place the traveller chose, else the hotel from the trip, else nothing (disabled).
        BlockEntity hotel = firstHotel();
        RidePlace dropoff = null;
        String placeLine = getString(R.string.home_arrival_none);
        int buttonText = R.string.ride_to_hotel;
        if (arrivalChoice != null) {
            dropoff = arrivalChoice;
            placeLine = getString(R.string.home_arrival_typed, arrivalChoice.label());
            buttonText = R.string.ride_to_destination;
        } else if (hotel != null) {
            dropoff = RidePlace.point(hotel.locationName, null, hotel.lat, hotel.lng, false);
            String name = hotel.locationName != null ? hotel.locationName : hotel.title;
            placeLine = getString(R.string.home_arrival_hotel, name);
        }
        binding.homeArrivalPlace.setText(placeLine);
        binding.homeArrivalChoose.setText(dropoff == null ? R.string.ride_choose_place : R.string.ride_somewhere_else);
        binding.homeRideArrival.bind(provider, new RideRequest(RidePlacement.ARRIVAL, RidePlace.myLocation(), dropoff),
                getString(buttonText));
    }

    /** "Leave by HH:mm (estimate)" with how it was worked out; hidden without a rule or once that time has passed. */
    private void renderLeaveBy(@Nullable BlockEntity flight, @Nullable RidePoints.LeaveByRule rule) {
        Instant leaveBy = flight == null || rule == null ? null : rule.leaveBy(flight.startTime);
        boolean show = leaveBy != null && leaveBy.isAfter(Instant.now());
        binding.homeLeaveBy.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.homeLeaveByDetail.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.homeLeaveBy.setText(getString(R.string.ride_leave_by, TripTime.time(leaveBy)));
            binding.homeLeaveByDetail.setText(getString(R.string.ride_leave_by_detail, rule.rideEstimateMin,
                    rule.atAirportBeforeDepartureMin));
        }
    }

    /** The trip's first hotel booking that says where it is, or null. */
    @Nullable
    private BlockEntity firstHotel() {
        for (BlockEntity block : blocks) {
            boolean located = (block.locationName != null && !block.locationName.trim().isEmpty())
                    || (block.lat != null && block.lng != null);
            if (BlockEntity.TYPE_HOTEL.equals(block.type) && !BlockEntity.STATUS_CANCELLED.equals(block.status)
                    && located) {
                return block;
            }
        }
        return null;
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
