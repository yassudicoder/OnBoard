package com.safarsaathi.recovery.connection;

/**
 * Where a connection (flight 1 lands, flight 2 leaves from the same airport) stands. Saved and sent by name, e.g.
 * "LIKELY_MISSED". Each status says whether it is a prediction or a confirmed fact ({@link #certainty()}).
 */
public enum ConnectionStatus {
    /** Predicted: enough time, with room to spare. */
    OK(Certainty.PREDICTED),
    /** Predicted: meets the minimum connection time, but with little room to spare. */
    AT_RISK(Certainty.PREDICTED),
    /** Predicted: below the minimum connection time, or flight 2 leaves before flight 1 lands. */
    LIKELY_MISSED(Certainty.PREDICTED),
    /** Confirmed: flight 2 left while the traveller was not there yet, or the airline says the connection is missed. */
    CONFIRMED_MISSED(Certainty.CONFIRMED),
    /** Confirmed: flight 1 or flight 2 is cancelled. */
    CANCELLED(Certainty.CONFIRMED),
    /** Confirmed: the airline rebooked the traveller (only from airline data or the traveller's own word). */
    REBOOKED_BY_AIRLINE(Certainty.CONFIRMED),
    /** Confirmed: the traveller made the connection or has a new plan. */
    RESOLVED(Certainty.CONFIRMED),
    /** OnBoard cannot tell, e.g. the airport has no minimum connection time in our data. Never guessed. */
    UNKNOWN(Certainty.UNKNOWN);

    private final Certainty certainty;

    ConnectionStatus(Certainty certainty) {
        this.certainty = certainty;
    }

    public Certainty certainty() {
        return certainty;
    }
}
