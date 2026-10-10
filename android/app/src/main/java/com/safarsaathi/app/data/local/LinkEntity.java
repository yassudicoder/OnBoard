package com.safarsaathi.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * A "domino" link between two blocks (Room table "links"): block {@code toBlockId} can only start
 * {@code minGapMinutes + travelMinutes} after block {@code fromBlockId} ends. Mirrors the backend's Link.
 */
@Entity(tableName = "links", indices = {@Index("tripId")})
public class LinkEntity {

    public static final String MODE_WALK = "WALK";
    public static final String MODE_CAB = "CAB";
    public static final String MODE_METRO = "METRO";
    public static final String MODE_NONE = "NONE";

    /** The SERVER's id. */
    @PrimaryKey
    public long id;

    public long tripId;

    public long fromBlockId;

    public long toBlockId;

    /** Minimum time between the two blocks, e.g. 75 minutes to change planes at Delhi. */
    public int minGapMinutes;

    /** Time needed to get from one place to the other, e.g. 75 minutes by cab. */
    public int travelMinutes;

    /** WALK, CAB, METRO or NONE. */
    @NonNull
    public String travelMode = "";

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LinkEntity)) {
            return false;
        }
        LinkEntity that = (LinkEntity) other;
        return id == that.id
                && tripId == that.tripId
                && fromBlockId == that.fromBlockId
                && toBlockId == that.toBlockId
                && minGapMinutes == that.minGapMinutes
                && travelMinutes == that.travelMinutes
                && travelMode.equals(that.travelMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tripId, fromBlockId, toBlockId, minGapMinutes, travelMinutes, travelMode);
    }

    @NonNull
    @Override
    public String toString() {
        return "Link " + id + ": " + fromBlockId + " -> " + toBlockId;
    }
}
