package com.safarsaathi.app.data.vault;

/** What a country is for this trip. The pre-departure checks (V4) use it: entry rules differ from transit rules. */
public enum CountryRole {
    ORIGIN,
    TRANSIT,
    DESTINATION
}
