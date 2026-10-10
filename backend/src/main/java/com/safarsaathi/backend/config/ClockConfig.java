package com.safarsaathi.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the one {@link Clock} that the whole backend uses to ask "what time is it now?".
 *
 * <p>Why not just call {@code ZonedDateTime.now()} everywhere? Because code that takes the time from this
 * bean, e.g. {@code OffsetDateTime.now(clock)}, can later be given a fixed or fast-forwarded clock
 * ({@code Clock.fixed(...)}, {@code Clock.offset(...)}) in tests and demos without changing any business code.
 */
@Configuration
public class ClockConfig {

    /** The real system clock, in our time zone (safarsaathi.zone = Asia/Kolkata, i.e. Indian Standard Time, UTC+05:30). */
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.zone()));
    }
}
