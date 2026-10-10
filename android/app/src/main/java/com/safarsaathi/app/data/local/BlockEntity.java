package com.safarsaathi.app.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.util.Objects;

/**
 * One booking or event on the trip (a "block"): a flight, a cab, a meeting, a hotel check-in...
 * Room table "blocks"; mirrors the backend's Block (CLAUDE.md section 3).
 *
 * <p>Times are {@link Instant}s (an exact moment, no time zone). The screen shows them in Indian time
 * (AppConfig.TRIP_ZONE). Room stores them as numbers via {@link TimeConverters}.
 */
@Entity(tableName = "blocks", indices = {@Index("tripId")})
public class BlockEntity {

    // The values the backend uses. Kept as Strings on purpose (see TripEntity).
    public static final String TYPE_FLIGHT = "FLIGHT";
    public static final String TYPE_HOTEL = "HOTEL";

    public static final String STATUS_OK = "OK";
    public static final String STATUS_AUTO_ADJUSTED = "AUTO_ADJUSTED";
    public static final String STATUS_NEEDS_USER = "NEEDS_USER";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** The SERVER's id. */
    @PrimaryKey
    public long id;

    /** Which trip this block belongs to (TripEntity.id). Indexed, because every screen query filters on it. */
    public long tripId;

    /** FLIGHT, BAG_CLAIM, CAB, HOTEL, MEETING, EVENT, RESTAURANT, TRAIN or OTHER. */
    @NonNull
    public String type = "";

    @NonNull
    public String title = "";

    @NonNull
    public Instant startTime = Instant.EPOCH;

    @NonNull
    public Instant endTime = Instant.EPOCH;

    @Nullable
    public String locationName;

    @Nullable
    public Double lat;

    @Nullable
    public Double lng;

    /** Who holds the booking: MOCK_AIR, MOCK_STAY, MOCK_CAB, MOCK_TABLE, GOOGLE_CALENDAR or NONE. */
    @NonNull
    public String provider = "";

    /** The booking reference at that provider, e.g. a PNR. Null when there is none. */
    @Nullable
    public String providerRef;

    /** FIXED (never moved for you), AUTO_SHIFT (may be moved for you) or ASK_USER (moved only after you agree). */
    @NonNull
    public String flex = "";

    /** True for the block the trip is for (e.g. the client meeting): it must not be missed. */
    public boolean anchor;

    /** OK, AUTO_ADJUSTED, NEEDS_USER, DONE or CANCELLED. */
    @NonNull
    public String status = "";

    /**
     * Free changes are allowed until this many minutes after the start (negative = before the start,
     * e.g. -30 = until 30 minutes before). Null = never free, a fee always applies.
     */
    @Nullable
    public Integer freeChangeUntilMinutes;

    /** Fee in rupees for a change outside the free window. */
    public int changeFeeInr;

    @Nullable
    public String notes;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockEntity)) {
            return false;
        }
        BlockEntity that = (BlockEntity) other;
        return id == that.id
                && tripId == that.tripId
                && anchor == that.anchor
                && changeFeeInr == that.changeFeeInr
                && type.equals(that.type)
                && title.equals(that.title)
                && startTime.equals(that.startTime)
                && endTime.equals(that.endTime)
                && Objects.equals(locationName, that.locationName)
                && Objects.equals(lat, that.lat)
                && Objects.equals(lng, that.lng)
                && provider.equals(that.provider)
                && Objects.equals(providerRef, that.providerRef)
                && flex.equals(that.flex)
                && status.equals(that.status)
                && Objects.equals(freeChangeUntilMinutes, that.freeChangeUntilMinutes)
                && Objects.equals(notes, that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tripId, type, title, startTime, endTime, locationName, lat, lng, provider,
                providerRef, flex, anchor, status, freeChangeUntilMinutes, changeFeeInr, notes);
    }

    @NonNull
    @Override
    public String toString() {
        return "Block " + id + " " + type + " " + status + " (" + title + ")";
    }
}
