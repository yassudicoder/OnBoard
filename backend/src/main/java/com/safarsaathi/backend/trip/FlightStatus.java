package com.safarsaathi.backend.trip;

/**
 * A flight's status as the API shows it ({@link FlightResponse#status()}). Not stored: {@link TripMapper} works it out
 * from the block, in this order: CANCELLED (block cancelled), then the flight phase when something was reported
 * (BOARDING, IN_AIR, LANDED), then DELAYED (later than the timetable), else SCHEDULED.
 */
public enum FlightStatus {
    SCHEDULED,
    DELAYED,
    BOARDING,
    IN_AIR,
    LANDED,
    CANCELLED
}
