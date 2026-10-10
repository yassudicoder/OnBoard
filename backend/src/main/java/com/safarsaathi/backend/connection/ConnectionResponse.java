package com.safarsaathi.backend.connection;

import java.time.OffsetDateTime;

import com.safarsaathi.recovery.alternatives.Urgency;
import com.safarsaathi.recovery.connection.Certainty;
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.rules.ConnectionType;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * One connection in {@code GET /api/trips/{id}/connections} (docs/api.md has an example).
 *
 * @param certainty             PREDICTED (OK, AT_RISK, LIKELY_MISSED), CONFIRMED (CONFIRMED_MISSED, CANCELLED,
 *                              REBOOKED_BY_AIRLINE, RESOLVED) or UNKNOWN: the app styles them differently
 * @param landsAt               flight 1's landing in the connection airport's local time, e.g. +03:00 in Doha
 * @param nextLeavesAt          flight 2's departure, same local time
 * @param availableMinutes      landing to next departure; negative when flight 2 leaves first
 * @param neededMinutes         the minimum connection time; null = UNKNOWN (no figure for this airport)
 * @param projectedSlackMinutes available - needed; negative = minutes short; null when needed is unknown
 * @param mct                   the minimum connection time with its source; null when unknown
 * @param transfer              taxiing + getting off + transfer security: always an ESTIMATE
 * @param gateClose             how long before departure the gate closes, with its source
 * @param basedOnDemoValue      true = the status rests on a DEMO VALUE minimum; the app must say "based on a demo
 *                              minimum connection time"
 * @param reason                plain English, local airport times
 * @param urgency               NORMAL or URGENT: how the traveller wants alternatives ranked (C2)
 * @param rebookedFlight        the flight the airline rebooked the traveller on (confirmed), or null (C2)
 * @param rebookedDeparture     its departure in the connection airport's local time, or null when not known
 * @param missConfirmedAt       when the miss was first confirmed, or null
 * @param updatedAt             when the server last checked this connection (Indian time, like createdAt)
 * @param dataAsOf              when the flight data behind it arrived: the app's "as of" time
 */
public record ConnectionResponse(
        long id,
        long tripId,
        long inboundBlockId,
        long outboundBlockId,
        String inboundFlightNo,
        String outboundFlightNo,
        String airport,
        String airportZone,
        ConnectionType connectionType,
        Boolean terminalChange,
        ConnectionStatus status,
        Certainty certainty,
        OffsetDateTime landsAt,
        OffsetDateTime nextLeavesAt,
        int availableMinutes,
        Integer neededMinutes,
        Integer projectedSlackMinutes,
        Sourced mct,
        Sourced transfer,
        Sourced gateClose,
        TicketType ticketType,
        boolean basedOnDemoValue,
        String reason,
        Urgency urgency,
        String rebookedFlight,
        OffsetDateTime rebookedDeparture,
        OffsetDateTime missConfirmedAt,
        OffsetDateTime updatedAt,
        OffsetDateTime dataAsOf) {

    /**
     * A number of minutes with where it comes from.
     *
     * @param kind        OFFICIAL (sourceUrl set), DEMO_VALUE ("demo value") or ESTIMATE ("estimate")
     * @param lastChecked yyyy-MM-dd, or null for an estimate
     */
    public record Sourced(int minutes, ValueKind kind, String source, String sourceUrl, String lastChecked) {
    }
}
