package com.safarsaathi.recovery.alternatives;

/** Can the traveller change planes on this route without a transit visa, as far as OnBoard knows? */
public enum TransitVisaStatus {
    /** No transit at all (a direct flight), or a checked source says no visa is needed. */
    OK,
    /** A checked source says a transit visa is needed. */
    REQUIRED,
    /** Not known: shown as "transit visa: UNKNOWN, check transit requirements". */
    UNKNOWN
}
