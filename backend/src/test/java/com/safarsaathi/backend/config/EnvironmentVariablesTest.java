package com.safarsaathi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.safarsaathi.backend.config.ProviderStatus.Mode;

/**
 * Checks the wiring in application.yml: each environment variable name (GEMINI_API_KEY, ...) must reach the
 * right setting. A typo there would silently leave a service on MOCK, so it is worth a test.
 * The values below act like environment variables but beat real ones and .env, so the result is the same
 * on every laptop. test-database.properties gives the app an in-memory test database (no Docker needed).
 * (Not no-keys.properties: its blank keys would beat the pretend variables below.)
 */
@TestPropertySource(locations = "classpath:test-database.properties")
@SpringBootTest(properties = {
        "APP_MODE=prod",
        "GEMINI_API_KEY=test-gemini-key",
        "GEMINI_MODEL=gemini-test-model",
        "AERODATABOX_API_KEY=test-aerodatabox-key",
        "GOOGLE_ROUTES_API_KEY=test-routes-key",
        "SARVAM_API_KEY=test-sarvam-key",
        "FIREBASE_CREDENTIALS_PATH=no-such-firebase-file.json",
        "GOOGLE_CALENDAR_CREDENTIALS_PATH=no-such-calendar-file.json"
})
class EnvironmentVariablesTest {

    @Autowired
    private AppProperties properties;

    @Autowired
    private ProviderStatus status;

    @Test
    void eachVariableReachesItsOwnSetting() {
        assertThat(properties.mode()).isEqualTo("prod");
        assertThat(status.provider("flightStatus").mode()).isEqualTo(Mode.REAL);
        assertThat(status.provider("ai").mode()).isEqualTo(Mode.REAL);
        assertThat(status.provider("directions").mode()).isEqualTo(Mode.REAL);
        assertThat(status.provider("voice").mode()).isEqualTo(Mode.REAL);
        assertThat(status.geminiModel()).isEqualTo("gemini-test-model");
        // These files do not exist, so push and calendar stay MOCK, and the reason shows which path was read.
        assertThat(status.provider("push").reason()).endsWith("no-such-firebase-file.json");
        assertThat(status.provider("calendar").reason()).endsWith("no-such-calendar-file.json");
    }
}
