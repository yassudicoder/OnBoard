package com.safarsaathi.app.ui.trip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One row of the trip timeline. There are three kinds:
 * <ul>
 *   <li>DAY_HEADER: "Sun, 11 Oct"</li>
 *   <li>BLOCK: one booking card (time, icon, title, place, status chip, star on the anchor)</li>
 *   <li>LINK: the small line between two linked cards, e.g. "75 min by cab"</li>
 * </ul>
 * Built by {@link TripRowBuilder}; drawn by TripTimelineAdapter. Plain Java (no Android classes), so the
 * row logic is tested on the laptop. Fields that do not belong to a row's kind are null.
 */
public final class TripRow {

    public enum Kind { DAY_HEADER, BLOCK, LINK }

    public final Kind kind;

    /** DAY_HEADER: the day (in India). */
    @Nullable
    public final LocalDate day;

    /** BLOCK: the booking. */
    @Nullable
    public final BlockEntity block;

    /** BLOCK: its times, e.g. "08:00–09:45" (see TripTime.timeRange). */
    @Nullable
    public final String timeText;

    /** LINK: the link between this block and the next one. */
    @Nullable
    public final LinkEntity link;

    /** LINK: true when it connects two flights, so the gap reads "to change planes". */
    public final boolean planeChange;

    private TripRow(Kind kind, @Nullable LocalDate day, @Nullable BlockEntity block, @Nullable String timeText,
                    @Nullable LinkEntity link, boolean planeChange) {
        this.kind = kind;
        this.day = day;
        this.block = block;
        this.timeText = timeText;
        this.link = link;
        this.planeChange = planeChange;
    }

    public static TripRow dayHeader(LocalDate day) {
        return new TripRow(Kind.DAY_HEADER, day, null, null, null, false);
    }

    public static TripRow block(BlockEntity block, String timeText) {
        return new TripRow(Kind.BLOCK, null, block, timeText, null, false);
    }

    public static TripRow link(LinkEntity link, boolean planeChange) {
        return new TripRow(Kind.LINK, null, null, null, link, planeChange);
    }

    /**
     * Identifies "the same row" across updates (e.g. block 3 before and after a delay), so the list can
     * update just the rows that changed.
     */
    public String key() {
        if (kind == Kind.DAY_HEADER) {
            return "day:" + day;
        }
        if (kind == Kind.BLOCK) {
            return "block:" + Objects.requireNonNull(block).id;
        }
        return "link:" + Objects.requireNonNull(link).id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TripRow)) {
            return false;
        }
        TripRow that = (TripRow) other;
        return kind == that.kind
                && planeChange == that.planeChange
                && Objects.equals(day, that.day)
                && Objects.equals(block, that.block)
                && Objects.equals(timeText, that.timeText)
                && Objects.equals(link, that.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, day, block, timeText, link, planeChange);
    }

    @NonNull
    @Override
    public String toString() {
        return key();
    }
}
