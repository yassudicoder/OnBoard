package com.safarsaathi.app.data.vault;

/**
 * One part of a journey, as the trip folder groups documents: a FLIGHT ("Delhi → Kigali"), a TRANSIT stop
 * ("Kigali transit") or a STAY at a destination ("Kinshasa").
 */
public enum LegKind {
    FLIGHT,
    TRANSIT,
    STAY
}
