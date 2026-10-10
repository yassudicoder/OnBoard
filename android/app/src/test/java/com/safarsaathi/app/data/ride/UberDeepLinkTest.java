package com.safarsaathi.app.data.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

/**
 * The Uber link follows Uber's documented universal-link format (see {@link UberDeepLink}): action=setPickup, then
 * client_id when we have one, then pickup and dropoff keys, everything percent-encoded as UTF-8.
 */
public class UberDeepLinkTest {

    private static final RidePlace T2 = RidePlace.point("Mumbai Airport Terminal 2, Departures",
            "Chhatrapati Shivaji Maharaj International Airport Terminal 2, Mumbai", 19.0969906, 72.8739312, true);

    @Test
    public void departure_myLocationPickup_toTheTerminal() {
        assertEquals("https://m.uber.com/ul/?action=setPickup"
                        + "&pickup=my_location"
                        + "&dropoff%5Blatitude%5D=19.096991"
                        + "&dropoff%5Blongitude%5D=72.873931"
                        + "&dropoff%5Bnickname%5D=Mumbai%20Airport%20Terminal%202%2C%20Departures"
                        + "&dropoff%5Bformatted_address%5D=Chhatrapati%20Shivaji%20Maharaj%20International%20Airport"
                        + "%20Terminal%202%2C%20Mumbai",
                UberDeepLink.build(null, RidePlace.myLocation(), T2));
    }

    @Test
    public void namesWithSpacesAndNonAsciiCharacters_areEncodedAsUtf8() {
        // "मुंबई विमानतळ" (Marathi: Mumbai airport), written with escapes so the file's own encoding cannot matter.
        String marathi = "मुंबई विमानतळ";
        String link = UberDeepLink.build(null, RidePlace.myLocation(),
                RidePlace.point(marathi, "Café Mondegar, Colaba", 18.9236, 72.8319, false));

        assertTrue(link, link.contains("&dropoff%5Bnickname%5D="
                + "%E0%A4%AE%E0%A5%81%E0%A4%82%E0%A4%AC%E0%A4%88%20"
                + "%E0%A4%B5%E0%A4%BF%E0%A4%AE%E0%A4%BE%E0%A4%A8%E0%A4%A4%E0%A4%B3"));
        assertTrue(link, link.contains("&dropoff%5Bformatted_address%5D=Caf%C3%A9%20Mondegar%2C%20Colaba"));
        assertFalse("spaces are %20, never +", link.contains("+"));
    }

    @Test
    public void characters_thatWouldBreakTheUrl_areEncoded() {
        assertEquals("A%26B%3DC%2BD%2F%C3%89", UberDeepLink.encode("A&B=C+D/É"));
    }

    @Test
    public void myLocationPickup_sendsNoPickupCoordinates() {
        String link = UberDeepLink.build(null, RidePlace.myLocation(), T2);

        assertTrue(link.contains("&pickup=my_location&"));
        assertFalse(link.contains("pickup%5B"));
    }

    @Test
    public void knownPickup_sendsItsCoordinatesAndNames() {
        RidePlace pickup = RidePlace.point("Arrivals pickup", "Gate 3", -33.94, 151.17, false);

        String link = UberDeepLink.build(null, pickup, T2);

        assertTrue(link, link.contains("?action=setPickup&pickup%5Blatitude%5D=-33.94&pickup%5Blongitude%5D=151.17"
                + "&pickup%5Bnickname%5D=Arrivals%20pickup&pickup%5Bformatted_address%5D=Gate%203&dropoff%5B"));
        assertFalse(link.contains("my_location"));
    }

    @Test
    public void missingClientId_buildsTheLinkWithoutIt() {
        assertFalse(UberDeepLink.build(null, RidePlace.myLocation(), T2).contains("client_id"));
        assertFalse(UberDeepLink.build("", RidePlace.myLocation(), T2).contains("client_id"));
        assertFalse(UberDeepLink.build("   ", RidePlace.myLocation(), T2).contains("client_id"));
    }

    @Test
    public void clientId_comesRightAfterTheAction() {
        assertTrue(UberDeepLink.build(" abc-123_X ", RidePlace.myLocation(), T2)
                .startsWith("https://m.uber.com/ul/?action=setPickup&client_id=abc-123_X&pickup=my_location&"));
    }

    @Test
    public void missingDropoff_cannotBuildALink() {
        assertThrows(IllegalArgumentException.class,
                () -> UberDeepLink.build(null, RidePlace.myLocation(), null));
        assertThrows(IllegalArgumentException.class,
                () -> UberDeepLink.build(null, RidePlace.myLocation(), RidePlace.typed("   ")));
        assertThrows(IllegalArgumentException.class,
                () -> UberDeepLink.build(null, RidePlace.myLocation(), RidePlace.myLocation()));
    }

    @Test
    public void typedAddress_isSentWithoutCoordinates() {
        String link = UberDeepLink.build(null, RidePlace.myLocation(), RidePlace.typed("  12 MG Road, Pune "));

        assertTrue(link, link.endsWith("&pickup=my_location&dropoff%5Bformatted_address%5D=12%20MG%20Road%2C%20Pune"));
        assertFalse(link.contains("dropoff%5Blatitude"));
    }

    @Test
    public void coordinates_alwaysUseADot_whateverThePhoneLanguage() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // German writes 19,5 for 19.5
            assertEquals("19.096991", UberDeepLink.coordinate(19.0969906));
            assertEquals("72.5", UberDeepLink.coordinate(72.5));
            assertEquals("-0.000001", UberDeepLink.coordinate(-0.000001));
        } finally {
            Locale.setDefault(before);
        }
    }
}
