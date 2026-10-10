package com.safarsaathi.backend.planb;

import java.time.OffsetDateTime;
import java.util.List;

import com.safarsaathi.recovery.alternatives.Urgency;
import com.safarsaathi.recovery.connection.Certainty;
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.plan.OvernightAdvice;
import com.safarsaathi.recovery.plan.PlanStep;
import com.safarsaathi.recovery.plan.RecoveryTrigger;
import com.safarsaathi.recovery.policy.ContactChannel;

/**
 * {@code GET /api/connections/{id}/plan}: everything the Recovery screen shows, and what the phone saves as its
 * offline "Plan B pack" (from C3). docs/api.md has an example.
 *
 * @param earliestDeparture when the traveller can leave on another flight, local time at {@code startAirport}
 * @param basedOnDataAt     when the flight data behind the plan arrived (Indian time)
 * @param dataCheckedAt     the last time the server confirmed the flight data: the later of basedOnDataAt and the
 *                          flight watcher's last completed check. The app's "as of" time
 * @param stale             true when dataCheckedAt is older than RecoverySettings.staleAfterMinutes: "may be outdated"
 * @param airlineRebooking  the flight the airline rebooked the traveller on (confirmed), or null
 * @param recommended       the one option to show first, or null when none was found
 * @param others            up to 2 more options
 * @param moreOptions       how many more ranked options {@code /alternatives} lists
 * @param rejectedCount     routes not offered because a change of planes is too short
 * @param whereToGo         "On arrival in Doha: follow signs to Transfers ..." (no invented desk locations)
 * @param overnight         {@code needed} = false when the next option is soon
 * @param contacts          the involved airlines' contact channels (fictional airlines: .example addresses only)
 * @param alternativesLabel "SAMPLE DATA: ..." when the alternatives come from the sample schedule, else null
 */
public record PlanResponse(
        long connectionId,
        long tripId,
        long planId,
        int planVersion,
        RecoveryTrigger trigger,
        String startAirport,
        String startAirportZone,
        String destination,
        ConnectionStatus connectionStatus,
        Certainty certainty,
        String connectionReason,
        boolean basedOnDemoValue,
        TicketType ticketType,
        Urgency urgency,
        OffsetDateTime earliestDeparture,
        OffsetDateTime generatedAt,
        OffsetDateTime basedOnDataAt,
        OffsetDateTime dataCheckedAt,
        boolean stale,
        Rebooking airlineRebooking,
        OptionResponse recommended,
        List<OptionResponse> others,
        int moreOptions,
        int rejectedCount,
        String whereToGo,
        List<PlanStep> steps,
        OvernightAdvice overnight,
        List<AirlineContact> contacts,
        String alternativesLabel,
        String alternativesProvider) {

    /**
     * The airline's rebooking. {@code departs} is null when not known; {@code note} says where the time comes from.
     */
    public record Rebooking(String flightNo, OffsetDateTime departs, String note) {
    }

    /** How to reach one airline. {@code kind} OFFICIAL or SAMPLE; null when the airline is not in the policy file. */
    public record AirlineContact(String airlineCode, String airlineName, boolean fictional, String kind,
                                 List<ContactChannel> channels) {
    }
}
