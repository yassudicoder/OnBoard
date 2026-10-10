package com.safarsaathi.backend.connection;

import com.safarsaathi.recovery.rules.AirportRules;

/**
 * Where the airport rules come from: time zones, minimum connection times, gate closing, nearby airports.
 * Today one implementation, {@link FileAirportRulesProvider} (the team's data file, no key needed). A real
 * minimum-connection-time data feed would be a second implementation behind this same interface.
 */
public interface AirportRulesProvider {

    /** The rules; never null. Checked at startup, so every value has its source or label. */
    AirportRules rules();

    /** A short name for the logs. */
    String name();
}
