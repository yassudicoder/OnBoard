package com.safarsaathi.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;

/** Reads and writes the saved airport maps. LiveData for screens; the others block (call them on a background thread). */
@Dao
public abstract class AirportMapDao {

    /** Emits null while no copy of this terminal's map is saved. */
    @Query("SELECT * FROM airport_maps WHERE airport = :airport AND terminal = :terminal")
    public abstract LiveData<AirportMapEntity> observe(String airport, String terminal);

    @Query("SELECT * FROM airport_maps WHERE airport = :airport AND terminal = :terminal")
    public abstract AirportMapEntity get(String airport, String terminal);

    /** Saves the map, replacing the saved copy of the same terminal. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void save(AirportMapEntity map);

    /** Only remembers "the server says this is still the newest map". */
    @Query("UPDATE airport_maps SET checkedAt = :checkedAt WHERE airport = :airport AND terminal = :terminal")
    public abstract void markChecked(String airport, String terminal, Instant checkedAt);
}
