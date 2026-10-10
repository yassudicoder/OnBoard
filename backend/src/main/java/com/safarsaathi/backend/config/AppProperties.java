package com.safarsaathi.backend.config;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Our own settings: everything under {@code safarsaathi:} in application.yml.
 *
 * <p>Spring Boot creates this object at startup and fills it from application.yml, which in turn reads the
 * environment variables (or the git-ignored .env file). The names match like this:
 * {@code safarsaathi.providers.gemini.api-key} in YAML = {@code providers().gemini().apiKey()} in Java.
 *
 * <p>Why the prefix "safarsaathi" and not just "app"? Spring Boot also copies an environment variable whose name
 * matches a setting straight into that setting ("relaxed binding": APP_ZONE would fill app.zone), skipping
 * application.yml. A stray APP_ZONE variable could then change our fixed time zone, and a real APP_MODE variable
 * would beat the APP_MODE value a test sets. Nobody has variables named SAFARSAATHI_..., so with this prefix the
 * environment variables reach our settings only through the ${...} lines in application.yml.
 *
 * <p>Java records are used because settings never change while the app runs. Any class can ask Spring for an
 * {@code AppProperties} in its constructor. An empty {@code @DefaultValue} on a group means "create it even
 * if application.yml says nothing about it", so groups are never {@code null}.
 *
 * @param mode      APP_MODE: "demo" (default) or "prod". Demo mode will check flights every 30 s instead of 5 min.
 * @param zone      the one time zone used everywhere (Asia/Kolkata); fixed in application.yml, not an env var.
 * @param providers API keys and credential files for the outside services. All optional: blank = MOCK.
 * @param seed      loading the demo trips from mock-data/demo-trips.json into an empty database.
 */
@ConfigurationProperties(prefix = "safarsaathi")
public record AppProperties(String mode, String zone, @DefaultValue Providers providers, @DefaultValue Seed seed) {

    public static final String DEFAULT_MODE = "demo";
    public static final String DEFAULT_ZONE = "Asia/Kolkata";

    /** Runs once, when the record is created: fills in defaults and rejects a mistyped APP_MODE early. */
    public AppProperties {
        // Blank counts as "not set": copying .env.example to .env gives lines like "APP_MODE=" with no value.
        mode = normaliseMode(mode);
        zone = isBlank(zone) ? DEFAULT_ZONE : zone.strip();
        if (!mode.equals("demo") && !mode.equals("prod")) {
            throw new IllegalArgumentException("APP_MODE must be 'demo' or 'prod', but it is '" + mode + "'");
        }
    }

    /**
     * APP_MODE as we compare it: blank means the default ("demo"), and spaces and capitals are ignored ("Prod " is
     * "prod"). Public so {@link com.safarsaathi.backend.demo.DemoModeOnly} reads the mode exactly the same way.
     */
    public static String normaliseMode(String mode) {
        return isBlank(mode) ? DEFAULT_MODE : mode.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * True in demo mode only. The demo controls (POST /api/demo/trigger-delay, trigger-cancel, reset) exist only
     * then; in prod mode nobody can delay, cancel or reset trips through them.
     */
    public boolean demoControlsEnabled() {
        return DEFAULT_MODE.equals(mode);
    }

    /** One entry per outside service. Each comment names the environment variable(s) that fill it. */
    public record Providers(
            @DefaultValue Gemini gemini,                  // GEMINI_API_KEY, GEMINI_MODEL - AI (Google Gemini)
            @DefaultValue ApiKey aerodatabox,             // AERODATABOX_API_KEY - flight status (AeroDataBox on RapidAPI)
            @DefaultValue ApiKey googleRoutes,            // GOOGLE_ROUTES_API_KEY - door-to-door directions
            @DefaultValue CredentialsFile firebase,       // FIREBASE_CREDENTIALS_PATH - push notifications (Firebase FCM)
            @DefaultValue CredentialsFile googleCalendar, // GOOGLE_CALENDAR_CREDENTIALS_PATH - moving meetings
            @DefaultValue ApiKey sarvam) {                // SARVAM_API_KEY - Indian-language voice (Sarvam AI)
    }

    /**
     * Google Gemini settings.
     *
     * @param apiKey GEMINI_API_KEY (blank = MOCK)
     * @param model  GEMINI_MODEL; blank means the default model, see {@link ProviderStatus#geminiModel()}
     */
    public record Gemini(String apiKey, String model) {
        public Gemini {
            apiKey = strip(apiKey);
            model = strip(model);
        }

        @Override
        public String toString() { // never print the key itself, e.g. if someone logs this object
            return "Gemini[apiKey=" + (isBlank(apiKey) ? "not set" : "set") + ", model=" + model + "]";
        }
    }

    /** A service that only needs an API key (blank = MOCK). */
    public record ApiKey(String apiKey) {
        public ApiKey {
            apiKey = strip(apiKey);
        }

        @Override
        public String toString() { // never print the key itself
            return "ApiKey[apiKey=" + (isBlank(apiKey) ? "not set" : "set") + "]";
        }
    }

    /** A service that needs a JSON credentials file on disk; this is its path (blank or missing file = MOCK). */
    public record CredentialsFile(String credentialsPath) {
        public CredentialsFile {
            credentialsPath = strip(credentialsPath);
        }
    }

    /**
     * The demo trips (safarsaathi.seed.* in application.yml), read by
     * {@link com.safarsaathi.backend.demo.DemoTripSeeder}.
     *
     * @param enabled   false = never save the demo trips. Default true.
     * @param dayOffset the demo trips happen on "today in India + this many days". Default 1 = tomorrow.
     */
    public record Seed(@DefaultValue("true") boolean enabled, @DefaultValue("1") int dayOffset) {
    }

    /** True for null, "" and text that is only spaces. */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Removes spaces at both ends of a value. A stray space after a key in .env is an easy copy-paste slip,
     * and the real service would reject the key with it. null stays null.
     */
    static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
