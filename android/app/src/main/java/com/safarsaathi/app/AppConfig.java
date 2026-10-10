package com.safarsaathi.app;

import java.time.ZoneId;

/**
 * App settings that are fixed when the app is built (they come from android/local.properties
 * through BuildConfig, see app/build.gradle.kts).
 *
 * <p>This class is plain Java on purpose: no Android imports. That way the unit tests in
 * src/test can check it on the laptop in milliseconds, without an emulator.
 */
public final class AppConfig {

    /** Used when BACKEND_BASE_URL is blank. 10.0.2.2 is how the Android emulator reaches the laptop. */
    public static final String DEFAULT_BACKEND_BASE_URL = "http://10.0.2.2:8080/";

    /** Values returned by {@link #mapsMode()}: real Google Maps, or our mock/offline maps. */
    public static final String MODE_REAL = "REAL";
    public static final String MODE_MOCK = "MOCK";

    /**
     * The trip the app shows: demo trip 1 ("Client visit: Mumbai to Bengaluru", see mock-data/demo-trips.json).
     * Until trips can be created in the app, this is "the current trip".
     */
    public static final long DEMO_TRIP_ID = 1;

    /** Every time in the app is shown in Indian time, whatever the phone's own time zone is (CLAUDE.md). */
    public static final ZoneId TRIP_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * While the Trip tab is on screen it asks the backend for news this often, so a delay triggered for the
     * demo shows up within a few seconds. (Push messages replace most of this polling in Phase 3.)
     */
    public static final long AUTO_REFRESH_INTERVAL_MS = 10_000;

    private final String backendBaseUrl;
    private final boolean hasMapsKey;

    /**
     * @param backendBaseUrl where the backend runs, e.g. "http://10.0.2.2:8080/" (blank = default)
     * @param hasMapsKey     true when MAPS_API_KEY is set in local.properties (BuildConfig.HAS_MAPS_KEY;
     *                       app/build.gradle.kts decides what counts as "set")
     */
    public AppConfig(String backendBaseUrl, boolean hasMapsKey) {
        this.backendBaseUrl = normaliseBaseUrl(backendBaseUrl);
        this.hasMapsKey = hasMapsKey;
    }

    /**
     * Cleans up a base URL: blank becomes the default, and a missing "/" at the end is added
     * (Retrofit refuses base URLs that do not end with "/").
     */
    static String normaliseBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_BACKEND_BASE_URL;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    /** The backend address, always ending with "/". */
    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public boolean hasMapsKey() {
        return hasMapsKey;
    }

    /** "REAL" when a Maps key is configured, otherwise "MOCK" (the demo still works, with offline maps). */
    public String mapsMode() {
        return hasMapsKey ? MODE_REAL : MODE_MOCK;
    }
}
