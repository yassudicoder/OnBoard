package com.safarsaathi.backend.ripple;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * What happened to one block: it has new times (e.g. a flight delay), or it is cancelled.
 * Make one with {@link #move}, {@link #delay} or {@link #cancel}.
 *
 * @param blockId   the block that changed
 * @param newStart  its new start; null when cancelled
 * @param newEnd    its new end; null when cancelled
 * @param cancelled true when the block will not happen at all
 */
public record RippleChange(long blockId, Instant newStart, Instant newEnd, boolean cancelled) {

    public RippleChange {
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

    /** The block now runs from {@code newStart} to {@code newEnd}. */
    public static RippleChange move(long blockId, Instant newStart, Instant newEnd) {
        return new RippleChange(blockId, newStart, newEnd, false);
    }

    /** The block runs {@code minutes} later than its current times, e.g. a flight delayed by 90 minutes. */
    public static RippleChange delay(RippleBlock block, long minutes) {
        Duration delay = Duration.ofMinutes(minutes);
        return move(block.id(), block.start().plus(delay), block.end().plus(delay));
    }

    /** The block will not happen, e.g. a cancelled flight. */
    public static RippleChange cancel(long blockId) {
        return new RippleChange(blockId, null, null, true);
    }
}
