package com.safarsaathi.app.ui.navigate;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The "Directions to T2" link follows Google's documented Maps URL format (Directions action). */
public class GoogleMapsLinkTest {

    @Test
    public void drivingDirectionsToTheTerminal() {
        assertEquals("https://www.google.com/maps/dir/?api=1"
                        + "&destination=Chhatrapati+Shivaji+Maharaj+International+Airport+Terminal+2%2C+Mumbai"
                        + "&travelmode=driving&dir_action=navigate",
                GoogleMapsLink.drivingDirectionsUrl(GoogleMapsLink.PILOT_TERMINAL_DESTINATION));
    }

    @Test
    public void specialCharacters_areEncoded() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=A%26B+%7C+C"
                + "&travelmode=driving&dir_action=navigate", GoogleMapsLink.drivingDirectionsUrl("A&B | C"));
    }
}
