package com.safarsaathi.backend.trip;

/** What kind of disruption hit a block (CLAUDE.md section 3). Saved by name, e.g. "DELAY". */
public enum DisruptionKind {
    /** The block now runs later, e.g. a flight delay. */
    DELAY,
    /** The block will not happen, e.g. a cancelled flight. */
    CANCEL,
    /** Same time, different gate. Changes no times, so no ripple (not produced yet). */
    GATE_CHANGE,
    /** The traveller missed it, e.g. a missed connection (not produced yet). */
    MISSED
}
