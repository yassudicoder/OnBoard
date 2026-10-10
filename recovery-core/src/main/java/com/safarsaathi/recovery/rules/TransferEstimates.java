package com.safarsaathi.recovery.rules;

/**
 * How long the walk from the arriving plane to the next gate takes in practice, beyond the published minimum:
 * taxiing to the stand, getting off the plane, and transfer security. These are the team's estimates, never official
 * figures, so {@link #kind} is ESTIMATE and the app says "estimate".
 */
public final class TransferEstimates {

    public int taxiMinutes;
    public int deplaningMinutes;
    public int transferSecurityMinutes;
    public ValueKind kind;
    public String source;

    /** For the JSON readers. */
    public TransferEstimates() {
    }

    public TransferEstimates(int taxiMinutes, int deplaningMinutes, int transferSecurityMinutes, ValueKind kind,
                             String source) {
        this.taxiMinutes = taxiMinutes;
        this.deplaningMinutes = deplaningMinutes;
        this.transferSecurityMinutes = transferSecurityMinutes;
        this.kind = kind;
        this.source = source;
    }

    public int totalMinutes() {
        return taxiMinutes + deplaningMinutes + transferSecurityMinutes;
    }
}
