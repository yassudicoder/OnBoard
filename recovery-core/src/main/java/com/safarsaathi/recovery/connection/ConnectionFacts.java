package com.safarsaathi.recovery.connection;

/**
 * Things about a connection that are confirmed and stay true, kept by the caller (the backend's connections table).
 * The flight data alone can't always tell them, e.g. once both flights have landed you can no longer see that flight 2
 * left first.
 */
public final class ConnectionFacts {

    /** The connection was confirmed missed earlier (flight 2 left first, or the airline said so). */
    public boolean missConfirmed;
    /** The flight the airline rebooked the traveller on, e.g. "MJ 33"; null = no confirmed rebooking. */
    public String rebookedFlight;
    /** The traveller made the connection or has a new plan. */
    public boolean resolved;
}
