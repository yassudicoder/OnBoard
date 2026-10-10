package com.safarsaathi.backend.trip;

import java.time.OffsetDateTime;

/**
 * The flight fields of a FLIGHT block ({@link BlockResponse#flight()}; null for every other block).
 *
 * <p>Every time is in the local time of its airport: departures in the departure airport's zone ({@code depZone}),
 * arrivals in the arrival airport's zone ({@code arrZone}), e.g. {@code "2026-10-11T19:35:00+03:00"} for landing in
 * Doha. The offset says how far that is from UTC; the zone id lets the app work with local dates and clocks.
 *
 * @param scheduledDep the timetable departure; never changes
 * @param estimatedDep the latest known departure: the same as {@code scheduledDep} until a change is reported
 *                     (e.g. a delay from the flight-status provider)
 * @param status       see {@link FlightStatus}
 */
public record FlightResponse(
        String flightNo,
        String depAirport,
        String arrAirport,
        String depTerminal,
        String arrTerminal,
        String depZone,
        String arrZone,
        OffsetDateTime scheduledDep,
        OffsetDateTime scheduledArr,
        OffsetDateTime estimatedDep,
        OffsetDateTime estimatedArr,
        FlightStatus status) {
}
