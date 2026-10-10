package com.safarsaathi.backend.ripple;

import java.time.Instant;
import java.util.Objects;

import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.Flex;

/**
 * One block of a trip, as the ripple engine sees it: only the facts it needs. The caller copies them from the
 * trip's {@code Block} entities (see {@code watcher.DisruptionService}).
 *
 * <p>{@link Flex} and {@link BlockStatus} are the trip's own enums, so the whole codebase has ONE set of values.
 * They are plain Java enums with no database code in them, so the engine still has no link to Spring or JPA.
 *
 * @param id                     the block's id (same as in the database)
 * @param hotel                  true for a hotel check-in (block type HOTEL): check-in is a time window, see the
 *                               hotel rule in {@link RippleEngine}
 * @param start                  when the block starts
 * @param end                    when it ends (same as start for a moment in time, e.g. a cab pickup)
 * @param flex                   what the engine may do when the block runs late
 * @param anchor                 true = the reason for the trip (e.g. the client meeting): never moved automatically
 * @param freeChangeUntilMinutes until when a change is free, in minutes from the start: -30 = until 30 minutes
 *                               before the start, 0 = until it starts; null = a change is never free
 * @param status                 the block's status right now
 */
public record RippleBlock(long id, boolean hotel, Instant start, Instant end, Flex flex, boolean anchor,
                          Integer freeChangeUntilMinutes, BlockStatus status) {

    /** Checks the values once here, so the engine can trust them. */
    public RippleBlock {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(flex, "flex");
        Objects.requireNonNull(status, "status");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Block " + id + " ends before it starts");
        }
    }
}
