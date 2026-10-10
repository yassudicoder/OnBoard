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
 * <p>A change stays here until the watcher has SAVED it ({@link #applied}). If saving fails, the next check hands it
 * out again, so a delay is never lost. Once saved it is forgotten, just like a real airline announces a delay once;
 * otherwise the watcher would apply the same 90 minutes again every 30 seconds.
 *
 * <p>The pending changes live in memory only (a restart forgets them), in a {@link ConcurrentHashMap} because web
 * requests and the watcher's timer use this object from different threads.
 */
@Component
public class MockFlightStatusProvider implements FlightStatusProvider {

    /** Block id -> the change waiting to be saved for that block. */
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    /**
     * Block {@code blockId} will be {@code minutes} later than its current times. Two delays reported before the
     * change is saved add up (+30 then +60 = +90); a cancellation wins over any delay.
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

    /** Forgets every change that was reported but not saved yet (used by {@code POST /api/demo/reset}). */
    public void clear() {
        pending.clear();
    }

    /** How many changes are waiting to be saved. */
    public int pendingCount() {
        return pending.size();
    }

    /** The change waiting for this block, if any. With {@link #restore}: undo a report whose saving failed. */
    public Optional<Pending> pendingFor(long blockId) {
        return Optional.ofNullable(pending.get(blockId));
    }

    /**
     * Puts back what {@link #pendingFor} returned earlier. A demo button uses it when saving its change failed: the
     * caller got an error, so the change must not stay behind and be applied later by the timer.
     */
    public void restore(long blockId, Optional<Pending> previous) {
        previous.ifPresentOrElse(change -> pending.put(blockId, change), () -> pending.remove(blockId));
    }

    @Override
    public Optional<FlightStatusChange> check(Block flight) {
        Pending change = pending.get(flight.getId()); // NOT removed here: see applied()
        if (change == null) {
            return Optional.empty();
        }
        if (change.cancelled()) {
            return Optional.of(FlightStatusChange.cancelled("Cancelled (mock flight status)"));
        }
        return Optional.of(FlightStatusChange.delayedBy(change.delayMinutes(),
                "Delayed by " + change.delayMinutes() + " minutes (mock flight status)"));
    }

    /**
     * The watcher saved {@code saved}: take exactly that out of what is waiting. Anything reported after the check
     * stays (e.g. checked +30, then +60 more reported: +60 is left for the next check).
     */
    @Override
    public void applied(Block flight, FlightStatusChange saved) {
        pending.computeIfPresent(flight.getId(), (id, waiting) -> waiting.minus(saved));
    }

    @Override
    public String name() {
        return "MockFlightStatusProvider (MOCK: delays and cancellations come from the demo buttons)";
    }

    /**
     * One reported change that is not saved yet: a delay in minutes, or a cancellation.
     *
     * @param delayMinutes minutes of delay (0 for a cancellation)
     * @param cancelled    true for a cancellation
     */
    public record Pending(int delayMinutes, boolean cancelled) {

        static final Pending CANCEL = new Pending(0, true);

        /** This change followed by a later one. */
        Pending plus(Pending later) {
            if (cancelled || later.cancelled) {
                return CANCEL;
            }
            return new Pending(delayMinutes + later.delayMinutes, false);
        }

        /** What is still waiting once {@code saved} is saved; null = nothing (the map then drops the entry). */
        Pending minus(FlightStatusChange saved) {
            if (saved.cancelled()) {
                return null; // the cancellation is saved: nothing is left to do for this block
            }
            if (cancelled || saved.delayMinutes() == null) {
                return this; // a cancellation reported meanwhile is still to do (or the change was not ours)
            }
            int left = delayMinutes - saved.delayMinutes();
            return left > 0 ? new Pending(left, false) : null;
        }
    }
}
