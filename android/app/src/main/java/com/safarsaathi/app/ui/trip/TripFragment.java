package com.safarsaathi.app.ui.trip;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.databinding.FragmentTripBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Trip tab, "My trip": the whole trip as a timeline (layout: res/layout/fragment_trip.xml).
 *
 * <p>What it shows always comes from Room (the plan saved on the phone), so it also works offline.
 * The network is only used to refresh that saved plan:
 * <ul>
 *   <li>when the tab opens, when the traveller pulls the list down, and on "Retry";</li>
 *   <li>every 10 seconds while the tab is on screen, so a delay triggered on the backend during the demo
 *       turns the cards amber/red within seconds. The timer stops as soon as the tab is not visible.</li>
 * </ul>
 */
public class TripFragment extends Fragment {

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
    private FragmentTripBinding binding;
    private TripHeaderAdapter headerAdapter;
    private TripTimelineAdapter timelineAdapter;

    private TripViewModel viewModel;

    // The latest values from the ViewModel. render() decides what to show from all of them together.
    private boolean tripLoaded; // false until Room has answered once (null trip = "no saved copy")
    @Nullable
    private TripEntity trip;
    private List<BlockEntity> blocks = Collections.emptyList();
    private SyncState syncState = SyncState.idle();

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public TripFragment() {
        super(R.layout.fragment_trip);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentTripBinding.bind(view);
        viewModel = new ViewModelProvider(this, TripViewModel.factory(AppConfig.DEMO_TRIP_ID))
                .get(TripViewModel.class);

        // The list = header row + timeline rows, glued together by a ConcatAdapter.
        headerAdapter = new TripHeaderAdapter();
        timelineAdapter = new TripTimelineAdapter(this::showBlockDetails);
        binding.tripList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.tripList.setAdapter(new ConcatAdapter(headerAdapter, timelineAdapter));
        // No cross-fade when a row changes: the sync line changes often and should not blink.
        RecyclerView.ItemAnimator animator = binding.tripList.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        binding.tripSwipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.tripRetry.setOnClickListener(button -> viewModel.refresh());
        binding.tripAttentionBanner.setOnClickListener(banner -> openFirstBlockNeedingDecision());

        // getViewLifecycleOwner(): observers stop automatically when the views are destroyed.
        LifecycleOwner owner = getViewLifecycleOwner();
        viewModel.getTrip().observe(owner, newTrip -> {
            tripLoaded = true;
            trip = newTrip;
            render();
        });
        viewModel.getBlocks().observe(owner, newBlocks -> {
            blocks = newBlocks == null ? Collections.emptyList() : newBlocks;
            render();
        });
        viewModel.getRows().observe(owner, rows -> timelineAdapter.submitList(rows));
        viewModel.getSyncState().observe(owner, newState -> {
            syncState = newState;
            render();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // The tab is now visible: refresh once now, then every 10 seconds.
        viewModel.refresh();
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
        // Let go of the views (and the adapters that hold views): the fragment may live on without them.
        binding.tripList.setAdapter(null);
        binding = null;
        headerAdapter = null;
        timelineAdapter = null;
        super.onDestroyView();
    }

    /** Shows the list or the empty state, the banner and the header, from the latest values. */
    private void render() {
        if (binding == null) {
            return;
        }
        boolean hasTrip = trip != null;
        binding.tripSwipeRefresh.setVisibility(hasTrip ? View.VISIBLE : View.GONE);
        binding.tripEmptyState.setVisibility(!hasTrip && tripLoaded ? View.VISIBLE : View.GONE);
        if (syncState.kind != SyncState.Kind.REFRESHING) {
            binding.tripSwipeRefresh.setRefreshing(false); // stop the pull-to-refresh spinner
        }

        if (hasTrip) {
            renderHeader(trip);
            renderBanner();
        } else {
            headerAdapter.setHeader(null);
            binding.tripAttentionBanner.setVisibility(View.GONE);
            renderEmptyState();
        }
    }

    private void renderHeader(TripEntity currentTrip) {
        String purpose = getString(TripUi.purposeLabel(currentTrip.purpose));
        String summary;
        if (blocks.isEmpty()) {
            summary = purpose;
        } else {
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            String firstDay = TripTime.dayHeader(TripTime.localDate(blocks.get(0).startTime), locale);
            String count = getResources().getQuantityString(R.plurals.trip_bookings, blocks.size(), blocks.size());
            summary = getString(R.string.trip_summary, purpose, firstDay, count);
        }
        String syncLine = TripUi.syncLine(requireContext(), syncState, currentTrip.lastSyncedAt);
        boolean offline = syncState.kind == SyncState.Kind.OFFLINE;
        headerAdapter.setHeader(new TripHeaderAdapter.Header(currentTrip.title, summary, syncLine, offline));
    }

    /** The red banner: "1 thing needs your decision: Client meeting in Whitefield". */
    private void renderBanner() {
        List<BlockEntity> needDecision = TripRowBuilder.needingDecision(blocks);
        if (needDecision.isEmpty()) {
            binding.tripAttentionBanner.setVisibility(View.GONE);
            return;
        }
        List<String> titles = new ArrayList<>();
        for (BlockEntity block : needDecision) {
            titles.add(block.title);
        }
        int count = needDecision.size();
        binding.tripAttentionTitle.setText(getResources().getQuantityString(
                R.plurals.trip_attention_title, count, count, String.join(", ", titles)));
        binding.tripAttentionBanner.setVisibility(View.VISIBLE);
    }

    /** No saved plan yet: "Loading your trip…" while the first refresh runs, otherwise why it failed + Retry. */
    private void renderEmptyState() {
        boolean failed = syncState.kind == SyncState.Kind.OFFLINE;
        binding.tripEmptyTitle.setText(failed ? R.string.trip_empty_error_title : R.string.trip_empty_loading_title);
        binding.tripEmptyMessage.setText(failed
                ? TripUi.emptyStateMessage(syncState.problem)
                : R.string.trip_empty_loading_message);
        binding.tripRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
    }

    private void openFirstBlockNeedingDecision() {
        List<BlockEntity> needDecision = TripRowBuilder.needingDecision(blocks);
        if (!needDecision.isEmpty()) {
            showBlockDetails(needDecision.get(0).id);
        }
    }

    /** Opens the details sheet for one block (not twice, and not while the screen is being saved). */
    private void showBlockDetails(long blockId) {
        FragmentManager fragments = getChildFragmentManager();
        if (fragments.isStateSaved() || fragments.findFragmentByTag(DETAILS_SHEET_TAG) != null) {
            return;
        }
        BlockDetailsSheet.newInstance(blockId).show(fragments, DETAILS_SHEET_TAG);
    }
}
