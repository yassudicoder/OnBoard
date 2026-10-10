package com.safarsaathi.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test helper: stops the clock at {@link #NOW} (10 October 2026, 17:30:05 in India) for a Spring test.
 * Use it with {@code @Import(FixedClockConfig.class)}.
 *
 * <p>The demo trips get their dates from the clock (trip day = today + 1), so with a stopped clock every test run
 * gets the same dates: the trip day is 11 October 2026, and the trip-1 flight leaves at 2026-10-11T08:00:00+05:30.
 *
 * <p>{@code @Primary} = when a class asks Spring for a {@link Clock}, it gets this one instead of the real clock
 * from ClockConfig.
 */
@TestConfiguration
public class FixedClockConfig {

    public static final Instant NOW = OffsetDateTime.parse("2026-10-10T17:30:05+05:30").toInstant();

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));
    }
}
