package com.safarsaathi.backend;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test helper: like {@link FixedClockConfig}, but the test can move the clock on with {@link MovableClock#advance}.
 * Starts at {@link #START} (10 Oct 2026, 17:30:05 in India), so the demo trip day is 11 Oct 2026.
 * Use it with {@code @Import(MovableClockConfig.class)}; call {@code clock.set(START)} in {@code @BeforeEach}.
 *
 * <p>Needed to show that a demo reset gives the trips a NEW createdAt: with a stopped clock, "now" never changes.
 */
@TestConfiguration
public class MovableClockConfig {

    public static final Instant START = FixedClockConfig.NOW;

    @Bean
    @Primary
    public MovableClock movableClock() {
        return new MovableClock(START, ZoneId.of("Asia/Kolkata"));
    }

    /** A clock that stands still until the test moves it. */
    public static class MovableClock extends Clock {

        private final ZoneId zone;
        private volatile Instant now; // volatile: the app may read it from another thread (e.g. the watcher's timer)

        public MovableClock(Instant start, ZoneId zone) {
            this.now = start;
            this.zone = zone;
        }

        public void set(Instant moment) {
            now = moment;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        /** The same moment seen from another zone (a snapshot; fine for the one-off uses in our code). */
        @Override
        public Clock withZone(ZoneId otherZone) {
            return Clock.fixed(now, otherZone);
        }
    }
}
