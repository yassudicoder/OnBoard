package com.safarsaathi.app.ui.trip;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.databinding.FragmentTripStatusBinding;
import com.safarsaathi.app.ui.home.HomeGreeting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * PARKED FEATURE (CLAUDE.md "Parked"): trip status, the old Home tab of the trip-timeline plan
 * (layout: res/layout/fragment_trip_status.xml). Only reachable when feature.tripTimeline=true (Profile > More).
 * The trip at a glance for the demo's sample traveller, Aditi.
 *
 * <p>Every trip detail comes from the saved plan in Room, through the same {@link TripViewModel} and repository
 * as the Trip tab, and the state rules live in {@link TripOverview} and {@link TripUi}, which the Trip tab uses
 * too. So the two tabs always agree, and Home works offline exactly like Trip. Nothing about the trip is
 * typed into this screen.
 *
 * <p>The network is only used to refresh the saved plan: when the tab opens (unless another tab just did),
 * when the traveller pulls down or taps Retry, and every 10 seconds while the tab is on screen, so a delay
 * triggered for the demo shows up within seconds.
 */
public class TripStatusFragment extends Fragment {

    private static final String DETAILS_SHEET_TAG = "block-details";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            viewModel.refreshQuietly();
            mainHandler.postDelayed(this, AppConfig.AUTO_REFRESH_INTERVAL_MS);
        }
    };

    // Only valid between onViewCreated and onDestroyView (the fragment can outlive its views).
    private FragmentTripStatusBinding binding;

    private TripViewModel viewModel;

    // The latest values from the ViewModel. render() decides what to show from all of them together.
    private boolean tripLoaded;   // false until Room has answered once (null trip = "no saved copy")
    private boolean blocksLoaded; // false until the blocks have arrived once, so the screen never flashes "no bookings"
    @Nullable
    private TripEntity trip;
    private List<BlockEntity> blocks = Collections.emptyList();
    private SyncState syncState = SyncState.idle();

    // What the two booking lists show now, so they are only rebuilt when they change (not on every sync tick).
    private List<BlockEntity> shownNeedingDecision = Collections.emptyList();
    private List<BlockEntity> shownChanged = Collections.emptyList();

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public TripStatusFragment() {
        super(R.layout.fragment_trip_status);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentTripStatusBinding.bind(view);
        // The same kind of ViewModel as the Trip tab: same repository, same Room data, same sync state.
        viewModel = new ViewModelProvider(this, TripViewModel.factory(AppConfig.DEMO_TRIP_ID))
                .get(TripViewModel.class);
        shownNeedingDecision = Collections.emptyList();
        shownChanged = Collections.emptyList();

        String name = getString(R.string.sample_traveller_name);
        binding.tripStatusAvatar.setText(HomeGreeting.initial(name));
        // Screen readers can jump from heading to heading.
        ViewCompat.setAccessibilityHeading(binding.tripStatusGreeting, true);
        ViewCompat.setAccessibilityHeading(binding.tripStatusNeedsTitle, true);
        ViewCompat.setAccessibilityHeading(binding.tripStatusChangedTitle, true);

        binding.tripStatusRoot.setOnRefreshListener(() -> viewModel.refresh());
        binding.tripStatusRetry.setOnClickListener(button -> viewModel.refresh());
        binding.tripStatusOpenTimeline.setOnClickListener(button -> openTripTab());

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
        // The tab is now visible: refresh (unless another tab just did), then every 10 seconds.
        viewModel.refreshOnOpen();
        mainHandler.removeCallbacks(autoRefresh);
        mainHandler.postDelayed(autoRefresh, AppConfig.AUTO_REFRESH_INTERVAL_MS);
    }

    @Override
    public void onStop() {
        // Another tab, or the app went to the background: stop asking the server.
        mainHandler.removeCallbacks(autoRefresh);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---- Drawing ----

    /** Shows the trip, or the loading / error box, from the latest values. */
    private void render() {
        if (binding == null) {
            return;
        }
        Context context = requireContext();
        String name = getString(R.string.sample_traveller_name);
        binding.tripStatusGreeting.setText(getString(greetingText(), name));

        if (syncState.kind != SyncState.Kind.REFRESHING) {
            binding.tripStatusRoot.setRefreshing(false); // stop the pull-to-refresh spinner
        }
        String syncLine = TripUi.syncLine(context, syncState, trip == null ? null : trip.lastSyncedAt);
        binding.tripStatusSync.setText(syncLine);
        binding.tripStatusSync.setVisibility(syncLine.isEmpty() ? View.GONE : View.VISIBLE);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(binding.tripStatusSync,
                syncState.kind == SyncState.Kind.OFFLINE ? R.drawable.ic_offline_24 : 0, 0, 0, 0);

        boolean showTrip = trip != null && blocksLoaded;
        binding.tripStatusContent.setVisibility(showTrip ? View.VISIBLE : View.GONE);
        binding.tripStatusStateBox.setVisibility(showTrip ? View.GONE : View.VISIBLE);
        if (showTrip) {
            renderTrip(trip);
        } else {
            renderNoSavedPlan();
        }
    }

    /** No plan saved on the phone yet: "Loading your trip…" while it is fetched, otherwise why it failed + Retry. */
    private void renderNoSavedPlan() {
        boolean failed = tripLoaded && syncState.kind == SyncState.Kind.OFFLINE;
        binding.tripStatusProgress.setVisibility(failed ? View.GONE : View.VISIBLE);
        binding.tripStatusStateIcon.setVisibility(failed ? View.VISIBLE : View.GONE);
        binding.tripStatusStateTitle.setText(failed ? R.string.trip_empty_error_title : R.string.trip_empty_loading_title);
        binding.tripStatusStateMessage.setText(failed
                ? TripUi.emptyStateMessage(syncState.problem)
                : R.string.trip_empty_loading_message);
        binding.tripStatusRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
    }

    private void renderTrip(TripEntity currentTrip) {
        TripOverview overview = TripOverview.of(blocks, Instant.now());
        renderStatusCard(overview);

        binding.tripStatusTripTitle.setText(currentTrip.title);
        binding.tripStatusTripSummary.setText(TripUi.tripSummary(requireContext(), currentTrip, blocks));
        renderFlight(overview.flight);

        // The flight already has its own card above, so it is not listed again under "Changed for you".
        List<BlockEntity> changed = new ArrayList<>(overview.changed);
        if (overview.flight != null) {
            changed.remove(overview.flight);
        }
        if (!overview.needingDecision.equals(shownNeedingDecision)) {
            shownNeedingDecision = overview.needingDecision;
            fillList(binding.tripStatusNeedsList, shownNeedingDecision);
        }
        if (!changed.equals(shownChanged)) {
            shownChanged = changed;
            fillList(binding.tripStatusChangedList, shownChanged);
        }
        binding.tripStatusNeedsTitle.setVisibility(shownNeedingDecision.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tripStatusChangedTitle.setVisibility(shownChanged.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The big card: the trip's state in one line, what it means, and one button. */
    private void renderStatusCard(TripOverview overview) {
        Context context = requireContext();
        @ColorRes int fill;
        @ColorRes int onFill;
        @DrawableRes int icon;
        String title;
        List<String> lines = new ArrayList<>();
        BlockEntity flight = overview.flight;
        String flightLine = flight == null ? null : flightMovedLine(flight);

        switch (overview.state) {
            case NEEDS_DECISION: {
                fill = R.color.status_needs_user_container;
                onFill = R.color.status_needs_user_on_container;
                icon = R.drawable.ic_warning_24;
                int count = overview.needingDecision.size();
                title = getResources().getQuantityString(R.plurals.home_state_needs_decision, count, count);
                lines.add(getString(R.string.home_decide_on, titles(overview.needingDecision)));
                addIfPresent(lines, flightLine);
                break;
            }
            case CHANGED:
                fill = R.color.status_adjusted_container;
                onFill = R.color.status_adjusted_on_container;
                icon = R.drawable.ic_schedule_24;
                title = getString(R.string.home_state_changed);
                addIfPresent(lines, flightLine);
                if (overview.autoAdjustedCount > 0) {
                    lines.add(getResources().getQuantityString(R.plurals.home_moved_for_you,
                            overview.autoAdjustedCount, overview.autoAdjustedCount));
                }
                if (overview.cancelledCount > 0) {
                    lines.add(getResources().getQuantityString(R.plurals.home_cancelled,
                            overview.cancelledCount, overview.cancelledCount));
                }
                break;
            case NO_BOOKINGS:
                fill = R.color.status_done_container;
                onFill = R.color.status_done_on_container;
                icon = R.drawable.ic_trip_24;
                title = getString(R.string.home_state_no_bookings);
                lines.add(getString(R.string.home_state_no_bookings_detail));
                break;
            case ON_TRACK:
            default:
                fill = R.color.status_ok_container;
                onFill = R.color.status_ok_on_container;
                icon = R.drawable.ic_check_circle_24;
                title = getString(R.string.home_state_on_track);
                lines.add(overview.nextUp == null
                        ? getString(R.string.home_nothing_next)
                        : getString(R.string.home_next_step, overview.nextUp.title, dayAndTime(overview.nextUp.startTime)));
                break;
        }

        int fillColor = ContextCompat.getColor(context, fill);
        int onFillColor = ContextCompat.getColor(context, onFill);
        binding.tripStatusStatusCard.setCardBackgroundColor(fillColor);
        binding.tripStatusStatusIcon.setImageResource(icon);
        binding.tripStatusStatusIcon.setImageTintList(ColorStateList.valueOf(onFillColor));
        binding.tripStatusStatusTitle.setText(title);
        binding.tripStatusStatusTitle.setTextColor(onFillColor);
        binding.tripStatusStatusDetail.setText(String.join("\n", lines));
        binding.tripStatusStatusDetail.setTextColor(onFillColor);

        // One button: open the first booking that needs a decision, or the whole trip. Drawn in the card's
        // text colour with the card's fill as its text, so it stands out on every state colour.
        binding.tripStatusStatusAction.setBackgroundTintList(ColorStateList.valueOf(onFillColor));
        binding.tripStatusStatusAction.setTextColor(fillColor);
        if (overview.state == TripOverview.State.NEEDS_DECISION) {
            long firstId = overview.needingDecision.get(0).id;
            binding.tripStatusStatusAction.setText(R.string.home_action_see_details);
            binding.tripStatusStatusAction.setOnClickListener(button -> showBlockDetails(firstId));
            binding.tripStatusStatusAction.setVisibility(View.VISIBLE);
        } else if (overview.state == TripOverview.State.NO_BOOKINGS) {
            binding.tripStatusStatusAction.setVisibility(View.GONE);
        } else {
            binding.tripStatusStatusAction.setText(R.string.home_action_view_trip);
            binding.tripStatusStatusAction.setOnClickListener(button -> openTripTab());
            binding.tripStatusStatusAction.setVisibility(View.VISIBLE);
        }
    }

    /** The flight part of the trip card; hidden when the trip has no flight. */
    private void renderFlight(@Nullable BlockEntity flight) {
        int visibility = flight == null ? View.GONE : View.VISIBLE;
        binding.tripStatusFlightDivider.setVisibility(visibility);
        binding.tripStatusFlight.setVisibility(visibility);
        if (flight == null) {
            return;
        }
        Context context = requireContext();
        binding.tripStatusFlightTitle.setText(flight.title);
        binding.tripStatusDepartsTime.setText(TripTime.time(flight.startTime));
        binding.tripStatusDepartsDay.setText(day(flight.startTime));
        binding.tripStatusArrivesTime.setText(TripTime.time(flight.endTime));
        binding.tripStatusArrivesDay.setText(day(flight.endTime));
        TripUi.styleStatusChip(binding.tripStatusFlightStatus, flight);

        String planned = TripUi.plannedText(context, flight);
        binding.tripStatusFlightPlanned.setVisibility(planned == null ? View.GONE : View.VISIBLE);
        if (planned != null) {
            int fill = ContextCompat.getColor(context, R.color.status_adjusted_container);
            int onFill = ContextCompat.getColor(context, R.color.status_adjusted_on_container);
            binding.tripStatusFlightPlanned.setText(planned);
            binding.tripStatusFlightPlanned.setBackgroundTintList(ColorStateList.valueOf(fill));
            binding.tripStatusFlightPlanned.setTextColor(onFill);
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(binding.tripStatusFlightPlanned,
                    R.drawable.ic_schedule_24, 0, 0, 0);
            TextViewCompat.setCompoundDrawableTintList(binding.tripStatusFlightPlanned, ColorStateList.valueOf(onFill));
        }

        boolean hasLocation = flight.locationName != null && !flight.locationName.trim().isEmpty();
        binding.tripStatusFlightFrom.setVisibility(hasLocation ? View.VISIBLE : View.GONE);
        binding.tripStatusFlightFrom.setText(hasLocation ? getString(R.string.home_flight_from, flight.locationName) : "");
        long flightId = flight.id;
        binding.tripStatusFlight.setOnClickListener(view -> showBlockDetails(flightId));
    }

    /** Fills a list with the Trip tab's booking cards (tap = details). */
    private void fillList(LinearLayout list, List<BlockEntity> listBlocks) {
        list.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (BlockEntity block : listBlocks) {
            list.addView(BlockCards.create(inflater, list, block, this::showBlockDetails));
        }
    }

    // ---- Text helpers ----

    /** "Flight IJ 512 ... now leaves 2 hours later than planned." or null when the flight did not move. */
    @Nullable
    private String flightMovedLine(BlockEntity flight) {
        long moved = TripOverview.minutesMoved(flight);
        if (moved == 0) {
            return null;
        }
        String howMuch = TripUi.duration(requireContext(), (int) Math.abs(moved));
        return getString(moved > 0 ? R.string.home_flight_later : R.string.home_flight_earlier, flight.title, howMuch);
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

    /** "Sun, 11 Oct" in the screen's language. */
    private String day(Instant instant) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        return TripTime.dayHeader(TripTime.localDate(instant), locale);
    }

    /** "Sun, 11 Oct, 08:00" */
    private String dayAndTime(Instant instant) {
        return getString(R.string.home_day_time, day(instant), TripTime.time(instant));
    }

    private static String titles(List<BlockEntity> list) {
        List<String> titles = new ArrayList<>();
        for (BlockEntity block : list) {
            titles.add(block.title);
        }
        return String.join(", ", titles);
    }

    private static void addIfPresent(List<String> lines, @Nullable String line) {
        if (line != null) {
            lines.add(line);
        }
    }

    // ---- Navigation ----

    /** Opens the details sheet of one booking (the same sheet as on the Trip tab). */
    private void showBlockDetails(long blockId) {
        FragmentManager fragments = getChildFragmentManager();
        if (fragments.isStateSaved() || fragments.findFragmentByTag(DETAILS_SHEET_TAG) != null) {
            return;
        }
        BlockDetailsSheet.newInstance(blockId).show(fragments, DETAILS_SHEET_TAG);
    }

    /** Opens the (parked) trip timeline screen. */
    private void openTripTab() {
        Navigation.findNavController(requireView()).navigate(R.id.navigation_trip);
    }
}
