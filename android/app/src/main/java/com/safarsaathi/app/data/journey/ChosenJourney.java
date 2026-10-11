package com.safarsaathi.app.data.journey;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.time.LocalDateTime;

/**
 * The journey the traveller picked in "Plan a journey" (a SAMPLE flight), kept on this phone. While one is set, the
 * Home flight card shows it instead of the server's demo trip; "Find my trip" (booking reference) clears it again.
 */
public final class ChosenJourney {

    private static final String PREFS = "onboard_journey";

    public final String flightNo;
    public final JourneyCatalog.City from;
    public final JourneyCatalog.City to;
    public final LocalDateTime departs;
    public final LocalDateTime arrives;

    private ChosenJourney(String flightNo, JourneyCatalog.City from, JourneyCatalog.City to, LocalDateTime departs,
                          LocalDateTime arrives) {
        this.flightNo = flightNo;
        this.from = from;
        this.to = to;
        this.departs = departs;
        this.arrives = arrives;
    }

    public static void save(Context context, JourneyCatalog.Flight flight) {
        prefs(context).edit()
                .putString("flight", flight.flightNo)
                .putString("from", flight.from.code)
                .putString("to", flight.to.code)
                .putString("departs", flight.departs.toString())
                .putString("arrives", flight.arrives.toString())
                .apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    @Nullable
    public static ChosenJourney load(Context context) {
        SharedPreferences prefs = prefs(context);
        String flight = prefs.getString("flight", null);
        if (flight == null) {
            return null;
        }
        try {
            return new ChosenJourney(flight, JourneyCatalog.city(prefs.getString("from", "BOM")),
                    JourneyCatalog.city(prefs.getString("to", "BLR")),
                    LocalDateTime.parse(prefs.getString("departs", "")),
                    LocalDateTime.parse(prefs.getString("arrives", "")));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
