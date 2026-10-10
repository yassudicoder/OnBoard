package com.safarsaathi.backend.ripple;

import java.time.Instant;

import com.safarsaathi.backend.trip.BlockStatus;

/**
 * The ripple engine's verdict for one block.
 *
 * @param blockId     which block
 * @param status      OK, AUTO_ADJUSTED or NEEDS_USER; for the changed block itself: CANCELLED when it was
 *                    cancelled, otherwise the status it already had (the caller may pick another one)
 * @param newStart    the new start to save, or null when the times stay as they are
 * @param newEnd      the new end to save, or null when the times stay as they are
 * @param minutesLate how many minutes after its planned start you can begin this block (0 = on time).
 *                    For the changed block: how many minutes later it now starts
 * @param reason      one or two plain-English sentences for the traveller
 */
public record BlockOutcome(long blockId, BlockStatus status, Instant newStart, Instant newEnd, int minutesLate,
                           String reason) {

    /** true when the engine gives new times (newStart and newEnd are then both set). */
    public boolean moved() {
        return newStart != null;
    }
}
