package com.safarsaathi.app.testing;

import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;

import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;

/**
 * Opens any screen of the navigation graph directly, for UI tests of screens that are not bottom-bar tabs: the
 * Wallet (opened from Profile) and the PARKED trip-timeline screens, which the app only links to when
 * feature.tripTimeline=true. Tests open them this way, so they run whatever the flag says.
 */
public final class Screens {

    private Screens() {
    }

    /** Navigates to {@code destinationId} (e.g. R.id.navigation_trip) on the activity's main thread. */
    public static void open(ActivityScenario<MainActivity> scenario, int destinationId) {
        scenario.onActivity(activity ->
                Navigation.findNavController(activity, R.id.nav_host_fragment).navigate(destinationId));
    }
}
