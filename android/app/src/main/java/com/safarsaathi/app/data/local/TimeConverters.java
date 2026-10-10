package com.safarsaathi.app.data.local;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import java.time.Instant;

/**
 * Tells Room how to store a {@link Instant}: SQLite has no time type, so we save the number of
 * milliseconds since 1970-01-01 UTC. Numbers also sort correctly, so "ORDER BY startTime" just works.
 * Registered for the whole database in {@link AppDatabase} (@TypeConverters).
 */
public final class TimeConverters {

    private TimeConverters() {
    }

    @TypeConverter
    @Nullable
    public static Long fromInstant(@Nullable Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    @TypeConverter
    @Nullable
    public static Instant toInstant(@Nullable Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }
}
