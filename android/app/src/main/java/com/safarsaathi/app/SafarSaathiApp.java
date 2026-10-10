package com.safarsaathi.app;

import android.app.Application;
import android.util.Log;

/**
 * Runs once when the app process starts, before any screen. Registered in AndroidManifest.xml
 * ({@code android:name=".SafarSaathiApp"}).
 */
public class SafarSaathiApp extends Application {

    /** Log tag. In Android Studio's Logcat, filter on "SafarSaathi" to see our messages. */
    private static final String TAG = "SafarSaathi";

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceLocator.init(this);

        // Say once, at startup, which features run for real and which use mock data
        // (same idea as the backend's MOCK/REAL log). Never log key values themselves.
        AppConfig config = ServiceLocator.get().getAppConfig();
        Log.i(TAG, "Maps: " + config.mapsMode() + (config.hasMapsKey() ? "" : " (no MAPS_API_KEY)"));
        Log.i(TAG, "Backend: " + config.getBackendBaseUrl());
    }
}
