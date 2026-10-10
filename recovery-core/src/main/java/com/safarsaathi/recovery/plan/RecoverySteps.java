package com.safarsaathi.recovery.plan;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.safarsaathi.recovery.alternatives.OptionSource;
import com.safarsaathi.recovery.alternatives.RankedOption;
import com.safarsaathi.recovery.alternatives.TransitVisaStatus;
import com.safarsaathi.recovery.connection.FlightLeg;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.policy.AirlinePolicies;
import com.safarsaathi.recovery.policy.AirlinePolicy;
import com.safarsaathi.recovery.rules.AirportPack;
import com.safarsaathi.recovery.rules.AirportRules;
import com.safarsaathi.recovery.text.Sentences;

/**
 * The numbered next steps of a recovery plan (the same for all three triggers, adapted per case):
 * <ol>
 *   <li>check whether the airline already rebooked you (or: it did, check the new booking);</li>
 *   <li>ask the airline for rebooking or a refund (OnBoard prepares the request);</li>
 *   <li>compare the alternatives;</li>
 *   <li>check transit and visa rules for the chosen route;</li>
 *   <li>if overnight: what the airline provides (UNKNOWN when not in the policy file), keep receipts;</li>
 *   <li>confirm your choice: nothing is booked or paid without explicit approval.</li>
 * </ol>
 * Plus one "where to go now" line: the transfer desk at a hub, the airline's counter at the departure airport. No
 * invented desk locations or gates.
 */
public final class RecoverySteps {

    /** What the steps are built from. */
    public static final class Input {
        public RecoveryStart start;
        public TicketType ticketType = TicketType.UNKNOWN;
        /** The flight the airline rebooked the traveller on (confirmed), or null. */
        public String rebookedFlight;
        /** Its departure, when known. */
        public Instant rebookedDeparture;
        /** The recommended alternative, or null when there is none. */
        public RankedOption recommended;
        public OvernightAdvice overnight;
        public int searchWindowHours;
        public AirlinePolicies policies;
        public AirportRules rules;
        public String language = "en";
    }

    private RecoverySteps() {
    }

    public static List<PlanStep> build(Input in) {
        Sentences text = Sentences.load(RecoverySteps.class, "plan_text", in.language);
        List<PlanStep> steps = new ArrayList<>();
        String airline = airlineName(in.start.replacedFlight, in.policies);
        String destination = city(in.start.destination, in.rules, text);

        if (in.rebookedFlight != null) {
            String when = "";
            if (in.rebookedDeparture != null) {
                ZonedDateTime local = in.rebookedDeparture.atZone(in.start.zone);
                when = text.get("rebooked_when", "city", city(in.start.airport, in.rules, text),
                        "time", text.timeWithDay(local));
            }
            add(steps, "CHECK_REBOOKING", text.get("rebooked", "flight", in.rebookedFlight, "when", when));
            add(steps, "ASK_AIRLINE", text.get("ask_airline_rebooked"));
        } else {
            String check;
            if (in.ticketType == TicketType.SINGLE_BOOKING) {
                check = text.get("check_rebooking_single", "airline", airline, "destination", destination);
            } else if (in.ticketType == TicketType.SEPARATE_TICKETS) {
                check = text.get("check_rebooking_separate", "airline", airline);
            } else {
                check = text.get("check_rebooking_unknown");
            }
            add(steps, "CHECK_REBOOKING", check);
            add(steps, "ASK_AIRLINE", text.get("ask_airline"));
        }

        RankedOption best = in.recommended;
        if (best == null) {
            add(steps, "COMPARE", text.get("compare_none", "hours", String.valueOf(in.searchWindowHours)));
        } else {
            FlightLeg last = best.route.legs.get(best.route.legs.size() - 1);
            String sample = best.route.source == OptionSource.SAMPLE ? text.get("compare_sample_label") : "";
            add(steps, "COMPARE", text.get("compare", "flights", best.route.flightNumbers(),
                    "destination", destination, "arrives", text.timeWithDay(last.arrival().atZone(last.arrZone)),
                    "sample", sample));
        }

        String transitStatus = "";
        if (best != null) {
            if (best.route.stops() == 0) {
                transitStatus = text.get("transit_status_none");
            } else if (best.transitVisaStatus == TransitVisaStatus.REQUIRED) {
                transitStatus = text.get("transit_status_required");
            } else if (best.transitVisaStatus == TransitVisaStatus.UNKNOWN) {
                transitStatus = text.get("transit_status_unknown");
            }
        }
        add(steps, "TRANSIT", text.get("transit", "status", transitStatus));

        OvernightAdvice overnight = in.overnight;
        if (overnight != null && overnight.needed) {
            Instant next = in.rebookedDeparture != null ? in.rebookedDeparture
                    : best != null ? best.route.departure() : null;
            String time = next == null ? "" : text.timeWithDay(next.atZone(in.start.zone));
            add(steps, "OVERNIGHT", text.get("overnight", "time", time, "airline", overnight.airlineName,
                    "unknown", overnight.policyText == null ? text.get("overnight_unknown") : ""));
        }
        add(steps, "CONFIRM", text.get("confirm"));
        return steps;
    }

    /** "On arrival in Doha: follow signs to Transfers ..." or "At Delhi airport: ask at the IndiJet counter ...". */
    public static String whereToGo(RecoveryStart start, AirlinePolicies policies, AirportRules rules,
                                   String language) {
        Sentences text = Sentences.load(RecoverySteps.class, "plan_text", language);
        String city = city(start.airport, rules, text);
        if (start.atHub) {
            return text.get("at_hub", "city", city);
        }
        return text.get("at_departure", "city", city, "airline", airlineName(start.disruptedFlight, policies));
    }

    private static void add(List<PlanStep> steps, String kind, String text) {
        steps.add(new PlanStep(steps.size() + 1, kind, text));
    }

    private static String airlineName(FlightLeg flight, AirlinePolicies policies) {
        String code = flight.airlineCode();
        AirlinePolicy policy = policies.find(code);
        return policy != null && policy.name != null ? policy.name : code;
    }

    private static String city(String iata, AirportRules rules, Sentences text) {
        AirportPack airport = rules.find(iata);
        return airport != null ? airport.cityName(text.language()) : iata;
    }
}
