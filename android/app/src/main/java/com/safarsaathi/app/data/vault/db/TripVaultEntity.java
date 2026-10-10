package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.time.LocalDate;

/** One trip folder: all the documents for one journey. Its legs, countries and travellers have their own tables. */
@Entity(tableName = "trips")
public class TripVaultEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    /** e.g. "Delhi → Kigali → Kinshasa". */
    @NonNull
    public String title = "";

    @Nullable
    public LocalDate startDate;

    @Nullable
    public LocalDate endDate;

    /** When the "make offline-ready" test last passed (V6); null = not done yet. */
    @Nullable
    public Instant offlineReadyAt;

    /** Total size of the trip's files at that moment (V6). */
    public long sizeBytes;

    /** True for the made-up demo trip (shown with a SAMPLE label). */
    public boolean sample;

    @NonNull
    public Instant createdAt = Instant.EPOCH;
}
