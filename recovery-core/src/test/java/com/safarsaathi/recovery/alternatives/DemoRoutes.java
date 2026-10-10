package com.safarsaathi.recovery.alternatives;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safarsaathi.recovery.connection.FlightLeg;
import com.safarsaathi.recovery.plan.RecoverySettings;
import com.safarsaathi.recovery.policy.AirlinePolicies;
import com.safarsaathi.recovery.policy.AirlinePolicy;
import com.safarsaathi.recovery.policy.ContactChannel;
import com.safarsaathi.recovery.rules.AirportPack;
import com.safarsaathi.recovery.rules.AirportRules;
import com.safarsaathi.recovery.rules.ConnectionRule;
import com.safarsaathi.recovery.rules.ConnectionType;
import com.safarsaathi.recovery.rules.RulesDefaults;
import com.safarsaathi.recovery.rules.SourcedMinutes;
import com.safarsaathi.recovery.rules.TransferEstimates;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * Test data shared by the alternatives and plan tests: the same rules, settings and SAMPLE flights as the files in
 * mock-data/recovery, around trip 3 (MJ 31 Doha 21:55 on 11 Oct → London 03:10 on 12 Oct missed or cancelled).
 */
public final class DemoRoutes {

    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    public static final ZoneId QATAR = ZoneId.of("Asia/Qatar");
    public static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    public static final ZoneId BAHRAIN = ZoneId.of("Asia/Bahrain");
    public static final ZoneId LONDON = ZoneId.of("Europe/London");

    /** MJ 31's timetable arrival in London: 03:10 on 12 Oct (BST). */
    public static final Instant ORIGINAL_ARRIVAL = at("2026-10-12T03:10+01:00");

    private DemoRoutes() {
    }

    /** MJ 33 Doha 07:40 → London 12:55 on 12 Oct: direct. */
    public static CandidateRoute mj33() {
        return route(33800, leg("MJ 33", "DOH", QATAR, "2026-10-12T07:40+03:00", "LHR", LONDON, "2026-10-12T12:55+01:00"));
    }

    /** MJ 37 Doha 13:30 → London 18:45 on 12 Oct: direct. */
    public static CandidateRoute mj37() {
        return route(29900, leg("MJ 37", "DOH", QATAR, "2026-10-12T13:30+03:00", "LHR", LONDON, "2026-10-12T18:45+01:00"));
    }

    /** DA 112 Doha 01:45 → Dubai 03:50, DA 5 Dubai 06:00 → London 10:25 (130 min in Dubai, needs 75: OK). */
    public static CandidateRoute viaDubai() {
        return route(46300,
                leg("DA 112", "DOH", QATAR, "2026-10-12T01:45+03:00", "DXB", DUBAI, "2026-10-12T03:50+04:00"),
                leg("DA 5", "DXB", DUBAI, "2026-10-12T06:00+04:00", "LHR", LONDON, "2026-10-12T10:25+01:00"));
    }

    /** DA 114 Doha 22:40 → Dubai 00:45, then the same DA 5: valid, but waits longer than {@link #viaDubai()}. */
    public static CandidateRoute viaDubaiLongWait() {
        return route(46300,
                leg("DA 114", "DOH", QATAR, "2026-10-11T22:40+03:00", "DXB", DUBAI, "2026-10-12T00:45+04:00"),
                leg("DA 5", "DXB", DUBAI, "2026-10-12T06:00+04:00", "LHR", LONDON, "2026-10-12T10:25+01:00"));
    }

    /** DA 114 → Dubai 00:45, DA 3 Dubai 01:40 → London 06:15: only 55 min in Dubai, which needs 75. */
    public static CandidateRoute viaDubaiTooShort() {
        return route(46300,
                leg("DA 114", "DOH", QATAR, "2026-10-11T22:40+03:00", "DXB", DUBAI, "2026-10-12T00:45+04:00"),
                leg("DA 3", "DXB", DUBAI, "2026-10-12T01:40+04:00", "LHR", LONDON, "2026-10-12T06:15+01:00"));
    }

    /** DN 404 Doha 23:20 → Bahrain 00:10, DN 9 Bahrain 01:30 → London 06:00: earliest, but Bahrain has no MCT. */
    public static CandidateRoute viaBahrain() {
        return route(38900,
                leg("DN 404", "DOH", QATAR, "2026-10-11T23:20+03:00", "BAH", BAHRAIN, "2026-10-12T00:10+03:00"),
                leg("DN 9", "BAH", BAHRAIN, "2026-10-12T01:30+03:00", "LHR", LONDON, "2026-10-12T06:00+01:00"));
    }

    /** IJ 1909 Delhi 08:20 → Doha 10:05, MJ 37 Doha 13:30 → London 18:45: two airlines = separate tickets. */
    public static CandidateRoute delhiTwoAirlines() {
        return route(49700,
                leg("IJ 1909", "DEL", INDIA, "2026-10-12T08:20+05:30", "DOH", QATAR, "2026-10-12T10:05+03:00"),
                leg("MJ 37", "DOH", QATAR, "2026-10-12T13:30+03:00", "LHR", LONDON, "2026-10-12T18:45+01:00"));
    }

    /** Everything leaving Doha after a hub cancellation, in no particular order. */
    public static List<CandidateRoute> fromDoha() {
        return new ArrayList<>(Arrays.asList(mj37(), viaDubaiTooShort(), mj33(), viaBahrain(), viaDubaiLongWait(),
                viaDubai()));
    }

    public static FlightLeg leg(String flightNo, String from, ZoneId fromZone, String departs, String to, ZoneId toZone,
                                String arrives) {
        FlightLeg leg = new FlightLeg();
        leg.flightNo = flightNo;
        leg.depAirport = from;
        leg.depZone = fromZone;
        leg.scheduledDep = at(departs);
        leg.arrAirport = to;
        leg.arrZone = toZone;
        leg.scheduledArr = at(arrives);
        return leg;
    }

    public static CandidateRoute route(int fareInr, FlightLeg... legs) {
        CandidateRoute route = new CandidateRoute();
        route.legs = new ArrayList<>(Arrays.asList(legs));
        route.fareInr = fareInr;
        route.fareRules = "SAMPLE: test rules";
        route.source = OptionSource.SAMPLE;
        return route;
    }

    public static Instant at(String timeWithOffset) {
        return OffsetDateTime.parse(timeWithOffset).toInstant();
    }

    /** The values of airport-rules.json for the airports used here. */
    public static AirportRules rules() {
        AirportRules rules = new AirportRules();
        rules.lastChecked = "2026-10-11";
        rules.defaults = new RulesDefaults();
        rules.defaults.transfer = new TransferEstimates(10, 10, 20, ValueKind.ESTIMATE, "Team estimate");
        rules.defaults.gateClose = new SourcedMinutes(20, ValueKind.ESTIMATE, "Team estimate", null, "2026-10-11");
        rules.defaults.departureProcess = new SourcedMinutes(90, ValueKind.ESTIMATE, "Team estimate", null,
                "2026-10-11");
        rules.defaults.atRiskMarginMinutes = 15;

        AirportPack doha = airport("DOH", "Doha", "QA", "Asia/Qatar");
        doha.connectionRules.add(demoValue(60));
        doha.gateClose = new SourcedMinutes(20, ValueKind.OFFICIAL, "Hamad International Airport, Transfers page",
                "https://dohahamadairport.com/airport-guide/at-the-airport/transfers", "2026-10-11");
        AirportPack dubai = airport("DXB", "Dubai", "AE", "Asia/Dubai");
        dubai.connectionRules.add(demoValue(75));
        rules.airports = new ArrayList<>(Arrays.asList(airport("DEL", "Delhi", "IN", "Asia/Kolkata"), doha, dubai,
                airport("BAH", "Bahrain", "BH", "Asia/Bahrain"), airport("LHR", "London", "GB", "Europe/London")));
        return rules;
    }

    /** The values of recovery-settings.json. */
    public static RecoverySettings settings() {
        RecoverySettings settings = new RecoverySettings();
        settings.lastChecked = "2026-10-11";
        settings.normal = new RankingWeights(180, 10);
        settings.urgent = new RankingWeights(30, 1);
        settings.searchWindowHours = 36;
        settings.maxLayoverHours = 12;
        settings.overnightThresholdHours = 6;
        settings.staleAfterMinutes = 15;
        settings.otherOptionsShown = 2;
        settings.alternativesKept = 10;
        return settings;
    }

    /** IndiJet (no assistance text: unknown) and Marjan Air (a SAMPLE assistance text). */
    public static AirlinePolicies policies() {
        AirlinePolicies policies = new AirlinePolicies();
        policies.lastChecked = "2026-10-11";
        policies.airlines.add(policy("IJ", "IndiJet", null));
        policies.airlines.add(policy("MJ", "Marjan Air", "SAMPLE POLICY (fictional airline): meal vouchers."));
        policies.passengerRightsText = "Passenger-rights rules may apply depending on your route and airline.";
        return policies;
    }

    private static AirlinePolicy policy(String code, String name, String text) {
        AirlinePolicy policy = new AirlinePolicy();
        policy.airlineCode = code;
        policy.name = name;
        policy.fictional = true;
        policy.kind = "SAMPLE";
        policy.delayAssistanceText = text;
        ContactChannel desk = new ContactChannel();
        desk.type = "DESK";
        desk.label = name + " desk";
        policy.contactChannels.add(desk);
        policy.lastChecked = "2026-10-11";
        return policy;
    }

    private static ConnectionRule demoValue(int minutes) {
        return new ConnectionRule(ConnectionType.INTL_INTL, null, minutes, ValueKind.DEMO_VALUE,
                "DEMO VALUE - not an official figure", null, "2026-10-11");
    }

    private static AirportPack airport(String iata, String city, String country, String zone) {
        AirportPack airport = new AirportPack();
        airport.iata = iata;
        Map<String, String> names = new HashMap<>();
        names.put("en", city);
        airport.city = names;
        airport.country = country;
        airport.timeZone = zone;
        airport.lastChecked = "2026-10-11";
        return airport;
    }
}
