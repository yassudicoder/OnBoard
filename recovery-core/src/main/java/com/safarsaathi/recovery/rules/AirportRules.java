package com.safarsaathi.recovery.rules;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole airport rules file, {@code mock-data/recovery/airport-rules.json}: defaults plus one {@link AirportPack}
 * per airport. The backend reads it with Jackson, the app (from C3) with Gson, into these same classes.
 *
 * <p>{@link #problems()} checks the file: every number has a kind and a source, every official number a link, every
 * demo value says so. The backend refuses to start with a broken file, and a test runs it on the real file.
 */
public final class AirportRules {

    /** For humans reading the file: what it is and how to edit it. */
    public List<String> about = new ArrayList<>();
    public String preparedBy;
    /** When the file as a whole was last reviewed, as yyyy-MM-dd. */
    public String lastChecked;
    public RulesDefaults defaults;
    public List<AirportPack> airports = new ArrayList<>();

    /** The airport with this code, or null when the file does not know it. */
    public AirportPack find(String iata) {
        if (iata == null) {
            return null;
        }
        for (AirportPack airport : airports) {
            if (iata.equals(airport.iata)) {
                return airport;
            }
        }
        return null;
    }

    /** Everything wrong with the file, in plain words; empty when it is fine. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        checkDate("lastChecked of the file", lastChecked, problems);
        if (defaults == null) {
            problems.add("defaults are missing");
        } else {
            checkTransfer("defaults.transfer", defaults.transfer, problems);
            checkSourced("defaults.gateClose", defaults.gateClose, problems);
            if (defaults.transfer == null) {
                problems.add("defaults.transfer is missing");
            }
            if (defaults.gateClose == null) {
                problems.add("defaults.gateClose is missing");
            }
            checkSourced("defaults.departureProcess", defaults.departureProcess, problems);
            if (defaults.departureProcess == null) {
                problems.add("defaults.departureProcess is missing");
            }
            if (defaults.atRiskMarginMinutes < 0) {
                problems.add("defaults.atRiskMarginMinutes must be 0 or more");
            }
        }

        Set<String> codes = new HashSet<>();
        for (AirportPack airport : airports) {
            String where = "airport " + airport.iata;
            if (airport.iata == null || !airport.iata.matches("[A-Z]{3}")) {
                problems.add("an airport has no valid three-letter code: " + airport.iata);
            } else if (!codes.add(airport.iata)) {
                problems.add(where + " is listed twice");
            }
            if (airport.country == null || !airport.country.matches("[A-Z]{2}")) {
                problems.add(where + ": country must be a two-letter code");
            }
            try {
                ZoneId.of(airport.timeZone);
            } catch (RuntimeException e) { // null, or not a known time zone
                problems.add(where + ": unknown time zone " + airport.timeZone);
            }
            if (airport.city == null || airport.city.get("en") == null) {
                problems.add(where + ": city needs at least an English name");
            }
            checkDate(where + " lastChecked", airport.lastChecked, problems);
            for (ConnectionRule rule : airport.connectionRules) {
                String ruleWhere = where + " rule " + rule.type;
                if (rule.type == null) {
                    problems.add(where + ": a connection rule has no type");
                }
                checkNumber(ruleWhere, rule.minutes, rule.kind, rule.source, rule.sourceUrl, rule.lastChecked,
                        problems);
            }
            checkSourced(where + " gateClose", airport.gateClose, problems);
            checkTransfer(where + " transfer", airport.transfer, problems);
            for (NearbyAirport nearby : airport.nearbyAirports) {
                if (nearby.groundTransferMinutes <= 0 || isBlank(nearby.source)) {
                    problems.add(where + ": nearby airport " + nearby.iata
                            + " needs a ground transfer time and its source (no invented distances)");
                }
            }
        }
        for (AirportPack airport : airports) {
            for (NearbyAirport nearby : airport.nearbyAirports) {
                if (find(nearby.iata) == null) {
                    problems.add("airport " + airport.iata + ": nearby airport " + nearby.iata
                            + " is not in the file");
                }
            }
        }
        return problems;
    }

    private static void checkSourced(String where, SourcedMinutes value, List<String> problems) {
        if (value != null) {
            checkNumber(where, value.minutes, value.kind, value.source, value.sourceUrl, value.lastChecked, problems);
        }
    }

    private static void checkTransfer(String where, TransferEstimates transfer, List<String> problems) {
        if (transfer == null) {
            return;
        }
        if (transfer.taxiMinutes < 0 || transfer.deplaningMinutes < 0 || transfer.transferSecurityMinutes < 0) {
            problems.add(where + ": minutes cannot be negative");
        }
        if (transfer.kind != ValueKind.ESTIMATE) {
            problems.add(where + ": transfer times are team estimates, so kind must be ESTIMATE");
        }
        if (isBlank(transfer.source)) {
            problems.add(where + ": source is missing");
        }
    }

    /** The rules for any sourced number: positive, a kind, a source; official = a link; demo = says so. */
    private static void checkNumber(String where, int minutes, ValueKind kind, String source, String sourceUrl,
                                    String lastChecked, List<String> problems) {
        if (minutes <= 0) {
            problems.add(where + ": minutes must be more than 0");
        }
        if (kind == null) {
            problems.add(where + ": kind is missing (OFFICIAL, DEMO_VALUE or ESTIMATE)");
        }
        if (isBlank(source)) {
            problems.add(where + ": source is missing");
        }
        if (kind == ValueKind.OFFICIAL && isBlank(sourceUrl)) {
            problems.add(where + ": an OFFICIAL value needs the sourceUrl of the official page");
        }
        if (kind == ValueKind.DEMO_VALUE && (source == null || !source.startsWith("DEMO VALUE"))) {
            problems.add(where + ": a DEMO_VALUE's source must start with \"DEMO VALUE\"");
        }
        checkDate(where + " lastChecked", lastChecked, problems);
    }

    private static void checkDate(String where, String date, List<String> problems) {
        boolean valid = date != null;
        if (valid) {
            try {
                LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                valid = false;
            }
        }
        if (!valid) {
            problems.add(where + " must be a date like 2026-10-11, but is " + date);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
