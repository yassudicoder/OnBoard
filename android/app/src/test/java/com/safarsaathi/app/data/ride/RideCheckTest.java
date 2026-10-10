package com.safarsaathi.app.data.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What a ride button says before it is tapped: disabled with a reason, approximate points, offline. */
public class RideCheckTest {

    private static final RidePlace APPROXIMATE_T2 =
            RidePlace.point("Mumbai Airport Terminal 2, Departures", null, 19.0969906, 72.8739312, true);

    @Test
    public void missingDropoff_disablesTheButton_withAReason() {
        RideCheck none = RideCheck.of(new RideRequest(RidePlacement.ARRIVAL, RidePlace.myLocation(), null), true);
        RideCheck blank = RideCheck.of(
                new RideRequest(RidePlacement.ARRIVAL, RidePlace.myLocation(), RidePlace.typed("  ")), true);

        assertFalse(none.enabled());
        assertEquals(RideCheck.Problem.NO_DROPOFF, none.problem);
        assertFalse(blank.enabled());
        assertEquals(RideCheck.Problem.NO_DROPOFF, blank.problem);
    }

    @Test
    public void unverifiedAirportPoint_isApproximate() {
        RideCheck check = RideCheck.of(
                new RideRequest(RidePlacement.DEPARTURE, RidePlace.myLocation(), APPROXIMATE_T2), true);

        assertTrue(check.enabled());
        assertNull(check.problem);
        assertTrue(check.approximateDropoff);
        assertFalse("my location is never an 'approximate point'", check.approximatePickup);
        assertFalse(check.checkDropoffInRideApp);
    }

    @Test
    public void approximatePickupPoint_isSaid() {
        RidePlace pickup = RidePlace.point("Arrivals pickup", null, 19.09, 72.87, true);

        assertTrue(RideCheck.of(new RideRequest(RidePlacement.ARRIVAL, pickup, APPROXIMATE_T2), true)
                .approximatePickup);
    }

    @Test
    public void typedAddress_asksToCheckTheDropoffInTheRideApp() {
        RideCheck check = RideCheck.of(new RideRequest(RidePlacement.ARRIVAL, RidePlace.myLocation(),
                RidePlace.typed("Whitefield Residency, Bengaluru")), true);

        assertTrue(check.enabled());
        assertTrue(check.checkDropoffInRideApp);
        assertFalse(check.approximateDropoff);
    }

    @Test
    public void offline_stillLetsTheTravellerTry() {
        RideCheck check = RideCheck.of(
                new RideRequest(RidePlacement.DEPARTURE, RidePlace.myLocation(), APPROXIMATE_T2), false);

        assertTrue(check.enabled());
        assertTrue(check.offline);
    }
}
