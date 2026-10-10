package com.safarsaathi.app.data.ride;

import android.content.Context;

/**
 * A ride app we can hand a ride to (CLAUDE.md: every outside service sits behind an interface with a mock).
 *
 * <p>We only ever OPEN the ride app with the places pre-filled. Booking, price, driver and arrival time stay inside
 * that app and never come back to us, so the screen can only say "Opened in Uber", never "booked".
 *
 * <p>Built: {@link UberDeepLinkProvider} (real, no key needed) and {@link MockRideProvider} (demo, shows what would
 * open). Room for others later, but only with a link format checked in that company's official docs.
 */
public interface RideProvider {

    /** What happened when the ride was handed over. */
    enum Opened {
        /** The ride app itself opened. */
        APP,
        /** The ride app is not installed, so its website opened in the browser. */
        BROWSER,
        /** Demo provider: nothing was opened, the screen showed what would open. */
        DEMO,
        /** Neither the app nor a browser could open the link. */
        NOTHING
    }

    /** For logs and the Profile tab, e.g. "Uber". */
    String name();

    /** True for the demo provider (logged at start-up as MOCK, like the backend's providers). */
    boolean isMock();

    /** The link the ride app would get. Throws IllegalArgumentException when the request has no drop-off. */
    String link(RideRequest request);

    /** Opens the ride app (or shows the demo dialog). {@code context} must be a screen (Activity) context. */
    Opened open(Context context, RideRequest request);
}
