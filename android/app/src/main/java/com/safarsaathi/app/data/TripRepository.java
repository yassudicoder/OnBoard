package com.safarsaathi.app.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;
import com.safarsaathi.app.data.local.TripDao;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.data.remote.SafarSaathiApi;
import com.safarsaathi.app.data.remote.TripDto;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The ONE place screens get trip data from. It hides where the data lives:
 * <ul>
 *   <li><b>Reading</b> always comes from Room (the copy on the phone), as LiveData. So the Trip tab works
 *       the same with or without internet.</li>
 *   <li><b>{@link #refresh}</b> asks the backend for the latest plan and, following the sync rule
 *       ({@link SyncDecision}), overwrites the Room copy. Room then updates every screen by itself.</li>
 * </ul>
 *
 * <p>Threads: Retrofit does the network call on its own background thread ("enqueue"). Saving to Room
 * happens on ONE background thread (ioExecutor), so two refreshes can never write at the same time and
 * nothing slow ever runs on the main (screen) thread.
 */
public class TripRepository {

    private static final String TAG = "SafarSaathi";

    private final TripDao tripDao;
    private final SafarSaathiApi api;
    private final Executor ioExecutor;
    private final Clock clock;

    private final MutableLiveData<SyncState> syncState = new MutableLiveData<>(SyncState.idle());
    /** True while a request is running, so the 10-second timer and pull-to-refresh do not pile up requests. */
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    /**
     * @param ioExecutor runs every database write; must be a single background thread
     * @param clock      where "now" comes from (a fixed clock in tests)
     */
    public TripRepository(TripDao tripDao, SafarSaathiApi api, Executor ioExecutor, Clock clock) {
        this.tripDao = tripDao;
        this.api = api;
        this.ioExecutor = ioExecutor;
        this.clock = clock;
    }

    /** The saved trip; null while the phone has no copy of it. */
    public LiveData<TripEntity> observeTrip(long tripId) {
        return tripDao.observeTrip(tripId);
    }

    /** The saved blocks, in timeline order (start time, then id). */
    public LiveData<List<BlockEntity>> observeBlocks(long tripId) {
        return tripDao.observeBlocks(tripId);
    }

    public LiveData<List<LinkEntity>> observeLinks(long tripId) {
        return tripDao.observeLinks(tripId);
    }

    /** The outcome of the latest refresh (for the "Updated 17:42" / "Offline" line). */
    public LiveData<SyncState> getSyncState() {
        return syncState;
    }

    /** Same as {@code refresh(tripId, true)}: a refresh the traveller asked for. */
    public void refresh(long tripId) {
        refresh(tripId, true);
    }

    /**
     * Asks the server for the latest plan of this trip. Returns at once; the result arrives through
     * Room (new plan) and {@link #getSyncState()} (how it went). Safe to call from the main thread.
     * If a refresh is already running, this call does nothing: that one's result is coming anyway.
     *
     * @param showProgress true when the traveller asked (screen opened, pulled down, Retry): the sync
     *                     line then says "Refreshing…". The 10-second timer passes false, so the line
     *                     does not flicker every 10 seconds.
     */
    public void refresh(long tripId, boolean showProgress) {
        if (!refreshRunning.compareAndSet(false, true)) {
            return;
        }
        if (showProgress) {
            syncState.postValue(SyncState.refreshing());
        }
        try {
            api.getTrip(tripId).enqueue(new Callback<TripDto>() {
                @Override
                public void onResponse(@NonNull Call<TripDto> call, @NonNull Response<TripDto> response) {
                    ioExecutor.execute(() -> saveAnswer(tripId, response));
                }

                @Override
                public void onFailure(@NonNull Call<TripDto> call, @NonNull Throwable error) {
                    // IOException = the network itself failed. Anything else = the JSON could not be read.
                    SyncState.Problem problem = error instanceof IOException
                            ? SyncState.Problem.NO_CONNECTION
                            : SyncState.Problem.BAD_DATA;
                    ioExecutor.execute(() -> finishWithProblem(tripId, problem, error.toString()));
                }
            });
        } catch (RuntimeException e) {
            ioExecutor.execute(() -> finishWithProblem(tripId, SyncState.Problem.NO_CONNECTION, e.toString()));
        }
    }

    /** Runs on ioExecutor: checks the server's answer and saves it following the sync rule. */
    private void saveAnswer(long tripId, Response<TripDto> response) {
        if (!response.isSuccessful()) {
            SyncState.Problem problem = response.code() == 404
                    ? SyncState.Problem.NOT_FOUND
                    : SyncState.Problem.SERVER_ERROR;
            finishWithProblem(tripId, problem, "HTTP " + response.code());
            return;
        }
        try {
            TripMapper.MappedTrip fresh = TripMapper.fromDto(response.body());
            if (fresh.trip.id != tripId) {
                finishWithProblem(tripId, SyncState.Problem.BAD_DATA,
                        "asked for trip " + tripId + " but got trip " + fresh.trip.id);
                return;
            }
            Instant now = clock.instant();
            TripEntity saved = tripDao.getTrip(tripId);
            if (SyncDecision.shouldReplace(saved, fresh.trip)) {
                // Carry over each block's originally planned times (or set them, for a trip seen at version 1).
                List<BlockEntity> savedBlocks = saved == null ? Collections.emptyList() : tripDao.getBlocks(tripId);
                PlannedTimes.fill(saved, savedBlocks, fresh.trip, fresh.blocks);
                fresh.trip.lastSyncedAt = now;
                tripDao.replaceTrip(fresh.trip, fresh.blocks, fresh.links);
                Log.i(TAG, "Saved " + fresh.trip + " with " + fresh.blocks.size() + " blocks");
            } else {
                tripDao.updateLastSyncedAt(tripId, now);
            }
            finish(SyncState.upToDate(now));
        } catch (RuntimeException e) {
            // Bad data from the server (TripMapper) or a database problem: keep the saved plan.
            finishWithProblem(tripId, SyncState.Problem.BAD_DATA, e.toString());
        }
    }

    /** Runs on ioExecutor: reports a failed refresh, with the time of the plan still saved on the phone. */
    private void finishWithProblem(long tripId, SyncState.Problem problem, String detail) {
        Log.w(TAG, "Could not refresh trip " + tripId + " (" + problem + "): " + detail);
        Instant lastSyncedAt = null;
        try {
            TripEntity saved = tripDao.getTrip(tripId);
            lastSyncedAt = saved == null ? null : saved.lastSyncedAt;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not read the saved trip", e);
        }
        finish(SyncState.offline(problem, detail, lastSyncedAt));
    }

    /**
     * Ends a refresh. The "running" flag is cleared BEFORE the result is published: anyone who saw the
     * flag still set (and skipped their own refresh) is then guaranteed to receive this result.
     */
    private void finish(SyncState result) {
        refreshRunning.set(false);
        syncState.postValue(result);
    }
}
