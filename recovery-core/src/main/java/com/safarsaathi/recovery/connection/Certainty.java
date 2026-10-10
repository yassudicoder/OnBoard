package com.safarsaathi.recovery.connection;

/**
 * How sure a connection status is. The app styles the two main kinds differently (CLAUDE.md, Disruption Recovery):
 * a PREDICTED status is our calculation from the latest flight times; a CONFIRMED one is a fact from the airline,
 * the flight data or the traveller.
 */
public enum Certainty {
    PREDICTED,
    CONFIRMED,
    /** OnBoard cannot tell (e.g. no minimum connection time for the airport). */
    UNKNOWN
}
