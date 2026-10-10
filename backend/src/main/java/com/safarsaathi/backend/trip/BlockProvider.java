package com.safarsaathi.backend.trip;

/**
 * Who holds the booking behind a block, so the booking orchestrator (Phase 4) knows which adapter can change it.
 * The MOCK_* partners are fake booking servers inside our own backend. Saved by name, e.g. "MOCK_AIR".
 */
public enum BlockProvider {
    /** Flights. */
    MOCK_AIR,
    /** Hotels. */
    MOCK_STAY,
    /** Cabs. */
    MOCK_CAB,
    /** Restaurant tables. */
    MOCK_TABLE,
    /** Meetings in Google Calendar (a mock calendar when no Google credentials are set). */
    GOOGLE_CALENDAR,
    /** Nothing booked, e.g. waiting for bags. */
    NONE
}
