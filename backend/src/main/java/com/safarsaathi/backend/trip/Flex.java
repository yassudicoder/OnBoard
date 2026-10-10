package com.safarsaathi.backend.trip;

/** What the ripple engine (Phase 2) may do when an earlier block runs late. Saved by name, e.g. "AUTO_SHIFT". */
public enum Flex {
    /** Cannot move by itself, e.g. a flight. A clash needs the traveller. */
    FIXED,
    /** May be moved automatically while changes are still free, e.g. a cab pickup. */
    AUTO_SHIFT,
    /** Could move, but only after the traveller says yes, e.g. a client meeting. */
    ASK_USER
}
