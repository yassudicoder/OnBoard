package com.safarsaathi.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.Instant;
import java.util.List;

/**
 * All database reads and writes for trips, blocks and links. Room writes the actual code for every
 * abstract method below while the app builds (class TripDao_Impl).
 *
 * <p>Methods that return {@link LiveData} are for screens: Room runs the query in the background and runs
 * it again by itself whenever one of the tables changes, so the screen always shows the saved data.
 * The other methods block until done, so call them on a background thread only (TripRepository does).
 */
@Dao
public abstract class TripDao {

    // ---- For screens (LiveData, updates by itself) ----

    /** Emits null while no copy of this trip is saved on the phone. */
    @Query("SELECT * FROM trips WHERE id = :tripId")
    public abstract LiveData<TripEntity> observeTrip(long tripId);

    /** In timeline order: by start time, and by id when two blocks start at the same moment. */
    @Query("SELECT * FROM blocks WHERE tripId = :tripId ORDER BY startTime, id")
    public abstract LiveData<List<BlockEntity>> observeBlocks(long tripId);

    @Query("SELECT * FROM links WHERE tripId = :tripId ORDER BY id")
    public abstract LiveData<List<LinkEntity>> observeLinks(long tripId);

    // ---- For background threads (blocking) ----

    /** Returns null when no copy of this trip is saved. */
    @Query("SELECT * FROM trips WHERE id = :tripId")
    public abstract TripEntity getTrip(long tripId);

    @Query("SELECT * FROM blocks WHERE tripId = :tripId ORDER BY startTime, id")
    public abstract List<BlockEntity> getBlocks(long tripId);

    @Query("SELECT * FROM links WHERE tripId = :tripId ORDER BY id")
    public abstract List<LinkEntity> getLinks(long tripId);

    /** Only remembers "we heard from the server just now"; the plan itself stays as it is. */
    @Query("UPDATE trips SET lastSyncedAt = :syncedAt WHERE id = :tripId")
    public abstract void updateLastSyncedAt(long tripId, Instant syncedAt);

    /**
     * Swaps the saved copy of a trip for a new one: the trip row, ALL its blocks and ALL its links.
     *
     * <p>{@code @Transaction} = all of it happens, or none of it. The screen therefore never sees a
     * half-written plan (say, new blocks with old links), even if the app is killed in the middle.
     * Old blocks and links are deleted first, so ones the server no longer has disappear too.
     */
    @Transaction
    public void replaceTrip(TripEntity trip, List<BlockEntity> blocks, List<LinkEntity> links) {
        deleteLinks(trip.id);
        deleteBlocks(trip.id);
        deleteTrip(trip.id);
        insertTrip(trip);
        insertBlocks(blocks);
        insertLinks(links);
    }

    // Building blocks for replaceTrip(). Package-private: other classes should not call them one by one.

    @Query("DELETE FROM links WHERE tripId = :tripId")
    abstract void deleteLinks(long tripId);

    @Query("DELETE FROM blocks WHERE tripId = :tripId")
    abstract void deleteBlocks(long tripId);

    @Query("DELETE FROM trips WHERE id = :tripId")
    abstract void deleteTrip(long tripId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertTrip(TripEntity trip);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertBlocks(List<BlockEntity> blocks);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertLinks(List<LinkEntity> links);
}
