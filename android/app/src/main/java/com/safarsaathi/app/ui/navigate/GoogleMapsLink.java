package com.safarsaathi.app.ui.navigate;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * "Directions to the airport": hands the trip from home to the terminal over to the real Google Maps app (driving,
 * turn-by-turn from the phone's own location). Uses Google's documented Maps URL
 * (developers.google.com/maps/documentation/urls/get-started, checked 2026-10-11):
 * {@code https://www.google.com/maps/dir/?api=1&destination=...&travelmode=driving&dir_action=navigate}.
 * It opens the Google Maps app when it is installed, else the browser. No API key, nothing sent by us.
 */
public final class GoogleMapsLink {

    /** The pilot terminal as Google Maps should look it up (a place name, not coordinates we would have to invent). */
    public static final String PILOT_TERMINAL_DESTINATION =
            "Chhatrapati Shivaji Maharaj International Airport Terminal 2, Mumbai";

    private GoogleMapsLink() {
    }

    /** The Maps URL for driving directions to a place name. Plain Java, checked by GoogleMapsLinkTest. */
    public static String drivingDirectionsUrl(String destination) {
        return "https://www.google.com/maps/dir/?api=1&destination=" + encode(destination)
                + "&travelmode=driving&dir_action=navigate";
    }

    /** Opens Google Maps with directions to the pilot terminal. False when no app can open the link. */
    public static boolean openDirectionsToTerminal(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(drivingDirectionsUrl(PILOT_TERMINAL_DESTINATION)));
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 always exists
        }
    }
}
