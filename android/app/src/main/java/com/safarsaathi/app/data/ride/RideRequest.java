package com.safarsaathi.app.data.ride;

import androidx.annotation.Nullable;

/** A ride we want a ride app to pre-fill: where from, where to, and which button asked. Plain Java. */
public final class RideRequest {

    public final RidePlacement placement;
    public final RidePlace pickup;
    /** Null when the app knows no drop-off yet (the button is then disabled, see {@link RideCheck}). */
    @Nullable
    public final RidePlace dropoff;

    public RideRequest(RidePlacement placement, RidePlace pickup, @Nullable RidePlace dropoff) {
        this.placement = placement;
        this.pickup = pickup;
        this.dropoff = dropoff;
    }

    /** True when there is a drop-off a ride app could use. */
    public boolean hasDropoff() {
        return dropoff != null && !dropoff.isEmpty();
    }
}
