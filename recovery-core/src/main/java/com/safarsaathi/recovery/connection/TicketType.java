package com.safarsaathi.recovery.connection;

/**
 * Are both flights of a connection on one ticket? It changes the advice (C2): on one booking the airline is normally
 * responsible for getting the traveller to the destination; on separate tickets the second airline may treat them as
 * a no-show. Saved by name, e.g. "SINGLE_BOOKING".
 */
public enum TicketType {
    SINGLE_BOOKING,
    SEPARATE_TICKETS,
    /** Not known yet: the app asks the traveller once. */
    UNKNOWN
}
