package com.safarsaathi.recovery.rules;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything OnBoard knows about one airport for connections: its time zone, its minimum connection times, when the
 * gate closes, and nearby airports. One entry of {@code mock-data/recovery/airport-rules.json}.
 *
 * <p>An airport with no connection rules is fine: its connections are then UNKNOWN, never guessed.
 */
public final class AirportPack {

    /** The three-letter airport code, e.g. "DOH". */
    public String iata;
    public String name;
    /** The city by language code ("en", "hi", "mr"), used in sentences: "You land in Doha at 21:10". */
    public Map<String, String> city = new HashMap<>();
    /** Two-letter country code, e.g. "QA". Decides whether a flight is domestic or international. */
    public String country;
    /** The airport's time zone, e.g. "Asia/Qatar". Every time at this airport is shown in it. */
    public String timeZone;
    public List<ConnectionRule> connectionRules = new ArrayList<>();
    /** When the gate closes before departure, if the airport says so; null = the file's default (an estimate). */
    public SourcedMinutes gateClose;
    /** Transfer estimates for this airport; null = the file's defaults. */
    public TransferEstimates transfer;
    public List<NearbyAirport> nearbyAirports = new ArrayList<>();
    /** The airport's official page for passengers, if we have one. */
    public String sourceUrl;
    /** When a teammate last checked this entry, as yyyy-MM-dd. */
    public String lastChecked;
    /** For teammates: what was checked and why a value is missing. */
    public String notes;

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    /** The city in this language, the English name when that language is missing, or the airport code. */
    public String cityName(String language) {
        String name = city == null ? null : city.get(language);
        if (name == null || name.trim().isEmpty()) {
            name = city == null ? null : city.get("en");
        }
        return name == null || name.trim().isEmpty() ? iata : name;
    }
}
