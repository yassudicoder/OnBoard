package com.safarsaathi.backend.trip;

/**
 * How a block is doing. The timeline colours it: OK green, AUTO_ADJUSTED amber, NEEDS_USER red, DONE grey.
 * Every seeded block starts as OK; the ripple engine (Phase 2) changes it. Saved by name, e.g. "OK".
 */
public enum BlockStatus {
    OK,
    /** Moved automatically because an earlier block ran late. */
    AUTO_ADJUSTED,
    /** Broken, and only the traveller can decide what to do. */
    NEEDS_USER,
    DONE,
    CANCELLED
}
