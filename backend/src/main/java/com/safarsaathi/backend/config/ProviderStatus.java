package com.safarsaathi.backend.config;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Works out, once at startup, whether each outside service runs as MOCK (fake data, no key needed)
 * or REAL (calls the actual service), and why.
 *
 * <p>The rule: a provider is MOCK when its API key is blank, or when its credentials path is blank or the
 * file does not exist; otherwise it is REAL. Weather (Open-Meteo) needs no key, so it is always REAL.
 *
 * <p>It is a plain Java class: Spring creates it at startup (because of {@code @Component}), and a unit test
 * can simply call {@code new ProviderStatus(properties)}. It never stores or prints key values.
 */
@Component
public class ProviderStatus {

    /** MOCK = built-in fake data, needs no key. REAL = calls the actual outside service. */
    public enum Mode { MOCK, REAL }

    /**
     * One row of the status table.
     *
     * @param key     the name used in GET /api/health, e.g. "ai"
     * @param service the outside service behind it, e.g. "Gemini"
     * @param mode    MOCK or REAL
     * @param reason  a short explanation for humans, e.g. "GEMINI_API_KEY not set"
     */
    public record Provider(String key, String service, Mode mode, String reason) {
    }

    /** Used when GEMINI_MODEL is blank. Same default as in application.yml. */
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.8-flash";

    /**
     * A credentials path is repeated in the startup log only if it is shorter than this (the classic Windows
     * path limit). Anything longer is more likely pasted text than a path.
     */
    private static final int MAX_SHOWN_PATH_LENGTH = 260;

    private final List<Provider> providers;
    private final String geminiModel;

    public ProviderStatus(AppProperties properties) {
        AppProperties.Providers keys = properties.providers();
        // This order is also the order in the /api/health JSON and in the startup log.
        this.providers = List.of(
                fromApiKey("flightStatus", "AeroDataBox", "AERODATABOX_API_KEY", keys.aerodatabox().apiKey()),
                fromApiKey("ai", "Gemini", "GEMINI_API_KEY", keys.gemini().apiKey()),
                fromApiKey("directions", "Google Routes", "GOOGLE_ROUTES_API_KEY", keys.googleRoutes().apiKey()),
                fromCredentialsFile("push", "Firebase FCM", "FIREBASE_CREDENTIALS_PATH",
                        keys.firebase().credentialsPath()),
                fromCredentialsFile("calendar", "Google Calendar", "GOOGLE_CALENDAR_CREDENTIALS_PATH",
                        keys.googleCalendar().credentialsPath()),
                fromApiKey("voice", "Sarvam AI", "SARVAM_API_KEY", keys.sarvam().apiKey()),
                new Provider("weather", "Open-Meteo", Mode.REAL, "no key needed"));

        String model = keys.gemini().model();
        this.geminiModel = AppProperties.isBlank(model) ? DEFAULT_GEMINI_MODEL : model;
    }

    /** All providers, in display order. */
    public List<Provider> providers() {
        return providers;
    }

    /** One provider by its key, e.g. {@code provider("ai")}. */
    public Provider provider(String key) {
        for (Provider provider : providers) {
            if (provider.key().equals(key)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown provider: " + key);
    }

    /** Just key -> MOCK/REAL, in display order: the "providers" object of GET /api/health. */
    public Map<String, Mode> modes() {
        Map<String, Mode> modes = new LinkedHashMap<>(); // LinkedHashMap keeps the insertion order
        for (Provider provider : providers) {
            modes.put(provider.key(), provider.mode());
        }
        return modes;
    }

    /** The Gemini model to use: GEMINI_MODEL, or {@value #DEFAULT_GEMINI_MODEL} when that is blank. */
    public String geminiModel() {
        return geminiModel;
    }

    /** API-key services: REAL as soon as a key is set. Only says whether it is set, never what it is. */
    private static Provider fromApiKey(String key, String service, String envVar, String apiKey) {
        if (AppProperties.isBlank(apiKey)) {
            return new Provider(key, service, Mode.MOCK, envVar + " not set");
        }
        return new Provider(key, service, Mode.REAL, envVar + " is set");
    }

    /**
     * Credentials-file services: REAL only if the path points to a file that really exists.
     *
     * <p>The reason repeats the path only when it looks like a path to a .json file. An easy slip is to paste the
     * file's contents (which include a private key) or an API key on this line instead of a path, and the
     * startup log must never show that text.
     */
    private static Provider fromCredentialsFile(String key, String service, String envVar, String credentialsPath) {
        if (AppProperties.isBlank(credentialsPath)) {
            return new Provider(key, service, Mode.MOCK, envVar + " not set");
        }
        boolean looksLikeJsonPath = credentialsPath.toLowerCase(Locale.ROOT).endsWith(".json")
                && credentialsPath.length() < MAX_SHOWN_PATH_LENGTH;
        Path file;
        try {
            // A relative path is resolved from the folder the app was started in, so show the full path.
            file = Path.of(credentialsPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) { // e.g. characters that Windows does not allow in a path, like " in JSON
            return new Provider(key, service, Mode.MOCK, envVar + " is not a valid file path (value hidden)");
        }
        if (!Files.isRegularFile(file)) {
            String reason = looksLikeJsonPath
                    ? "file not found: " + file
                    : envVar + " does not point to a .json file (value hidden)";
            return new Provider(key, service, Mode.MOCK, reason);
        }
        return new Provider(key, service, Mode.REAL, "file found: " + file);
    }
}
