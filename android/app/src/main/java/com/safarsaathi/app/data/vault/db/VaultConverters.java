package com.safarsaathi.app.data.vault.db;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import java.time.LocalDate;

/**
 * Tells Room how to store a {@link LocalDate} (a day without a time, like a passport's expiry date): as ISO text,
 * "2029-03-14". ISO text sorts in date order, so "ORDER BY startDate" just works.
 * Instants use the app's existing TimeConverters (milliseconds since 1970).
 */
public final class VaultConverters {

    private VaultConverters() {
    }

    @TypeConverter
    @Nullable
    public static String fromLocalDate(@Nullable LocalDate date) {
        return date == null ? null : date.toString();
    }

    @TypeConverter
    @Nullable
    public static LocalDate toLocalDate(@Nullable String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }
}
