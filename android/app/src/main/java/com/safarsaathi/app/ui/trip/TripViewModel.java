package com.safarsaathi.app.ui.trip;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.TripRepository;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;
import com.safarsaathi.app.data.local.TripEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Data for the Trip tab and the Home tab. Each tab has its own instance, but both read the same Room data
 * through the same TripRepository, so they always agree. A ViewModel survives screen rotation, so the list is
 * not reloaded on every turn of the phone. Everything here comes from Room (through TripRepository); the screen
 * never talks to the network itself, it only asks for a refresh.
 */
public class TripViewModel extends ViewModel {

    private final TripRepository repository;
    private final long tripId;

    private final LiveData<TripEntity> trip;
    private final LiveData<List<BlockEntity>> blocks;
    private final MediatorLiveData<List<TripRow>> rows = new MediatorLiveData<>();

    // Latest blocks and links. The rows are rebuilt from both whenever either one changes.
    private List<BlockEntity> latestBlocks;
    private List<LinkEntity> latestLinks;

    public TripViewModel(TripRepository repository, long tripId) {
        this.repository = repository;
        this.tripId = tripId;
        trip = repository.observeTrip(tripId);
        blocks = repository.observeBlocks(tripId);
        LiveData<List<LinkEntity>> links = repository.observeLinks(tripId);

        rows.addSource(blocks, newBlocks -> {
            latestBlocks = newBlocks == null ? Collections.emptyList() : newBlocks;
            rebuildRows();
        });
        rows.addSource(links, newLinks -> {
            latestLinks = newLinks == null ? Collections.emptyList() : newLinks;
            rebuildRows();
        });
    }

    /** Waits until both blocks and links have loaded once, so the list does not first appear without its link rows. */
    private void rebuildRows() {
        if (latestBlocks != null && latestLinks != null) {
            rows.setValue(TripRowBuilder.build(latestBlocks, latestLinks));
        }
    }

    /** The saved trip, or null while the phone has no copy of it. */
    public LiveData<TripEntity> getTrip() {
        return trip;
    }

    /** The saved blocks in timeline order (for the banner and the details sheet). */
    public LiveData<List<BlockEntity>> getBlocks() {
        return blocks;
    }

    /** The timeline rows (day headers, booking cards, link rows). */
    public LiveData<List<TripRow>> getRows() {
        return rows;
    }

    public LiveData<SyncState> getSyncState() {
        return repository.getSyncState();
    }

    /** The traveller asked for fresh data (screen opened, pulled down, Retry): show "Refreshing…". */
    public void refresh() {
        repository.refresh(tripId, true);
    }

    /**
     * A tab just opened: refresh, unless the phone heard from the server less than one refresh interval ago
     * (e.g. switching from Home to Trip), so switching tabs does not send a request each time.
     */
    public void refreshOnOpen() {
        SyncState state = repository.getSyncState().getValue();
        boolean justSynced = state != null
                && state.kind == SyncState.Kind.UP_TO_DATE
                && state.lastSyncedAt != null
                && Duration.between(state.lastSyncedAt, Instant.now()).toMillis() < AppConfig.AUTO_REFRESH_INTERVAL_MS;
        if (!justSynced) {
            repository.refresh(tripId, true);
        }
    }

    /** The 10-second timer: check for news without changing the sync line while the request runs. */
    public void refreshQuietly() {
        repository.refresh(tripId, false);
    }

    /**
     * How Android creates this ViewModel: it needs a repository and a trip id, which a plain
     * no-argument constructor could not get. The repository comes from the ServiceLocator, so a test can
     * swap it (ServiceLocator.setTripRepositoryForTests).
     */
    public static ViewModelProvider.Factory factory(long tripId) {
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (modelClass.isAssignableFrom(TripViewModel.class)) {
                    return (T) new TripViewModel(ServiceLocator.get().getTripRepository(), tripId);
                }
                throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
            }
        };
    }
}
