package com.safarsaathi.recovery.rules;

/** The values the checker uses when an airport (or, from C2, an airline) has nothing more specific. */
public final class RulesDefaults {

    /** Taxiing, getting off the plane, transfer security: team estimates. */
    public TransferEstimates transfer;
    /** When the gate closes before departure if neither the airport nor the airline says: a team estimate. */
    public SourcedMinutes gateClose;
    /**
     * Check-in, security and passport control before a new departure, when a flight is cancelled before it leaves
     * (the plan then starts at the departure airport): a team estimate.
     */
    public SourcedMinutes departureProcess;
    /**
     * A product choice, not a fact: a connection that meets the minimum but leaves less than this many minutes to
     * spare is shown as "at risk".
     */
    public int atRiskMarginMinutes;
    public String atRiskMarginNote;
}
