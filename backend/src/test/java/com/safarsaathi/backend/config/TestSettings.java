package com.safarsaathi.backend.config;

import java.util.Map;

/**
 * Test helper: builds {@link AppProperties} from pretend environment variables, without Spring.
 * Because the test passes every value itself, the result never depends on a developer's .env file
 * or real environment variables.
 */
final class TestSettings {

    private TestSettings() {
    }

    /** Variables that are not in the map count as "not set". */
    static AppProperties fromEnv(Map<String, String> env) {
        return new AppProperties(env.get("APP_MODE"), null, new AppProperties.Providers(
                new AppProperties.Gemini(env.get("GEMINI_API_KEY"), env.get("GEMINI_MODEL")),
                new AppProperties.ApiKey(env.get("AERODATABOX_API_KEY")),
                new AppProperties.ApiKey(env.get("GOOGLE_ROUTES_API_KEY")),
                new AppProperties.CredentialsFile(env.get("FIREBASE_CREDENTIALS_PATH")),
                new AppProperties.CredentialsFile(env.get("GOOGLE_CALENDAR_CREDENTIALS_PATH")),
                new AppProperties.ApiKey(env.get("SARVAM_API_KEY"))),
                new AppProperties.Seed(true, 1)); // the defaults from application.yml
    }
}
