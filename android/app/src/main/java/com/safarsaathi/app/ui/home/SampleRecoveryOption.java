package com.safarsaathi.app.ui.home;

import java.time.Duration;
import java.time.Instant;

/**
 * Stand-in for Disruption Recovery, which is parked (CLAUDE.md "Parked": Plan B): ONE made-up alternative flight,
 * shown on Home only after the demo cancels the flight (/demo, "Cancel booking"). The screen labels it
 * "SAMPLE (demo data, not a real flight)". It exists so the "ride to another airport" button can be shown; replace
 * it with real alternatives when Disruption Recovery is built.
 */
final class SampleRecoveryOption {

    /** IndiJet is the demo's invented airline (mock-data/demo-trips.json). */
    static final String FLIGHT = "IJ 518";
    /** Leaves from Mumbai T1, which ride-points.json lists as near the pilot terminal (T2). */
    static final String AIRPORT = "BOM";
    static final String TERMINAL = "T1";

    private static final Duration AFTER_CANCELLED_FLIGHT = Duration.ofHours(3);

    private SampleRecoveryOption() {
    }

    /** The sample departs 3 hours after the cancelled flight was due, so the demo never goes stale. */
    static Instant departs(Instant cancelledDeparture) {
        return cancelledDeparture.plus(AFTER_CANCELLED_FLIGHT);
    }
}
