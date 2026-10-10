package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.safarsaathi.app.data.vault.LegKind;

import java.time.LocalDate;

/** One part of a trip, in order: "Delhi → Kigali" (FLIGHT), "Kigali transit" (TRANSIT), "Kinshasa" (STAY). */
@Entity(tableName = "legs",
        foreignKeys = @ForeignKey(entity = TripVaultEntity.class, parentColumns = "id", childColumns = "tripId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("tripId")})
public class TripLegEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    public String tripId = "";

    /** 0, 1, 2, ...: the order of the legs in the trip. */
    public int position;

    @NonNull
    public LegKind kind = LegKind.FLIGHT;

    @NonNull
    public String title = "";

    /** Airport codes (IATA), e.g. DEL and KGL for a flight; for a transit or a stay only {@link #fromCode}. */
    @Nullable
    public String fromCode;

    @Nullable
    public String toCode;

    /** Where a TRANSIT or STAY happens, as a 2-letter country code, e.g. "RW". */
    @Nullable
    public String countryIso2;

    @Nullable
    public LocalDate startDate;

    @Nullable
    public LocalDate endDate;
}
