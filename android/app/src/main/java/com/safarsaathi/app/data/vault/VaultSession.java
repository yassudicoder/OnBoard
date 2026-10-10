package com.safarsaathi.app.data.vault;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Is the vault unlocked right now? This state lives only in memory, so a restarted app always starts locked.
 *
 * <p>Rules:
 * <ul>
 *   <li>Lock switched off in the vault settings: always unlocked.</li>
 *   <li>Otherwise locked until {@link #unlock()} is called (after a successful fingerprint / face / PIN prompt).</li>
 *   <li>Auto-lock: locked again once the vault has not been used for the chosen number of minutes. "Used" =
 *       {@link #touch()}, which MainActivity calls on every tap or swipe while a vault screen is showing. Time
 *       spent in other tabs or outside the app counts as not used.</li>
 * </ul>
 * Plain Java with an injected {@link Clock}, so the timing is unit-tested without waiting.
 */
public final class VaultSession {

    /** The two settings the lock needs (VaultSettings on the phone, a simple fake in tests). */
    public interface LockPolicy {
        boolean lockEnabled();

        int autoLockMinutes();
    }

    private final Clock clock;
    private final LockPolicy policy;

    /** Last time the vault was used while unlocked; null = locked. */
    private Instant lastUsed;

    public VaultSession(Clock clock, LockPolicy policy) {
        this.clock = clock;
        this.policy = policy;
    }

    public synchronized boolean isUnlocked() {
        if (!policy.lockEnabled()) {
            return true;
        }
        if (lastUsed == null) {
            return false;
        }
        Instant lockAt = lastUsed.plus(Duration.ofMinutes(policy.autoLockMinutes()));
        if (!clock.instant().isBefore(lockAt)) {
            lastUsed = null; // auto-lock
            return false;
        }
        return true;
    }

    /** The traveller just proved it is them (fingerprint, face or PIN). */
    public synchronized void unlock() {
        lastUsed = clock.instant();
    }

    /** "Lock now", and after the whole vault is deleted. */
    public synchronized void lockNow() {
        lastUsed = null;
    }

    /** The traveller used a vault screen: restart the auto-lock countdown. Does nothing while locked. */
    public synchronized void touch() {
        if (lastUsed != null && isUnlocked()) {
            lastUsed = clock.instant();
        }
    }
}
