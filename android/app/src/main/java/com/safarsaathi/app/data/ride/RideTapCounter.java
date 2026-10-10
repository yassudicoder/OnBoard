package com.safarsaathi.app.data.ride;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Local-only analytics for the demo: how often each "Get a ride" button was tapped. Kept in a small file on the
 * phone (SharedPreferences) and shown on the Profile tab. Only counts, no places, no times, and it is never sent to
 * our server or anyone else.
 */
public final class RideTapCounter {

    private static final String FILE = "onboard_ride_taps";
    private static final String TAG = "RideTaps";

    private RideTapCounter() {
    }

    public static void count(Context context, RidePlacement placement) {
        SharedPreferences prefs = prefs(context);
        int taps = prefs.getInt(placement.name(), 0) + 1;
        prefs.edit().putInt(placement.name(), taps).apply();
        Log.i(TAG, placement + " ride button tapped (" + taps + " on this phone)");
    }

    public static int taps(Context context, RidePlacement placement) {
        return prefs(context).getInt(placement.name(), 0);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
