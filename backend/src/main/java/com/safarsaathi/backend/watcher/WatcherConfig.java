package com.safarsaathi.backend.watcher;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.safarsaathi.backend.config.AppProperties;

/**
 * Turns on Spring's timer for {@code @Scheduled} methods ({@code @EnableScheduling}) and decides how often the
 * {@link FlightWatcher} runs.
 */
@Configuration
@EnableScheduling
public class WatcherConfig {

    /**
     * How often the flight watcher checks: every 30 seconds in demo mode (APP_MODE=demo, the default), so a live
     * demo reacts quickly, and every 5 minutes in prod mode, to spare the real flight-status service.
     *
     * <p>{@link FlightWatcher}'s {@code @Scheduled} line reads this bean by its name, "flightCheckInterval".
     */
    @Bean
    public Duration flightCheckInterval(AppProperties properties) {
        return "prod".equals(properties.mode()) ? Duration.ofMinutes(5) : Duration.ofSeconds(30);
    }
}
