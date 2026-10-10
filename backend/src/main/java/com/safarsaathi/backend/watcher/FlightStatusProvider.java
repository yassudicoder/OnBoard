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
 *
 * <p>Asking and forgetting are two steps on purpose: {@link #check} must NOT forget a change, because saving it can
 * still fail (database down, ...). The watcher calls {@link #applied} only after the change is safely saved. If
 * saving fails, the next check simply hands the same change out again, so nothing is lost and nothing is saved twice.
 */
public interface FlightStatusProvider {

    /**
     * Has this flight changed? Must never throw: a provider that cannot find out (no network, unknown flight, ...)
     * answers "no change". Must not forget the change it hands out (see the class comment).
     *
     * @return the change, or empty when the flight is as planned (or nobody knows)
     */
    Optional<FlightStatusChange> check(Block flight);

    /**
     * The watcher saved {@code change} for this flight (the database transaction is committed): forget it, so the
     * next check does not hand it out again. The default does nothing, which is right for a provider whose answer
     * already compares with the block's saved times (asking again after saving then gives "no change").
     */
    default void applied(Block flight, FlightStatusChange change) {
    }

    /** A short name for the logs, e.g. "MockFlightStatusProvider (MOCK)". */
    String name();
}
