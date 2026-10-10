package com.safarsaathi.backend.health;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.safarsaathi.backend.config.AppProperties;
import com.safarsaathi.backend.config.ProviderStatus;

/**
 * GET /api/health: a quick "is the backend up, and which services are MOCK or REAL?" check.
 *
 * <p>Example answer:
 * <pre>
 * {"status":"UP","app":"onboard-backend","mode":"demo","time":"2026-10-10T14:05:00+05:30",
 *  "zone":"Asia/Kolkata","providers":{"flightStatus":"MOCK","ai":"MOCK", ... ,"weather":"REAL"}}
 * </pre>
 * Spring's own check, /actuator/health, also exists; this one adds the app-specific details.
 */
@RestController
public class HealthController {

    private final Clock clock;
    private final ProviderStatus providerStatus;
    private final AppProperties properties;
    private final String appName;

    // Spring calls this constructor and passes in the beans it needs.
    // @Value copies a single setting from application.yml (here: spring.application.name).
    public HealthController(Clock clock, ProviderStatus providerStatus, AppProperties properties,
                            @Value("${spring.application.name}") String appName) {
        this.clock = clock;
        this.providerStatus = providerStatus;
        this.properties = properties;
        this.appName = appName;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        // "now" comes from the shared Clock (Asia/Kolkata), cut to whole seconds so it is easy to read.
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return new HealthResponse("UP", appName, properties.mode(), now, clock.getZone().getId(),
                providerStatus.modes());
    }

    /**
     * The JSON body. Spring (through the Jackson library) turns each record field into a JSON key, in this order.
     * The time is written as ISO-8601 text with its offset, e.g. "2026-10-10T14:05:00+05:30".
     */
    public record HealthResponse(String status, String app, String mode, OffsetDateTime time, String zone,
                                 Map<String, ProviderStatus.Mode> providers) {
    }
}
