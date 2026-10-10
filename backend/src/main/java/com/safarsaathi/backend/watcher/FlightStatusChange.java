package com.safarsaathi.backend.watcher;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.safarsaathi.backend.ripple.RippleChange;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.DisruptionKind;

/**
 * What a {@link FlightStatusProvider} found out about one block. Three kinds (make them with the static methods):
 * <ul>
 *   <li>{@link #delayedBy}: "N minutes later than now planned" (the mock, from the demo buttons). The new times are
 *       worked out only when the change is saved, from the block's times read INSIDE the database transaction,
 *       after the trip row is locked ({@link #toRippleChange}). So two delays saved at the same moment add up
 *       (+90 and +30 = +120) instead of the second one overwriting the first.</li>
 *   <li>{@link #movedTo}: exact new times, e.g. a real flight API's revised departure. Saving it twice gives the same
 *       result, so asking again is harmless.</li>
 *   <li>{@link #cancelled}: the block will not happen.</li>
 * </ul>
 *
 * @param delayMinutes for a delay: how many minutes later (more than 0); otherwise null
 * @param newStart     for exact new times: the block's new start; otherwise null
 * @param newEnd       for exact new times: the block's new end; otherwise null
 * @param cancelled    true when the block will not happen at all
 * @param detail       one plain-English line for the disruption log, e.g. "Delayed by 90 minutes (mock flight status)"
 */
public record FlightStatusChange(Integer delayMinutes, Instant newStart, Instant newEnd, boolean cancelled,
                                 String detail) {

    public FlightStatusChange {
        if (cancelled) {
            if (delayMinutes != null || newStart != null || newEnd != null) {
                throw new IllegalArgumentException("A cancelled block has no delay and no new times");
            }
        } else if (delayMinutes != null) {
            if (delayMinutes <= 0) {
                throw new IllegalArgumentException("A delay must be more than 0 minutes");
            }
            if (newStart != null || newEnd != null) {
                throw new IllegalArgumentException("A delay is relative: it has no exact new times");
            }
        } else {
            Objects.requireNonNull(newStart, "newStart");
            Objects.requireNonNull(newEnd, "newEnd");
            if (newEnd.isBefore(newStart)) {
                throw new IllegalArgumentException("The new end is before the new start");
            }
        }
    }

    /** The block runs {@code minutes} later than its times at the moment the change is saved. */
    public static FlightStatusChange delayedBy(int minutes, String detail) {
        return new FlightStatusChange(minutes, null, null, false, detail);
    }

    /** The block now runs from {@code newStart} to {@code newEnd}, e.g. the revised times from a real flight API. */
    public static FlightStatusChange movedTo(Instant newStart, Instant newEnd, String detail) {
        return new FlightStatusChange(null, newStart, newEnd, false, detail);
    }

    /** The block will not happen, e.g. a cancelled flight. */
    public static FlightStatusChange cancelled(String detail) {
        return new FlightStatusChange(null, null, null, true, detail);
    }

    /** CANCEL for a cancellation, DELAY for new times. */
    public DisruptionKind kind() {
        return cancelled ? DisruptionKind.CANCEL : DisruptionKind.DELAY;
    }

    /**
     * The same change in the ripple engine's own words. {@code current} must be the block as it is in the database
     * right now (DisruptionService reads it after locking the trip): a delay is added to ITS times.
     */
    RippleChange toRippleChange(Block current) {
        if (cancelled) {
            return RippleChange.cancel(current.getId());
        }
        if (delayMinutes != null) {
            Duration delay = Duration.ofMinutes(delayMinutes);
            return RippleChange.move(current.getId(), current.getStartTime().plus(delay),
                    current.getEndTime().plus(delay));
        }
        return RippleChange.move(current.getId(), newStart, newEnd);
    }
}
