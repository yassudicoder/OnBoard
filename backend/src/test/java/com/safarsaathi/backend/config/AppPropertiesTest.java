package com.safarsaathi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** The small rules inside {@link AppProperties}: defaults for blank values, APP_MODE check, hidden keys. */
class AppPropertiesTest {

    @Test
    void blankModeAndZoneFallBackToTheDefaults() {
        // Like a line "APP_MODE=" copied from .env.example without a value.
        AppProperties settings = TestSettings.fromEnv(Map.of("APP_MODE", " "));

        assertThat(settings.mode()).isEqualTo("demo");
        assertThat(settings.zone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void modeIgnoresUpperCase() {
        assertThat(TestSettings.fromEnv(Map.of("APP_MODE", "PROD")).mode()).isEqualTo("prod");
    }

    @Test
    void mistypedModeStopsTheAppWithAClearMessage() {
        assertThatThrownBy(() -> TestSettings.fromEnv(Map.of("APP_MODE", "production")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("APP_MODE must be 'demo' or 'prod', but it is 'production'");
    }

    @Test
    void spacesAroundValuesAreRemoved() {
        // e.g. "GEMINI_API_KEY=abc123 " with a stray space at the end of the line in .env
        AppProperties settings = TestSettings.fromEnv(Map.of(
                "GEMINI_API_KEY", " abc123 ",
                "GEMINI_MODEL", "gemini-test-model ",
                "FIREBASE_CREDENTIALS_PATH", " C:/keys/firebase.json "));

        assertThat(settings.providers().gemini().apiKey()).isEqualTo("abc123");
        assertThat(settings.providers().gemini().model()).isEqualTo("gemini-test-model");
        assertThat(settings.providers().firebase().credentialsPath()).isEqualTo("C:/keys/firebase.json");
    }

    @Test
    void printingTheSettingsNeverShowsTheKeys() {
        AppProperties settings = TestSettings.fromEnv(Map.of(
                "GEMINI_API_KEY", "secret-gemini-111",
                "AERODATABOX_API_KEY", "secret-aerodatabox-222",
                "GOOGLE_ROUTES_API_KEY", "secret-routes-333",
                "SARVAM_API_KEY", "secret-sarvam-444"));

        assertThat(settings.toString()).contains("apiKey=set").doesNotContain("secret");
    }
}
