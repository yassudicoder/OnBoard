package com.safarsaathi.app.ui.navigate;

import android.content.res.Resources;

import com.safarsaathi.app.R;

/** Distances and times the way the Navigate tab and its notification write them. */
final class NavText {

    private NavText() {
    }

    /** "40 m" (rounded like Google Maps: to 5 m under 100 m, to 10 m above). */
    static String distance(Resources res, double metres) {
        long rounded;
        if (metres < 10) {
            rounded = Math.round(metres);
        } else if (metres < 100) {
            rounded = Math.round(metres / 5) * 5;
        } else {
            rounded = Math.round(metres / 10) * 10;
        }
        return res.getString(R.string.nav_metres, (int) rounded);
    }

    /** "4 min" (at least 1). */
    static String minutes(Resources res, double seconds) {
        return res.getString(R.string.nav_minutes, (int) Math.max(1, Math.ceil(seconds / 60)));
    }

    /** The app's language code ("en", "hi", "mr"); place names and instructions fall back to English. */
    static String language(Resources res) {
        return res.getConfiguration().getLocales().get(0).getLanguage();
    }
}
