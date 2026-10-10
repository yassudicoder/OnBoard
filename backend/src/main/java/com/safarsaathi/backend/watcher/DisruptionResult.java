package com.safarsaathi.backend.watcher;

import java.time.OffsetDateTime;
import java.util.List;

import com.safarsaathi.backend.ripple.BrokenLink;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.DisruptionKind;

/**
 * What one applied disruption did to a trip: the JSON answer of {@code POST /api/demo/trigger-delay} and
 * {@code trigger-cancel}. Example (pretty-printed; docs/api.md has the full one):
 * <pre>
 * {"tripId":1,"version":2,"changedBlockId":1,"kind":"DELAY",
 *  "outcomes":[{"blockId":3,"title":"Cab pickup to the Whitefield office","status":"AUTO_ADJUSTED",
 *               "newStart":"2026-10-11T11:35:00+05:30","newEnd":"2026-10-11T11:35:00+05:30","minutesLate":90,
 *               "reason":"Moved from 10:05 to 11:35, free of charge, to fit the new plan."}, ...],
 *  "brokenLinks":[{"fromBlockId":3,"toBlockId":4,"minutesShort":50}]}
 * </pre>
 *
 * @param tripId         the trip
 * @param version        the trip's NEW version (one higher than before); the app sees it and refreshes
 * @param changedBlockId the block that was delayed or cancelled
 * @param kind           DELAY or CANCEL
 * @param outcomes       the changed block first, then every block that waits for it, in trip order
 *                       (the ripple engine's outcomes, see {@link com.safarsaathi.backend.ripple.RippleResult})
 * @param brokenLinks    links whose timing no longer works, with how many minutes are missing
 */
public record DisruptionResult(long tripId, int version, long changedBlockId, DisruptionKind kind,
                               List<Outcome> outcomes, List<BrokenLink> brokenLinks) {

    /**
     * One block's verdict.
     *
     * @param status      the block's new status (the changed block keeps its own, or CANCELLED)
     * @param newStart    the new start in Indian time, or null when the times stay as they are
     * @param newEnd      the new end in Indian time, or null when the times stay as they are
     * @param minutesLate how many minutes after its planned start the block can begin now (0 = on time)
     * @param reason      plain English, ready to show to the traveller
     */
    public record Outcome(long blockId, String title, BlockStatus status, OffsetDateTime newStart,
                          OffsetDateTime newEnd, int minutesLate, String reason) {
    }
}
