package com.safarsaathi.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for {@link AppConfig}. They run on the laptop, no emulator needed:
 * {@code ./gradlew testDebugUnitTest} (Windows: {@code .\gradlew.bat testDebugUnitTest}).
 */
public class AppConfigTest {

    @Test
    public void backendUrl_withoutTrailingSlash_getsSlashAdded() {
        AppConfig config = new AppConfig("http://192.168.1.23:8080", false);

        assertEquals("http://192.168.1.23:8080/", config.getBackendBaseUrl());
    }

    @Test
    public void backendUrl_withTrailingSlash_isKeptAsItIs() {
        AppConfig config = new AppConfig("http://10.0.2.2:8080/", false);

        assertEquals("http://10.0.2.2:8080/", config.getBackendBaseUrl());
    }

    @Test
    public void backendUrl_surroundingSpaces_areRemoved() {
        AppConfig config = new AppConfig("  https://safar-saathi.example.com/api  ", false);

        assertEquals("https://safar-saathi.example.com/api/", config.getBackendBaseUrl());
    }

    @Test
    public void backendUrl_blank_fallsBackToEmulatorDefault() {
        assertEquals("http://10.0.2.2:8080/", AppConfig.DEFAULT_BACKEND_BASE_URL);
        for (String blank : new String[] {null, "", "   ", "\t"}) {
            AppConfig config = new AppConfig(blank, false);
            assertEquals("for [" + blank + "]", AppConfig.DEFAULT_BACKEND_BASE_URL, config.getBackendBaseUrl());
        }
    }

    // Whether MAPS_API_KEY counts as "set" is decided in app/build.gradle.kts (it writes
    // BuildConfig.HAS_MAPS_KEY). These two tests check what the app makes of that yes/no answer.

    @Test
    public void mapsMode_withoutKey_isMock() {
        assertEquals("MOCK", new AppConfig("", false).mapsMode());
    }

    @Test
    public void mapsMode_withKey_isReal() {
        assertEquals("REAL", new AppConfig("", true).mapsMode());
    }
}
