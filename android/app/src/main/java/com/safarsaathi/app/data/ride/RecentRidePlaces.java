package com.safarsaathi.app.data.ride;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Addresses the traveller typed for a ride ("saved places"), newest first, at most {@link #MAX}. Phone only: kept
 * in a small file that is left out of cloud backup (res/xml/data_extraction_rules.xml) and never sent to our server.
 */
public final class RecentRidePlaces {

    public static final int MAX = 5;

    private static final String FILE = "onboard_ride_places";
    private static final String KEY_PREFIX = "place_";

    private RecentRidePlaces() {
    }

    public static List<String> all(Context context) {
        SharedPreferences prefs = prefs(context);
        List<String> places = new ArrayList<>();
        for (int i = 0; i < MAX; i++) {
            String place = prefs.getString(KEY_PREFIX + i, null);
            if (place != null) {
                places.add(place);
            }
        }
        return Collections.unmodifiableList(places);
    }

    /** Puts the address first (moving it up if it was already saved) and forgets the oldest beyond {@link #MAX}. */
    public static void remember(Context context, String address) {
        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        List<String> places = new ArrayList<>(all(context));
        places.remove(trimmed);
        places.add(0, trimmed);
        SharedPreferences.Editor editor = prefs(context).edit().clear();
        for (int i = 0; i < Math.min(places.size(), MAX); i++) {
            editor.putString(KEY_PREFIX + i, places.get(i));
        }
        editor.apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
