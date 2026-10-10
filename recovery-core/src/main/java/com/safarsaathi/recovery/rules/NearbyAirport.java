package com.safarsaathi.recovery.rules;

/**
 * Another airport a traveller could reasonably leave from instead, with the ground transfer time to get there.
 * Listed only when the time has a source: alternatives never use a distance we made up (C2).
 */
public final class NearbyAirport {

    public String iata;
    public int groundTransferMinutes;
    public String source;
    public String sourceUrl;
    /** yyyy-MM-dd */
    public String lastChecked;
}
