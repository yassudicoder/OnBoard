package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.time.LocalDate;

/** A person whose documents are in the vault (the phone's owner, or family travelling with them). */
@Entity(tableName = "travellers")
public class TravellerEntity {

    /** A random UUID, so ids never clash when a backup is restored (V6). */
    @PrimaryKey
    @NonNull
    public String id = "";

    /** Full name as printed in the passport. */
    @NonNull
    public String name = "";

    /** Nationality as a 2-letter country code (ISO 3166-1 alpha-2), e.g. "IN". */
    @Nullable
    public String nationality;

    @Nullable
    public LocalDate dateOfBirth;

    /** True for the demo traveller the app makes up (shown with a SAMPLE label). */
    public boolean sample;

    @NonNull
    public Instant createdAt = Instant.EPOCH;
}
