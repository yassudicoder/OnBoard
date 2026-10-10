package com.safarsaathi.backend.trip;

/** What kind of disruption hit a block (CLAUDE.md section 5). Saved by name, e.g. "DELAY". */
public enum DisruptionKind {
    /** The block now runs later, e.g. a flight delay. */
    DELAY,
    /** The block will not happen, e.g. a cancelled flight. */
    CANCEL,
    /** Same time, different gate. Changes no times, so no ripple (not produced yet). */
    GATE_CHANGE,
    /**
     * A PREDICTION: by the latest times the traveller can no longer reach this connecting flight (or train).
     * Until V3 this was called MISSED; the migration renamed the saved rows.
     */
    LIKELY_MISSED,
    /** A FACT: the connecting flight left without the traveller, or the airline says so (from phase C2). */
    CONFIRMED_MISSED
}
