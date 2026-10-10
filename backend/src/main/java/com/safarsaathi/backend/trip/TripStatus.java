package com.safarsaathi.backend.trip;

/** Where the whole trip stands. Saved by name, e.g. "PLANNED". */
public enum TripStatus {
    /** Not started yet. Every demo trip starts here. */
    PLANNED,
    /** Under way (from Phase 5, Plan B is precomputed when a trip becomes ACTIVE). */
    ACTIVE,
    DONE
}
