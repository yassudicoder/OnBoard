package com.safarsaathi.backend.watcher;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.safarsaathi.backend.ripple.RippleChange;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.DisruptionKind;

/**
 * What a {@link FlightStatusProvider} found out about one block: it now runs at new times (e.g. a delay), or it is
 * cancelled. The new times are worked out from the block's CURRENT times, so a block that was already delayed by 90
 * minutes and is then delayed by another 30 ends up 120 minutes late.
 *
 * <p>Make one with {@link #delayedBy}, {@link #movedTo} or {@link #cancelled}.
 *
 * @param newStart  the block's new start; null when cancelled
 * @param newEnd    the block's new end; null when cancelled
 * @param cancelled true when the block will not happen at all
 * @param detail    one plain-English line for the disruption log, e.g. "Delayed by 90 minutes (mock flight status)"
 */
public record FlightStatusChange(Instant newStart, Instant newEnd, boolean cancelled, String detail) {

    public FlightStatusChange {
        if (cancelled) {
            if (newStart != null || newEnd != null) {
                throw new IllegalArgumentException("A cancelled block has no new times");
            }
        } else {
            Objects.requireNonNull(newStart, "newStart");
            Objects.requireNonNull(newEnd, "newEnd");
            if (newEnd.isBefore(newStart)) {
                throw new IllegalArgumentException("The new end is before the new start");
            }
        }
    }

    /** The block runs {@code minutes} later than its current times: start and end both move. */
    public static FlightStatusChange delayedBy(Block block, int minutes, String detail) {
        Duration delay = Duration.ofMinutes(minutes);
        return new FlightStatusChange(block.getStartTime().plus(delay), block.getEndTime().plus(delay), false, detail);
    }

    /** The block now runs from {@code newStart} to {@code newEnd}, e.g. the revised times from a real flight API. */
    public static FlightStatusChange movedTo(Instant newStart, Instant newEnd, String detail) {
        return new FlightStatusChange(newStart, newEnd, false, detail);
    }

    /** The block will not happen, e.g. a cancelled flight. */
    public static FlightStatusChange cancelled(String detail) {
        return new FlightStatusChange(null, null, true, detail);
    }

    /** CANCEL for a cancellation, DELAY for new times. */
    public DisruptionKind kind() {
        return cancelled ? DisruptionKind.CANCEL : DisruptionKind.DELAY;
    }

    /** The same change in the ripple engine's own words. */
    RippleChange toRippleChange(long blockId) {
        return cancelled ? RippleChange.cancel(blockId) : RippleChange.move(blockId, newStart, newEnd);
    }
}
