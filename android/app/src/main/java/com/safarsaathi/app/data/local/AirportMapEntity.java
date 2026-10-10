package com.safarsaathi.app.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;

import java.time.Instant;

/**
 * The saved copy of one terminal's walking map (Room table "airport_maps"), so the Navigate tab works offline.
 * It holds the server's JSON as it is (map + "areas"); the screen reads it with MapJson. One row per terminal.
 *
 * <p>Where the copy came from: {@link #ORIGIN_BUNDLED} = the copy that ships inside the app (so the map works even
 * before the app ever reached the server), {@link #ORIGIN_SERVER} = downloaded from GET /api/airports/.../map.
 */
@Entity(tableName = "airport_maps", primaryKeys = {"airport", "terminal"})
public class AirportMapEntity {

    public static final String ORIGIN_BUNDLED = "BUNDLED";
    public static final String ORIGIN_SERVER = "SERVER";

    /** e.g. "BOM" */
    @NonNull
    public String airport = "";

    /** e.g. "T2" */
    @NonNull
    public String terminal = "";

    /** The map's own version number; a higher one replaces the saved copy. */
    public int version;

    /** The whole map as JSON: the same shape as the server's answer. */
    @NonNull
    public String json = "";

    /** BUNDLED or SERVER. */
    @NonNull
    public String origin = ORIGIN_BUNDLED;

    /** When this copy was saved on the phone. */
    @NonNull
    public Instant savedAt = Instant.EPOCH;

    /** When the server last confirmed this is still the newest map; null = never. */
    @Nullable
    public Instant checkedAt;
}
