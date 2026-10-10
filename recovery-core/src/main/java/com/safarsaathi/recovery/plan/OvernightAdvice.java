package com.safarsaathi.recovery.plan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.policy.AirlinePolicies;
import com.safarsaathi.recovery.policy.AirlinePolicy;
import com.safarsaathi.recovery.policy.RightsLink;
import com.safarsaathi.recovery.rules.AirportPack;
import com.safarsaathi.recovery.rules.AirportRules;
import com.safarsaathi.recovery.text.Sentences;

/**
 * The Overnight section of a recovery plan. Shown ({@link #needed}) when the next suitable flight leaves more than
 * N hours after the traveller could leave (RecoverySettings.overnightThresholdHours) or on a later day.
 *
 * <p>It only passes on what is known: the airline's own words from the policy file (or "ask the airline" when there
 * are none), a general "passenger-rights rules may apply" with official links, and "keep all receipts". It never
 * promises a hotel, meals or compensation, and OnBoard never books a hotel without explicit approval.
 */
public final class OvernightAdvice {

    public boolean needed;
    /** "Your next suitable flight leaves Doha at 07:40 on 12 Oct, 9 h 30 min after you could leave." */
    public String reason;
    public int waitMinutes;
    /** The airline whose flight was delayed or cancelled. */
    public String airlineCode;
    public String airlineName;
    /** The airline's own words; null = not known (then {@link #policyUnknownText} says to ask). */
    public String policyText;
    public String policyUnknownText;
    /** OFFICIAL or SAMPLE (fictional airline); null when the airline is not in the policy file. */
    public String policyKind;
    public String policySourceUrl;
    public String policyLastChecked;
    public String passengerRightsText;
    public List<RightsLink> passengerRightsLinks = new ArrayList<>();
    public String receiptsText;
    public String noBookingText;
    /** Only for separate tickets: who is (usually not) responsible. */
    public String separateTicketsText;
    /** Only at a hub: leaving the transit area may need permission to enter the country. */
    public String leavingTransitText;
    /** The hotel and meal request line for the airline request draft (C4). */
    public String requestLine;

    /**
     * @param nextDeparture the recommended option's departure (or the airline's rebooking); null = no option
     */
    public static OvernightAdvice build(RecoveryStart start, Instant nextDeparture, TicketType ticketType,
                                        AirlinePolicies policies, RecoverySettings settings, AirportRules rules,
                                        String language) {
        OvernightAdvice advice = new OvernightAdvice();
        if (nextDeparture == null) {
            return advice; // nothing to wait for: needed stays false
        }
        ZonedDateTime from = start.earliestDeparture.atZone(start.zone);
        ZonedDateTime next = nextDeparture.atZone(start.zone);
        long wait = Math.max(0, Duration.between(start.earliestDeparture, nextDeparture).toMinutes());
        advice.needed = wait > settings.overnightThresholdHours * 60L
                || next.toLocalDate().isAfter(from.toLocalDate());
        if (!advice.needed) {
            return advice;
        }
        Sentences text = Sentences.load(OvernightAdvice.class, "plan_text", language);
        AirportPack airport = rules.find(start.airport);
        String city = airport != null ? airport.cityName(text.language()) : start.airport;
        advice.waitMinutes = (int) wait;
        advice.reason = text.get("overnight_reason", "city", city, "time", text.timeWithDay(next),
                "wait", text.duration((int) wait));

        advice.airlineCode = start.disruptedFlight.airlineCode();
        AirlinePolicy policy = policies.find(advice.airlineCode);
        advice.airlineName = policy != null && policy.name != null ? policy.name : advice.airlineCode;
        if (policy != null) {
            advice.policyText = policy.delayAssistanceText;
            advice.policyKind = policy.kind;
            advice.policySourceUrl = policy.sourceUrl;
            advice.policyLastChecked = policy.lastChecked;
        }
        if (advice.policyText == null) {
            advice.policyUnknownText = text.get("policy_unknown");
        }
        advice.passengerRightsText = policies.passengerRightsText;
        advice.passengerRightsLinks = policies.passengerRightsLinks;
        advice.receiptsText = text.get("keep_receipts");
        advice.noBookingText = text.get("no_hotel_booking");
        if (ticketType == TicketType.SEPARATE_TICKETS) {
            advice.separateTicketsText = text.get("overnight_separate", "airline", advice.airlineName);
        }
        if (start.atHub) {
            advice.leavingTransitText = text.get("leaving_transit", "city", city);
        }
        advice.requestLine = text.get("request_line", "time", text.timeWithDay(next));
        return advice;
    }
}
