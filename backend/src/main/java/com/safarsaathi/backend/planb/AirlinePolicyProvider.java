package com.safarsaathi.backend.planb;

import com.safarsaathi.recovery.policy.AirlinePolicies;

/**
 * Where airline assistance policies and contact channels come from. Today {@link FileAirlinePolicyProvider} (the
 * team's file: SAMPLE for the fictional demo airlines, official pages with sourceUrl + lastChecked for real ones).
 */
public interface AirlinePolicyProvider {

    /** Never null. Checked at startup. */
    AirlinePolicies policies();

    String name();
}
