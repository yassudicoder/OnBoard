package com.safarsaathi.recovery.alternatives;

/** Where an alternative comes from. The app labels SAMPLE options "SAMPLE DATA". */
public enum OptionSource {
    /** The team's invented schedule file (mock-data/recovery/sample-schedules.json). */
    SAMPLE,
    /** A real flight-search provider (future, e.g. Duffel test mode). */
    PROVIDER
}
