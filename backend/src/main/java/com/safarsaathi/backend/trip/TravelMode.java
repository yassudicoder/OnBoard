package com.safarsaathi.backend.trip;

/** How the traveller gets from one block to the next one in a link. Saved by name, e.g. "CAB". */
public enum TravelMode {
    WALK,
    CAB,
    METRO,
    /** No travel needed, e.g. from landing straight to the baggage belt. */
    NONE
}
