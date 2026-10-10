package com.safarsaathi.app.data.ride;

/** Where a "Get a ride" button sits in the app. Used for the local tap counts (RideTapCounter). */
public enum RidePlacement {
    /** Flight card on Home: ride to the departure terminal. */
    DEPARTURE,
    /** "After you land" card on Home: ride to the hotel or another place. */
    ARRIVAL,
    /** "Flight cancelled" card on Home: ride to another airport / terminal an alternative leaves from. */
    RECOVERY
}
