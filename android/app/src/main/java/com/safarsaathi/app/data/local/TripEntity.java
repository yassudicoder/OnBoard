package com.safarsaathi.app.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.util.Objects;

/**
 * One trip, as saved on the phone (Room table "trips"). It mirrors the backend's Trip (CLAUDE.md section 3).
 *
 * <p>Room is the screen's source of truth: the Trip tab only ever reads these rows, and a refresh from the
 * server overwrites them (see TripRepository). That is what makes the plan readable with no internet.
 *
 * <p>Enum-like values (purpose, status, ...) are kept as plain Strings such as "BUSINESS". If a newer
 * backend sends a value this app version does not know yet, the app still saves and shows the trip
 * instead of crashing.
 */
@Entity(tableName = "trips")
public class TripEntity {

    /** The SERVER's id. Room never makes up its own ids for trips, blocks or links. */
    @PrimaryKey
    public long id;

    @NonNull
    public String title = "";

    /** BUSINESS, WEDDING, HOLIDAY, PILGRIMAGE, MEDICAL or EXAM. */
    @NonNull
    public String purpose = "";

    /** FIRST_TIME, FAMILY, BUSINESS or ELDERLY. */
    @NonNull
    public String persona = "";

    /** Language code, e.g. "en" or "mr". */
    @NonNull
    public String language = "";

    public int travellers;

    /** PLANNED, ACTIVE or DONE. */
    @NonNull
    public String status = "";

    /** Goes up by 1 on the server every time the plan changes. A higher number = newer plan. */
    public int version;

    /** When the server created this trip. A different value means the demo data was reloaded. */
    @NonNull
    public Instant createdAt = Instant.EPOCH;

    @NonNull
    public Instant updatedAt = Instant.EPOCH;

    /** Phone-only: when this phone last heard from the server about this trip (null = never). */
    @Nullable
    public Instant lastSyncedAt;

    // equals/hashCode compare every column, so the timeline only redraws rows that really changed
    // and tests can compare whole objects.
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TripEntity)) {
            return false;
        }
        TripEntity that = (TripEntity) other;
        return id == that.id
                && travellers == that.travellers
                && version == that.version
                && title.equals(that.title)
                && purpose.equals(that.purpose)
                && persona.equals(that.persona)
                && language.equals(that.language)
                && status.equals(that.status)
                && createdAt.equals(that.createdAt)
                && updatedAt.equals(that.updatedAt)
                && Objects.equals(lastSyncedAt, that.lastSyncedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, purpose, persona, language, travellers, status, version,
                createdAt, updatedAt, lastSyncedAt);
    }

    @NonNull
    @Override
    public String toString() {
        return "Trip " + id + " v" + version + " (" + title + ")";
    }
}
