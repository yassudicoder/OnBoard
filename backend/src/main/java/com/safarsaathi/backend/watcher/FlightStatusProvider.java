package com.safarsaathi.backend.watcher;

import java.util.Optional;

import com.safarsaathi.backend.trip.Block;

/**
 * Somewhere to ask "is this flight still on time?". The {@link FlightWatcher} asks it about every flight block.
 *
 * <p>Two implementations (CLAUDE.md rule: every outside service has a mock that needs no key):
 * <ul>
 *   <li>{@link MockFlightStatusProvider}: the default. Reports only the changes the demo buttons gave it.</li>
 *   <li>{@link AeroDataBoxProvider}: real flight status from AeroDataBox (RapidAPI), used only when
 *       AERODATABOX_API_KEY is set.</li>
 * </ul>
 */
public interface FlightStatusProvider {

    /**
     * Has this flight changed? The answer is relative to the block's CURRENT times (see {@link FlightStatusChange}).
     * Must never throw: a provider that cannot find out (no network, unknown flight, ...) answers "no change".
     *
     * @return the change, or empty when the flight is as planned (or nobody knows)
     */
    Optional<FlightStatusChange> check(Block flight);

    /** A short name for the logs, e.g. "MockFlightStatusProvider (MOCK)". */
    String name();
}
