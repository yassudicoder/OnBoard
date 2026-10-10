package com.safarsaathi.app.data.ride;

import androidx.annotation.Nullable;

/**
 * What a "Get a ride" button should say about a ride before it is tapped. Plain Java (the button turns it into
 * text), checked by RideCheckTest.
 *
 * <ul>
 *   <li>No drop-off: the button is disabled, and the screen says why.</li>
 *   <li>An unverified airport point: "Approximate pickup point" / "Approximate drop-off point".</li>
 *   <li>A drop-off without coordinates (typed address): "Check the drop-off in Uber before you book".</li>
 *   <li>Offline: the button still works (the link is built on the phone), but Uber needs internet to book.</li>
 * </ul>
 */
public final class RideCheck {

    public enum Problem {
        /** No drop-off known yet (e.g. no hotel in the trip and nothing typed). */
        NO_DROPOFF
    }

    /** Why the button is disabled; null = it can be tapped. */
    @Nullable
    public final Problem problem;
    public final boolean approximatePickup;
    public final boolean approximateDropoff;
    /** The drop-off has no coordinates, so the traveller should check it inside Uber. */
    public final boolean checkDropoffInRideApp;
    public final boolean offline;

    private RideCheck(@Nullable Problem problem, boolean approximatePickup, boolean approximateDropoff,
                      boolean checkDropoffInRideApp, boolean offline) {
        this.problem = problem;
        this.approximatePickup = approximatePickup;
        this.approximateDropoff = approximateDropoff;
        this.checkDropoffInRideApp = checkDropoffInRideApp;
        this.offline = offline;
    }

    /**
     * @param request the ride the button would ask for
     * @param online  whether the phone has a working internet connection right now
     */
    public static RideCheck of(RideRequest request, boolean online) {
        boolean offline = !online;
        RidePlace pickup = request.pickup;
        boolean approximatePickup = pickup != null && !pickup.myLocation && pickup.approximate;
        if (!request.hasDropoff()) {
            return new RideCheck(Problem.NO_DROPOFF, approximatePickup, false, false, offline);
        }
        RidePlace dropoff = request.dropoff;
        return new RideCheck(null, approximatePickup, dropoff.approximate, !dropoff.hasCoordinates(), offline);
    }

    public boolean enabled() {
        return problem == null;
    }
}
