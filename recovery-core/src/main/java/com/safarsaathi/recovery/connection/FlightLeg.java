package com.safarsaathi.recovery.connection;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * One flight as the connection checker sees it. The backend fills it from a flight block, the app (from C3) from its
 * offline copy.
 *
 * <p>Times are exact moments ({@link Instant}) plus the time zone of the airport they belong to: departure times in
 * {@link #depZone}, arrival times in {@link #arrZone}. Together they make the local time the traveller sees on the
 * airport screens ({@link #localDeparture()}).
 */
public final class FlightLeg {

    /** e.g. "IJ 1907". */
    public String flightNo;
    /** Three-letter airport codes, e.g. "DEL" and "DOH". */
    public String depAirport;
    public String arrAirport;
    /** e.g. "T3"; null = not known. */
    public String depTerminal;
    public String arrTerminal;
    public ZoneId depZone;
    public ZoneId arrZone;
    /** The timetable times. Never change. */
    public Instant scheduledDep;
    public Instant scheduledArr;
    /** The latest estimate (e.g. after a delay); null = no estimate, the timetable time applies. */
    public Instant estimatedDep;
    public Instant estimatedArr;
    public FlightPhase phase = FlightPhase.SCHEDULED;
    public boolean cancelled;

    /** The departure time to work with: the estimate when there is one, otherwise the timetable. */
    public Instant departure() {
        return estimatedDep != null ? estimatedDep : scheduledDep;
    }

    /** The arrival time to work with: the estimate when there is one, otherwise the timetable. */
    public Instant arrival() {
        return estimatedArr != null ? estimatedArr : scheduledArr;
    }

    /** The departure in the departure airport's local time. */
    public ZonedDateTime localDeparture() {
        return departure().atZone(depZone);
    }

    /** The arrival in the arrival airport's local time. */
    public ZonedDateTime localArrival() {
        return arrival().atZone(arrZone);
    }
}
