package com.safarsaathi.recovery.plan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.FlightLeg;
import com.safarsaathi.recovery.rules.AirportRules;

/**
 * Where and from when a recovery plan starts: which trigger, which airport, the earliest moment the traveller can
 * leave on another flight, and where they are going.
 *
 * <ul>
 *   <li>Flight 1 cancelled ({@link RecoveryTrigger#CANCELLED_BEFORE_DEPARTURE}): at the departure airport, from the
 *       original departure time, or from now + check-in/security time (an estimate) if that is later.</li>
 *   <li>Otherwise ({@link RecoveryTrigger#DELAY_MISSED_CONNECTION}, {@link RecoveryTrigger#CONNECTION_CANCELLED_AT_HUB}):
 *       at the hub, from flight 1's landing (or now, if later) + the time a connection there needs: the minimum
 *       connection time or our practical estimate, whichever is longer.</li>
 * </ul>
 */
public final class RecoveryStart {

    public RecoveryTrigger trigger;
    /** Where the plan starts, e.g. "DOH". */
    public String airport;
    public ZoneId zone;
    /** Alternatives must leave at or after this moment. */
    public Instant earliestDeparture;
    /** The final airport of the connection, e.g. "LHR". */
    public String destination;
    public ZoneId destinationZone;
    /** When the traveller was due there (the timetable arrival of flight 2). */
    public Instant originalArrival;
    /** The flight that caused the trouble (delayed or cancelled): its airline's assistance policy applies. */
    public FlightLeg disruptedFlight;
    /** The flight that has to be replaced (flight 2, or flight 1 when it is cancelled before departure). */
    public FlightLeg replacedFlight;
    /** true = the plan starts at the hub (transfer desk advice); false = at the departure airport. */
    public boolean atHub;

    /** Statuses that get a recovery plan. OK, RESOLVED and UNKNOWN don't (UNKNOWN can't tell anything is wrong). */
    public static boolean needsPlan(ConnectionStatus status) {
        return status == ConnectionStatus.AT_RISK || status == ConnectionStatus.LIKELY_MISSED
                || status == ConnectionStatus.CONFIRMED_MISSED || status == ConnectionStatus.CANCELLED
                || status == ConnectionStatus.REBOOKED_BY_AIRLINE;
    }

    /**
     * @param mctMinutes       the hub's minimum connection time, or null when unknown
     * @param practicalMinutes taxiing + getting off + transfer security + gate closing (estimate)
     * @param now              the current time
     */
    public static RecoveryStart of(FlightLeg inbound, FlightLeg outbound, Integer mctMinutes, int practicalMinutes,
                                   Instant now, AirportRules rules) {
        RecoveryStart start = new RecoveryStart();
        start.destination = outbound.arrAirport;
        start.destinationZone = outbound.arrZone;
        start.originalArrival = outbound.scheduledArr;
        if (inbound.cancelled) {
            start.trigger = RecoveryTrigger.CANCELLED_BEFORE_DEPARTURE;
            start.airport = inbound.depAirport;
            start.zone = inbound.depZone;
            Instant afterCheckIn = now.plus(Duration.ofMinutes(rules.defaults.departureProcess.minutes));
            start.earliestDeparture = later(afterCheckIn, inbound.scheduledDep);
            start.disruptedFlight = inbound;
            start.replacedFlight = inbound;
            start.atHub = false;
        } else {
            start.trigger = outbound.cancelled ? RecoveryTrigger.CONNECTION_CANCELLED_AT_HUB
                    : RecoveryTrigger.DELAY_MISSED_CONNECTION;
            start.airport = inbound.arrAirport;
            start.zone = inbound.arrZone;
            int needed = Math.max(mctMinutes == null ? 0 : mctMinutes, practicalMinutes);
            start.earliestDeparture = later(inbound.arrival(), now).plus(Duration.ofMinutes(needed));
            start.disruptedFlight = outbound.cancelled ? outbound : inbound;
            start.replacedFlight = outbound;
            start.atHub = true;
        }
        return start;
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
