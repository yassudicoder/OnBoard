package com.safarsaathi.recovery.alternatives;

import java.time.Instant;

import com.safarsaathi.recovery.connection.TicketType;

/** What the ranking needs to know about the traveller's situation. */
public final class RankingRequest {

    public Urgency urgency = Urgency.NORMAL;
    /** SEPARATE_TICKETS = a new ticket must be bought, so its fare counts (and is shown as the fare difference). */
    public TicketType ticketType = TicketType.UNKNOWN;
    /** When the traveller was due at the destination (the timetable arrival of the flight being replaced). */
    public Instant originalArrival;
    /**
     * Tie-breaker only: between equally good options, prefer this airline (e.g. "MJ", the airline of the flight being
     * replaced, which is the most likely to rebook the traveller).
     */
    public String preferredAirline;
    /** "en", "hi" or "mr" for the sentences (English when the language has none yet). */
    public String language = "en";
}
