package com.safarsaathi.backend.planb;

import java.util.List;

import com.safarsaathi.recovery.alternatives.Urgency;
import com.safarsaathi.recovery.connection.TicketType;

/**
 * {@code GET /api/connections/{id}/alternatives}: every ranked option the plan kept (best first, the recommended one
 * included) and every rejected route with the reason it is not offered.
 */
public record AlternativesResponse(
        long connectionId,
        int planVersion,
        Urgency urgency,
        TicketType ticketType,
        String alternativesLabel,
        List<OptionResponse> options,
        List<OptionResponse> rejected) {
}
