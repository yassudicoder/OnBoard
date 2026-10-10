package com.safarsaathi.recovery.rules;

/**
 * How far to trust a number in the airport rules file ({@code mock-data/recovery/airport-rules.json}), and so how the
 * app labels it. Saved in the file by name, e.g. "DEMO_VALUE".
 */
public enum ValueKind {
    /** Copied from an official airport or airline page: the entry has a sourceUrl and a lastChecked date. */
    OFFICIAL,
    /**
     * Set by the team because no official figure is public. The app always says "demo value" next to it, and every
     * result that depends on it says "based on a demo minimum connection time".
     */
    DEMO_VALUE,
    /** The team's rough estimate, e.g. how long taxiing takes. The app says "estimate". */
    ESTIMATE
}
