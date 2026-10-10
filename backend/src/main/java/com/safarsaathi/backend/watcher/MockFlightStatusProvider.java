package com.safarsaathi.backend.watcher;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.safarsaathi.backend.trip.Block;

/**
 * The pretend flight-status service (the default; needs no key). It knows nothing by itself: the demo buttons
 * ({@code POST /api/demo/trigger-delay} and {@code trigger-cancel}) tell it "block 1 is 90 minutes late" with
 * {@link #reportDelay} or {@link #reportCancel}, and the next {@link #check} of that block returns the change.
 *
 * <p>Each change is returned ONCE and then forgotten, just like a real airline announces a delay once.
 * Otherwise the watcher would apply the same 90 minutes again every 30 seconds.
 *
 * <p>The pending changes live in memory only (a restart forgets them), in a {@link ConcurrentHashMap} because web
 * requests and the watcher's timer use this object from different threads.
 */
@Component
public class MockFlightStatusProvider implements FlightStatusProvider {

    /** Block id -> the change waiting for that block's next check. */
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    /**
     * Block {@code blockId} will be {@code minutes} later than its current times. Two delays reported before the next
     * check add up (+30 then +60 = +90); a cancellation wins over any delay.
     */
    public void reportDelay(long blockId, int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("A delay must be more than 0 minutes");
        }
        pending.merge(blockId, new Pending(minutes, false), Pending::plus);
    }

    /** Block {@code blockId} is cancelled. */
    public void reportCancel(long blockId) {
        pending.put(blockId, Pending.CANCEL);
    }

    /** Forgets every change that was reported but not checked yet (used by {@code POST /api/demo/reset}). */
    public void clear() {
        pending.clear();
    }

    /** How many changes are waiting for a check. */
    public int pendingCount() {
        return pending.size();
    }

    @Override
    public Optional<FlightStatusChange> check(Block flight) {
        Pending change = pending.remove(flight.getId()); // remove = hand it out once
        if (change == null) {
            return Optional.empty();
        }
        if (change.cancelled()) {
            return Optional.of(FlightStatusChange.cancelled("Cancelled (mock flight status)"));
        }
        return Optional.of(FlightStatusChange.delayedBy(flight, change.delayMinutes(),
                "Delayed by " + change.delayMinutes() + " minutes (mock flight status)"));
    }

    @Override
    public String name() {
        return "MockFlightStatusProvider (MOCK: delays and cancellations come from the demo buttons)";
    }

    /** One reported change: a delay in minutes, or a cancellation. */
    private record Pending(int delayMinutes, boolean cancelled) {

        static final Pending CANCEL = new Pending(0, true);

        /** This change followed by a later one. */
        Pending plus(Pending later) {
            if (cancelled || later.cancelled) {
                return CANCEL;
            }
            return new Pending(delayMinutes + later.delayMinutes, false);
        }
    }
}
