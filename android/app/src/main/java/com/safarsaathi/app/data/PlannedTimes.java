package com.safarsaathi.app.data;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * THE "ORIGINAL PLAN" RULE: which times count as "planned" for each block, so the screens can say
 * "Planned for 08:00 · 2 hours later". Plain Java, checked by PlannedTimesTest.
 *
 * <p>The server does not send the original times; it only sends the plan as it is now, plus a version number
 * that starts at 1 when a trip is created and goes up by 1 on every change. So the phone remembers the times it
 * saw at version 1 (the demo page does the same for its "was 08:00"):
 * <ul>
 *   <li>a trip the phone sees for the first time (or again after a demo reset, which gives it a new createdAt):
 *       at version 1 its current times ARE the original plan; at a higher version the original plan is not
 *       known, so nothing is claimed (null);</li>
 *   <li>the same trip again: each block keeps the planned times already saved. A copy saved before this rule
 *       existed has none; if that copy was still version 1, its times are the original plan;</li>
 *   <li>a block that first appears after the trip changed: not known (null).</li>
 * </ul>
 * Null never turns into a guess: the screens then show the current times only.
 */
public final class PlannedTimes {

    /** The server's first version of a trip: its times are the original plan. */
    static final int ORIGINAL_VERSION = 1;

    private PlannedTimes() {
    }

    /**
     * Sets plannedStartTime / plannedEndTime on every block of the fresh copy, just before it replaces the saved one.
     *
     * @param saved       the copy on the phone, or null when there is none
     * @param savedBlocks the saved copy's blocks (empty when there is none)
     * @param fresh       the copy that just arrived from the server
     * @param freshBlocks its blocks; changed in place
     */
    public static void fill(@Nullable TripEntity saved, List<BlockEntity> savedBlocks,
                            TripEntity fresh, List<BlockEntity> freshBlocks) {
        boolean sameTrip = saved != null && saved.createdAt.equals(fresh.createdAt);
        Map<Long, BlockEntity> savedById = new HashMap<>();
        if (sameTrip) {
            for (BlockEntity block : savedBlocks) {
                savedById.put(block.id, block);
            }
        }
        for (BlockEntity block : freshBlocks) {
            BlockEntity before = savedById.get(block.id);
            if (before != null && before.plannedStartTime != null) {
                // Already known: keep it, however often the plan changed since.
                setPlanned(block, before.plannedStartTime, before.plannedEndTime);
            } else if (before != null && saved.version == ORIGINAL_VERSION) {
                // Saved before this rule existed, while the trip was still at version 1: those are the original times.
                setPlanned(block, before.startTime, before.endTime);
            } else if (before == null && fresh.version == ORIGINAL_VERSION) {
                // A new trip (or a block) seen at version 1: what the server sends now is the original plan.
                setPlanned(block, block.startTime, block.endTime);
            } else {
                setPlanned(block, null, null); // the original plan is not known
            }
        }
    }

    private static void setPlanned(BlockEntity block, @Nullable Instant start, @Nullable Instant end) {
        block.plannedStartTime = start;
        block.plannedEndTime = end;
    }
}
