package com.safarsaathi.recovery.connection;

/**
 * Where a flight is in its day, as far as OnBoard knows (from flight data; in the demo from the demo buttons).
 * A cancelled flight is marked separately ({@link FlightLeg#cancelled}). Saved by name, e.g. "IN_AIR".
 */
public enum FlightPhase {
    /** Nothing reported yet: the flight is still to leave. */
    SCHEDULED,
    BOARDING,
    /** The flight has left the gate. */
    IN_AIR,
    LANDED;

    /** True once the plane has left: IN_AIR or LANDED. */
    public boolean hasDeparted() {
        return this == IN_AIR || this == LANDED;
    }
}
