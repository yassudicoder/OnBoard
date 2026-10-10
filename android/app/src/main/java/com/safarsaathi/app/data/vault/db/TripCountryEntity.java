package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import com.safarsaathi.app.data.vault.CountryRole;

import java.time.LocalDate;

/**
 * A country on the trip and its role, e.g. IN = ORIGIN, RW = TRANSIT, CD = DESTINATION, with the days spent there.
 * The pre-departure checks (V4) and the country guidance (V5) start from this list.
 */
@Entity(tableName = "trip_countries",
        primaryKeys = {"tripId", "iso2", "role"},
        foreignKeys = @ForeignKey(entity = TripVaultEntity.class, parentColumns = "id", childColumns = "tripId",
                onDelete = ForeignKey.CASCADE))
public class TripCountryEntity {

    @NonNull
    public String tripId = "";

    /** 2-letter country code (ISO 3166-1 alpha-2). */
    @NonNull
    public String iso2 = "";

    @NonNull
    public CountryRole role = CountryRole.DESTINATION;

    @Nullable
    public LocalDate fromDate;

    @Nullable
    public LocalDate toDate;
}
