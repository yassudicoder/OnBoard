package com.safarsaathi.recovery.policy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole airline policy file (mock-data/recovery/airline-policies.json): one {@link AirlinePolicy} per airline plus
 * the general passenger-rights sentence and its official links. Read by the backend (Jackson) and from C3 the app
 * (Gson). {@link #problems()} keeps it honest; the backend refuses to start with a broken file.
 */
public final class AirlinePolicies {

    public List<String> about = new ArrayList<>();
    /** yyyy-MM-dd */
    public String lastChecked;
    public List<AirlinePolicy> airlines = new ArrayList<>();
    /** "Passenger-rights rules may apply depending on your route and airline ..." Never a promise. */
    public String passengerRightsText;
    public List<RightsLink> passengerRightsLinks = new ArrayList<>();

    /** The airline with this code, or null when the file does not know it. */
    public AirlinePolicy find(String airlineCode) {
        if (airlineCode == null) {
            return null;
        }
        for (AirlinePolicy airline : airlines) {
            if (airlineCode.equals(airline.airlineCode)) {
                return airline;
            }
        }
        return null;
    }

    /** Everything wrong with the file, in plain words; empty when it is fine. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        checkDate("lastChecked of the file", lastChecked, problems);
        if (passengerRightsText == null || passengerRightsText.trim().isEmpty()) {
            problems.add("passengerRightsText is missing");
        }
        for (RightsLink link : passengerRightsLinks) {
            if (link.url == null || !link.url.startsWith("https://")) {
                problems.add("passenger-rights link " + link.label + " needs an https:// official page");
            }
            checkDate("passenger-rights link " + link.label + " lastChecked", link.lastChecked, problems);
        }
        Set<String> codes = new HashSet<>();
        for (AirlinePolicy airline : airlines) {
            String where = "airline " + airline.airlineCode;
            if (airline.airlineCode == null || !codes.add(airline.airlineCode)) {
                problems.add(where + ": missing or listed twice");
            }
            boolean sample = "SAMPLE".equals(airline.kind);
            if (!sample && !"OFFICIAL".equals(airline.kind)) {
                problems.add(where + ": kind must be OFFICIAL or SAMPLE");
            }
            if (airline.fictional && !sample) {
                problems.add(where + ": a fictional airline can only have SAMPLE data");
            }
            if ("OFFICIAL".equals(airline.kind) && (airline.sourceUrl == null || !airline.sourceUrl.startsWith("https://"))) {
                problems.add(where + ": OFFICIAL data needs the airline's https:// sourceUrl");
            }
            if (sample && airline.delayAssistanceText != null && !airline.delayAssistanceText.startsWith("SAMPLE")) {
                problems.add(where + ": a SAMPLE policy text must start with \"SAMPLE\"");
            }
            checkDate(where + " lastChecked", airline.lastChecked, problems);
            for (ContactChannel channel : airline.contactChannels) {
                checkChannel(where, airline.fictional, channel, problems);
            }
        }
        return problems;
    }

    /** Fictional airlines: no phone numbers, and web/email only on the reserved ".example" domain. */
    private static void checkChannel(String where, boolean fictional, ContactChannel channel, List<String> problems) {
        String type = channel.type;
        if (!"WEB".equals(type) && !"EMAIL".equals(type) && !"PHONE".equals(type) && !"DESK".equals(type)) {
            problems.add(where + ": contact type must be WEB, EMAIL, PHONE or DESK, not " + type);
            return;
        }
        if ("DESK".equals(type)) {
            if (channel.value != null) {
                problems.add(where + ": a DESK contact has no value (no invented desk locations)");
            }
            return;
        }
        if (channel.value == null || channel.value.trim().isEmpty()) {
            problems.add(where + ": a " + type + " contact needs a value");
            return;
        }
        if (fictional && "PHONE".equals(type)) {
            problems.add(where + ": a fictional airline gets no phone number (it could be someone's real number)");
        }
        if (fictional && !channel.value.contains(".example")) {
            problems.add(where + ": a fictional airline's " + type + " must use the reserved .example domain");
        }
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
}
