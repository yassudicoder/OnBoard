package com.safarsaathi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.safarsaathi.backend.config.ProviderStatus.Mode;
import com.safarsaathi.backend.config.ProviderStatus.Provider;

/**
 * Plain unit tests for the MOCK/REAL rules (no Spring). Each test passes its own pretend environment
 * variables, so nothing here depends on a developer's .env file.
 *
 * <p>{@code @ParameterizedTest} + {@code @CsvSource} runs the same test once per line of the table.
 */
class ProviderStatusTest {

    /** JUnit creates an empty temporary folder for each test and deletes it afterwards. */
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @CsvSource({
            "flightStatus, AERODATABOX_API_KEY",
            "ai,           GEMINI_API_KEY",
            "directions,   GOOGLE_ROUTES_API_KEY",
            "push,         FIREBASE_CREDENTIALS_PATH",
            "calendar,     GOOGLE_CALENDAR_CREDENTIALS_PATH",
            "voice,        SARVAM_API_KEY"
    })
    void blankSettingMeansMockAndNamesTheMissingVariable(String provider, String envVar) {
        ProviderStatus status = statusFor(Map.of()); // nothing set at all

        assertThat(status.provider(provider).mode()).isEqualTo(Mode.MOCK);
        assertThat(status.provider(provider).reason()).isEqualTo(envVar + " not set");
    }

    @Test
    void keyMadeOfSpacesCountsAsBlank() {
        ProviderStatus status = statusFor(Map.of("GEMINI_API_KEY", "   "));

        assertThat(status.provider("ai").mode()).isEqualTo(Mode.MOCK);
    }

    @ParameterizedTest
    @CsvSource({
            "flightStatus, AERODATABOX_API_KEY",
            "ai,           GEMINI_API_KEY",
            "directions,   GOOGLE_ROUTES_API_KEY",
            "voice,        SARVAM_API_KEY"
    })
    void keySetMeansRealForThatProviderOnly(String provider, String envVar) {
        ProviderStatus status = statusFor(Map.of(envVar, "secret-test-key-123"));

        assertThat(status.provider(provider).mode()).isEqualTo(Mode.REAL);
        assertThat(status.provider(provider).reason()).isEqualTo(envVar + " is set"); // never the key itself
        assertThat(realProviders(status)).containsExactlyInAnyOrder(provider, "weather");
    }

    @ParameterizedTest
    @CsvSource({
            "push,     FIREBASE_CREDENTIALS_PATH",
            "calendar, GOOGLE_CALENDAR_CREDENTIALS_PATH"
    })
    void credentialsPathToMissingFileMeansMock(String provider, String envVar) {
        Path missing = tempDir.resolve("does-not-exist.json");

        ProviderStatus status = statusFor(Map.of(envVar, missing.toString()));

        assertThat(status.provider(provider).mode()).isEqualTo(Mode.MOCK);
        assertThat(status.provider(provider).reason()).isEqualTo("file not found: " + missing);
    }

    @ParameterizedTest
    @CsvSource({
            "push,     FIREBASE_CREDENTIALS_PATH",
            "calendar, GOOGLE_CALENDAR_CREDENTIALS_PATH"
    })
    void credentialsPathToExistingFileMeansReal(String provider, String envVar) throws IOException {
        Path file = Files.writeString(tempDir.resolve("credentials.json"), "{}");

        ProviderStatus status = statusFor(Map.of(envVar, file.toString()));

        assertThat(status.provider(provider).mode()).isEqualTo(Mode.REAL);
        assertThat(realProviders(status)).containsExactlyInAnyOrder(provider, "weather");
    }

    @Test
    void credentialsPathToAFolderMeansMock() {
        ProviderStatus status = statusFor(Map.of("FIREBASE_CREDENTIALS_PATH", tempDir.toString()));

        assertThat(status.provider("push").mode()).isEqualTo(Mode.MOCK);
    }

    @Test
    void weatherNeedsNoKeyAndIsAlwaysReal() {
        Provider weather = statusFor(Map.of()).provider("weather");

        assertThat(weather.mode()).isEqualTo(Mode.REAL);
        assertThat(weather.reason()).isEqualTo("no key needed");
    }

    @Test
    void blankGeminiModelFallsBackToTheDefault() {
        assertThat(statusFor(Map.of()).geminiModel()).isEqualTo("gemini-3.8-flash");
        assertThat(statusFor(Map.of("GEMINI_MODEL", "  ")).geminiModel()).isEqualTo("gemini-3.8-flash");
    }

    @Test
    void geminiModelCanBeChosen() {
        assertThat(statusFor(Map.of("GEMINI_MODEL", "gemini-test-model")).geminiModel())
                .isEqualTo("gemini-test-model");
    }

    @Test
    void providersComeInTheSameOrderAsInTheHealthJson() {
        assertThat(statusFor(Map.of()).modes().keySet())
                .containsExactly("flightStatus", "ai", "directions", "push", "calendar", "voice", "weather");
    }

    private static ProviderStatus statusFor(Map<String, String> env) {
        return new ProviderStatus(TestSettings.fromEnv(env));
    }

    private static List<String> realProviders(ProviderStatus status) {
        return status.providers().stream()
                .filter(provider -> provider.mode() == Mode.REAL)
                .map(provider -> provider.key())
                .toList();
    }
}
