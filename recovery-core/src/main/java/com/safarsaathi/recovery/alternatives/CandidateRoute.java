package com.safarsaathi.recovery.alternatives;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.safarsaathi.recovery.connection.FlightLeg;

/**
 * One way to the destination as a provider offers it: one or more flights, before OnBoard checks and ranks it.
 * Each leg's times are its timetable times with the airports' time zones (see {@link FlightLeg}).
 */
public final class CandidateRoute {

    /** The flights in order: one for a direct flight, two for one change of planes. */
    public List<FlightLeg> legs = new ArrayList<>();
    /** The price of the new ticket(s) in whole rupees; null = not known. A SAMPLE fare for SAMPLE routes. */
    public Integer fareInr;
    /** Change and refund rules in words, e.g. "SAMPLE: changes ₹3,000 + fare difference"; null = not known. */
    public String fareRules;
    public SeatAvailability seats = SeatAvailability.UNKNOWN;
    public OptionSource source = OptionSource.SAMPLE;

    public Instant departure() {
        return legs.get(0).departure();
    }

    public Instant arrival() {
        return legs.get(legs.size() - 1).arrival();
    }

    /** Changes of planes: 0 for a direct flight. */
    public int stops() {
        return legs.size() - 1;
    }

    /**
     * Do the flights belong to different airlines? In OnBoard's SAMPLE data each airline sells its own ticket, so
     * different airlines = separate tickets (a real provider would say).
     */
    public boolean separateTickets() {
        String first = legs.get(0).airlineCode();
        for (FlightLeg leg : legs) {
            String airline = leg.airlineCode();
            if (airline == null || !airline.equals(first)) {
                return true;
            }
        }
        return false;
    }

    /** Uses this airline on at least one leg. */
    public boolean usesAirline(String airlineCode) {
        if (airlineCode == null) {
            return false;
        }
        for (FlightLeg leg : legs) {
            if (airlineCode.equals(leg.airlineCode())) {
                return true;
            }
        }
        return false;
    }

    /** "MJ 33" or "DA 112 + DA 5": the flight numbers, for sentences. */
    public String flightNumbers() {
        StringBuilder text = new StringBuilder();
        for (FlightLeg leg : legs) {
            if (text.length() > 0) {
                text.append(" + ");
            }
            text.append(leg.flightNo);
        }
        return text.toString();
    }

    /**
     * A stable name for this exact route, e.g. "DA 112@2026-10-11T22:45:00Z+DA 5@2026-10-12T02:00:00Z": the same
     * flights at the same times always give the same key (used to say "option 2 is no longer available").
     */
    public String key() {
        StringBuilder key = new StringBuilder();
        for (FlightLeg leg : legs) {
            if (key.length() > 0) {
                key.append('+');
            }
            key.append(leg.flightNo).append('@').append(leg.departure());
        }
        return key.toString();
    }
}
