package com.safarsaathi.backend.watcher;

import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * When the flight watcher last finished checking every flight. A recovery plan's "as of" time is the later of this
 * and the moment its flight data last changed: "no news" from a working watcher still means the data is current.
 * A separate small bean, so the plan builder can read it without depending on the watcher itself.
 */
@Component
public class WatcherHeartbeat {

    private volatile Instant lastCheckCompleted;

    /** null until the first check has finished (the first one runs one interval after startup). */
    public Instant lastCheckCompleted() {
        return lastCheckCompleted;
    }

    void checkCompleted(Instant when) {
        this.lastCheckCompleted = when;
    }
}
