package com.safarsaathi.app.testing;

import android.Manifest;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * On Android 17+ a debug build of MainActivity asks once: "allow access to devices on your local network?"
 * (so it can reach the backend on the laptop). In a UI test that question would cover the screen, so the
 * tests answer "allow" in advance, before they open MainActivity.
 */
public final class LocalNetworkAccess {

    private LocalNetworkAccess() {
    }

    /** Gives the app the permission, as if the user had tapped "Allow". Does nothing on Android 16 and older. */
    public static void allowForTests() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
            return;
        }
        String app = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(app, Manifest.permission.ACCESS_LOCAL_NETWORK);
    }
}
