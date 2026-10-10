package com.safarsaathi.backend.trip;

/**
 * Why the traveller makes the trip (feature F1). It decides which block is the anchor (the one that must not be
 * missed) and, from Phase 5, how much lost time weighs when Plan B options are scored.
 *
 * <p>Like every enum in this package, it is saved in the database and sent in the JSON as its name, e.g.
 * "BUSINESS", and the Android app uses the same names. So never rename a value: add a new one instead.
 */
public enum Purpose {
    BUSINESS,
    /** A wedding or another family event. */
    WEDDING,
    HOLIDAY,
    PILGRIMAGE,
    MEDICAL,
    /** An exam or a job interview. */
    EXAM
}
