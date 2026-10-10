package com.safarsaathi.backend.planb;

import java.time.OffsetDateTime;
import java.util.List;

import com.safarsaathi.recovery.alternatives.OptionSource;
import com.safarsaathi.recovery.alternatives.SeatAvailability;
import com.safarsaathi.recovery.alternatives.TransitVisaStatus;
import com.safarsaathi.recovery.connection.Certainty;
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * One alternative as the API shows it (inside a plan and in the alternatives list). Also stored as JSON in
 * {@code alternative_options.option_json} (with {@code id} null), so a plan reads back exactly what was decided.
 * Every time is local: a departure in its departure airport's time, an arrival in its arrival airport's time.
 *
 * @param rank                  1 = best score; 0 for a rejected route
 * @param connectionTimeChecked every change of planes has a minimum connection time (official or demo value)
 * @param connectionLabel       "connection time not checked", or null
 * @param seatNote              "Availability unknown" unless a provider verified seats
 * @param fareDifferenceInr     only for separate tickets: the new ticket's fare (SAMPLE fare for SAMPLE options)
 * @param sourceLabel           "SAMPLE DATA" for options from the sample schedule, else null
 * @param airlineRebooking      true = this is the flight the airline rebooked the traveller on
 * @param score                 the ranking score in minutes (lower = better), for transparency
 * @param reason                "why this one" for the recommended option; a summary for the others; for a rejected
 *                              route, why it is not offered
 */
public record OptionResponse(
        Long id,
        int rank,
        boolean recommended,
        boolean rejected,
        String routeKey,
        String flights,
        List<Leg> legs,
        OffsetDateTime departs,
        OffsetDateTime arrives,
        int stops,
        int arrivalDelayMinutes,
        List<Check> connectionChecks,
        boolean connectionTimeChecked,
        String connectionLabel,
        boolean basedOnDemoValue,
        TransitVisaStatus transitVisaStatus,
        String transitNote,
        SeatAvailability seatAvailability,
        String seatNote,
        boolean separateTickets,
        String separateTicketWarning,
        Integer fareDifferenceInr,
        String fareNote,
        String changeRules,
        OptionSource source,
        String sourceLabel,
        boolean airlineRebooking,
        int score,
        String reason) {

    /** One flight of the option. {@code airline} = the airline's name ("Dunes Air"), or its code when unknown. */
    public record Leg(String flightNo, String airline, String from, String to, String depTerminal,
                      String arrTerminal, OffsetDateTime departs, OffsetDateTime arrives) {
    }

    /** One change of planes, checked against the airport's minimum connection time. */
    public record Check(String airport, ConnectionStatus status, Certainty certainty, int availableMinutes,
                        Integer neededMinutes, ValueKind mctKind, String mctSource, boolean basedOnDemoValue,
                        String reason) {
    }

    /** The same option with its database id (the stored JSON has none). */
    public OptionResponse withId(Long newId) {
        return new OptionResponse(newId, rank, recommended, rejected, routeKey, flights, legs, departs, arrives, stops,
                arrivalDelayMinutes, connectionChecks, connectionTimeChecked, connectionLabel, basedOnDemoValue,
                transitVisaStatus, transitNote, seatAvailability, seatNote, separateTickets, separateTicketWarning,
                fareDifferenceInr, fareNote, changeRules, source, sourceLabel, airlineRebooking, score, reason);
    }
}
