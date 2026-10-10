package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Unit tests for the vault lock and auto-lock ({@link VaultSession}), with a clock the test moves by hand. */
public class VaultSessionTest {

    /** A clock that only moves when the test says so. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-12T06:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** The two lock settings, without SharedPreferences. */
    private static final class Policy implements VaultSession.LockPolicy {
        boolean lockEnabled = true;
        int minutes = 5;

        @Override
        public boolean lockEnabled() {
            return lockEnabled;
        }

        @Override
        public int autoLockMinutes() {
            return minutes;
        }
    }

    private final TestClock clock = new TestClock();
    private final Policy policy = new Policy();
    private final VaultSession session = new VaultSession(clock, policy);

    @Test
    public void startsLocked_untilTheTravellerProvesItIsThem() {
        assertFalse(session.isUnlocked());
        session.unlock();
        assertTrue(session.isUnlocked());
    }

    @Test
    public void locksItselfAfterTheChosenMinutesWithoutUse() {
        session.unlock();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertTrue(session.isUnlocked());
        clock.advance(Duration.ofSeconds(1));
        assertFalse(session.isUnlocked());
        // Once locked it stays locked, even if the time setting changes afterwards.
        policy.minutes = 15;
        assertFalse(session.isUnlocked());
    }

    @Test
    public void usingTheVault_restartsTheCountdown() {
        session.unlock();
        clock.advance(Duration.ofMinutes(4));
        session.touch();
        clock.advance(Duration.ofMinutes(4));
        assertTrue(session.isUnlocked());
        clock.advance(Duration.ofMinutes(1));
        assertFalse(session.isUnlocked());
    }

    @Test
    public void touchingALockedVault_doesNotUnlockIt() {
        session.touch();
        assertFalse(session.isUnlocked());

        session.unlock();
        clock.advance(Duration.ofMinutes(5));
        session.touch(); // too late: the time had already run out
        assertFalse(session.isUnlocked());
    }

    @Test
    public void lockNow_locksAtOnce() {
        session.unlock();
        session.lockNow();
        assertFalse(session.isUnlocked());
    }

    @Test
    public void lockSwitchedOff_isAlwaysOpen() {
        policy.lockEnabled = false;
        assertTrue(session.isUnlocked());
        clock.advance(Duration.ofHours(10));
        assertTrue(session.isUnlocked());

        // Switching it back on locks the vault until the next unlock.
        policy.lockEnabled = true;
        assertFalse(session.isUnlocked());
    }
}
