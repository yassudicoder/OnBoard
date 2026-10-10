package com.safarsaathi.app.data.vault;

import androidx.annotation.Nullable;

import java.time.LocalDate;

/** A vault trip folder as other parts of the app see it (VaultSummaryReader). The title is null while locked. */
public final class TripRef {

    public final String id;
    @Nullable
    public final String title;
    @Nullable
    public final LocalDate startDate;
    @Nullable
    public final LocalDate endDate;
    public final boolean sample;

    TripRef(String id, @Nullable String title, @Nullable LocalDate startDate, @Nullable LocalDate endDate,
            boolean sample) {
        this.id = id;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sample = sample;
    }
}
