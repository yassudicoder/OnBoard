package com.safarsaathi.recovery.alternatives;

/**
 * The honest default: OnBoard has no transit-visa rules, so every change of planes is UNKNOWN ("check transit
 * requirements"). A direct flight needs no transit at all; the ranker handles that before asking.
 */
public final class NoTransitData implements TransitRequirementProvider {

    @Override
    public TransitVisaStatus statusAt(String airport, boolean separateTickets) {
        return TransitVisaStatus.UNKNOWN;
    }
}
