package com.safarsaathi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Checks the text of the startup block, without starting Spring. */
class ProviderStatusLoggerTest {

    @Test
    void reportShowsModeZoneEveryProviderAndTheModel() {
        String report = reportFor(Map.of());

        assertThat(report).contains(
                "App mode: demo", "time zone: Asia/Kolkata",
                "AERODATABOX_API_KEY not set", "GEMINI_API_KEY not set", "GOOGLE_ROUTES_API_KEY not set",
                "FIREBASE_CREDENTIALS_PATH not set", "GOOGLE_CALENDAR_CREDENTIALS_PATH not set",
                "SARVAM_API_KEY not set", "no key needed",
                "Gemini model: gemini-3.8-flash",
                "All MOCK is fine",
                "http://localhost:8080/api/health");
    }

    @Test
    void reportNeverShowsKeyValues() {
        String report = reportFor(Map.of(
                "GEMINI_API_KEY", "secret-gemini-111",
                "AERODATABOX_API_KEY", "secret-aerodatabox-222",
                "GOOGLE_ROUTES_API_KEY", "secret-routes-333",
                "SARVAM_API_KEY", "secret-sarvam-444"));

        assertThat(report).contains("GEMINI_API_KEY is set", "SARVAM_API_KEY is set").doesNotContain("secret");
    }

    @Test
    void reportNeverShowsTextPastedIntoACredentialsPath() {
        // An easy slip: pasting the JSON file's contents, or an API key, where the PATH to the file belongs.
        String report = reportFor(Map.of(
                "FIREBASE_CREDENTIALS_PATH", "{\"type\":\"service_account\",\"private_key\":\"secret-firebase-555\"}",
                "GOOGLE_CALENDAR_CREDENTIALS_PATH", "secret-calendar-666"));

        assertThat(report).contains("(value hidden)").doesNotContain("secret", "private_key");
    }

    @Test
    void reportSaysTheBackendIsLocalOnlyAndTheDemoControlsAreOnInDemoMode() {
        String report = reportFor(Map.of());

        assertThat(report).contains("Listening on 127.0.0.1: this computer only",
                "Demo controls (POST /api/demo/...): ON").doesNotContain("WARNING");
    }

    @Test
    void reportWarnsWhenDemoModeListensOnEveryNetwork() {
        String report = reportFor(Map.of(), "0.0.0.0");

        assertThat(report).contains("Listening on 0.0.0.0: other devices", "Demo controls (POST /api/demo/...): ON",
                "WARNING: demo mode on an address other computers can reach");
    }

    @Test
    void reportSaysTheDemoControlsAreOffInProdMode() {
        String report = reportFor(Map.of("APP_MODE", "prod"), "0.0.0.0");

        assertThat(report).contains("App mode: prod", "Demo controls (POST /api/demo/...): OFF")
                .doesNotContain("WARNING");
    }

    private static String reportFor(Map<String, String> env) {
        return reportFor(env, "127.0.0.1");
    }

    private static String reportFor(Map<String, String> env, String address) {
        AppProperties settings = TestSettings.fromEnv(env);
        return new ProviderStatusLogger(settings, new ProviderStatus(settings)).buildReport(address, "8080");
    }
}
