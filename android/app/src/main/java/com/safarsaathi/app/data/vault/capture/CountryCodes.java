package com.safarsaathi.app.data.vault.capture;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Country codes: travel documents print 3-letter codes ("IND", "RWA", "COD"); the vault stores 2-letter ones
 * ("IN", "RW", "CD"), like its country packs. The table is built from the country list that ships with Java and
 * Android (ISO 3166), so the team types in no codes. One ICAO exception: Germany's documents print "D", not "DEU"
 * (ICAO Doc 9303 Part 3). Codes that are not countries (e.g. "XXA", stateless) give null and are shown as printed.
 */
public final class CountryCodes {

    private static final Map<String, String> ALPHA3_TO_ALPHA2 = new HashMap<>();
    private static final Map<String, String> ALPHA2_TO_ALPHA3 = new HashMap<>();

    static {
        for (String alpha2 : Locale.getISOCountries()) {
            try {
                String alpha3 = new Locale.Builder().setRegion(alpha2).build().getISO3Country();
                if (alpha3 != null && alpha3.length() == 3) {
                    ALPHA3_TO_ALPHA2.put(alpha3, alpha2);
                    ALPHA2_TO_ALPHA3.put(alpha2, alpha3);
                }
            } catch (MissingResourceException | IllformedLocaleException e) {
                // a region without a 3-letter code: leave it out
            }
        }
        ALPHA3_TO_ALPHA2.put("D", "DE");
    }

    private CountryCodes() {
    }

    /** "IND" becomes "IN"; "D" becomes "DE"; null for codes that are not a country. */
    @Nullable
    public static String alpha2(@Nullable String alpha3) {
        if (alpha3 == null) {
            return null;
        }
        return ALPHA3_TO_ALPHA2.get(alpha3.replace("<", "").trim().toUpperCase(Locale.ROOT));
    }

    /** "IN" becomes "IND"; null when unknown. */
    @Nullable
    public static String alpha3(@Nullable String alpha2) {
        return alpha2 == null ? null : ALPHA2_TO_ALPHA3.get(alpha2.trim().toUpperCase(Locale.ROOT));
    }

    /** True for a real 2-letter country code such as "CD". */
    public static boolean isCountry(@Nullable String alpha2) {
        return alpha3(alpha2) != null;
    }
}
