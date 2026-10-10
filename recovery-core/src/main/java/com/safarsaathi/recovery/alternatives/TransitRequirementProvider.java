package com.safarsaathi.recovery.alternatives;

/**
 * Does changing planes at an airport need a transit visa for this traveller? Behind an interface because the answer
 * needs the traveller's documents and official rules: on the phone the Document Vault's checks engine can answer
 * (when it is there); the server never sees passports, so it uses {@link NoTransitData} and says UNKNOWN.
 */
public interface TransitRequirementProvider {

    /**
     * @param airport         where the traveller changes planes, e.g. "DXB"
     * @param separateTickets true when the two flights are on separate tickets (self-transfer often means entering
     *                        the country to collect bags and check in again)
     * @return OK, REQUIRED or UNKNOWN; never a guess
     */
    TransitVisaStatus statusAt(String airport, boolean separateTickets);
}
