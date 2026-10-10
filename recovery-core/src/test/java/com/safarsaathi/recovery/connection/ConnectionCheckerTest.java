package com.safarsaathi.recovery.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.safarsaathi.recovery.rules.AirportPack;
import com.safarsaathi.recovery.rules.AirportRules;
import com.safarsaathi.recovery.rules.ConnectionRule;
import com.safarsaathi.recovery.rules.ConnectionType;
import com.safarsaathi.recovery.rules.RulesDefaults;
import com.safarsaathi.recovery.rules.SourcedMinutes;
import com.safarsaathi.recovery.rules.TransferEstimates;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * The connection checker on demo trip 3 (Delhi 17:50 to Doha 19:35, then Doha 21:55 to London 03:10 next day) and
 * on the Leh trip (Pune to Delhi, then Delhi to Leh). The rules are built here, the same as in the real rules file:
 * Doha needs 60 min (DEMO VALUE) and its gate closes 20 min before departure (official); Delhi has no MCT; taxiing,
 * getting off and transfer security take 10 + 10 + 20 min (estimate); less than 15 min to spare is "at risk".
 */
class ConnectionCheckerTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId QATAR = ZoneId.of("Asia/Qatar");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    private final ConnectionChecker checker = new ConnectionChecker(rules());

    // ---- The four basic verdicts on the Doha connection ----

    @Test
    void onTimeConnectionIsOk() {
        ConnectionCheck check = checker.check(delhiDoha(0), dohaLondon(), null);

        assertThat(check.status).isEqualTo(ConnectionStatus.OK);
        assertThat(check.certainty()).isEqualTo(Certainty.PREDICTED);
        assertThat(check.connectionType).isEqualTo(ConnectionType.INTL_INTL);
        assertThat(check.availableMinutes).isEqualTo(140);
        assertThat(check.mctMinutes).isEqualTo(60);
        assertThat(check.slackMinutes).isEqualTo(80);
        assertThat(check.reason).isEqualTo("You land in Doha at 19:35, your next flight leaves at 21:55. "
                + "Doha needs at least 60 min (demo value). You have 140 min.");
    }

    @Test
    void ninetyFiveMinutesLateIsLikelyMissed() {
        ConnectionCheck check = checker.check(delhiDoha(95), dohaLondon(), null);

        assertThat(check.status).isEqualTo(ConnectionStatus.LIKELY_MISSED);
        assertThat(check.certainty()).isEqualTo(Certainty.PREDICTED);
        assertThat(check.availableMinutes).isEqualTo(45);
        assertThat(check.slackMinutes).isEqualTo(-15);
        assertThat(check.reason).isEqualTo("You land in Doha at 21:10, your next flight leaves at 21:55. "
                + "Doha needs at least 60 min (demo value). You have 45 min.");
    }

    @Test
    void seventyMinutesLateIsAtRisk() {
        // 70 min available: meets the 60-min minimum, but taxiing, getting off, security (40) + the gate closing
        // 20 min early also take 60 min, which leaves 10 min: less than the 15-min margin.
        ConnectionCheck check = checker.check(delhiDoha(70), dohaLondon(), null);

        assertThat(check.status).isEqualTo(ConnectionStatus.AT_RISK);
        assertThat(check.practicalMinutes).isEqualTo(60);
        assertThat(check.reason).isEqualTo("You land in Doha at 20:45, your next flight leaves at 21:55. "
                + "Doha needs at least 60 min (demo value). You have 70 min. That leaves only 10 min to spare: "
                + "taxiing, getting off the plane and transfer security take about 40 min (estimate), and the gate "
                + "closes 20 min before departure.");
    }

    @Test
    void sixtyMinutesLateIsStillOk() {
        assertThat(checker.check(delhiDoha(60), dohaLondon(), null).status).isEqualTo(ConnectionStatus.OK); // 80 min
    }

    @Test
    void reachingTheGateAfterItClosesIsOnlyAtRiskWhenTheMinimumIsMet() {
        // An airport whose official minimum (45) is lower than our estimate of the practical time (60): with 50 min
        // the airline's own rule is met, so our estimates may only warn (AT_RISK), never say "likely missed".
        AirportRules rules = rules();
        rules.find("DOH").connectionRules.set(0, new ConnectionRule(ConnectionType.INTL_INTL, null, 45,
                ValueKind.OFFICIAL, "Test airport page", "https://example.org/mct", "2026-10-11"));

        ConnectionCheck check = new ConnectionChecker(rules).check(delhiDoha(90), dohaLondon(), null);

        assertThat(check.status).isEqualTo(ConnectionStatus.AT_RISK);
        assertThat(check.reason).endsWith("Doha needs at least 45 min. You have 50 min. By our estimate you reach the "
                + "gate about 10 min after it closes: taxiing, getting off the plane and transfer security take "
                + "about 40 min (estimate), and the gate closes 20 min before departure.");
    }

    // ---- Time zones ----

    @Test
    void landingIsShownInDohaTimeNotDelhiTime() {
        ConnectionCheck check = checker.check(delhiDoha(95), dohaLondon(), null);

        // 21:10 in Doha is 23:40 in Delhi: the traveller must see the Doha clock.
        assertThat(check.landsAt).isEqualTo(ZonedDateTime.of(2026, 10, 11, 21, 10, 0, 0, QATAR));
        assertThat(check.nextLeavesAt).isEqualTo(ZonedDateTime.of(2026, 10, 11, 21, 55, 0, 0, QATAR));
        assertThat(check.reason).contains("21:10").doesNotContain("23:40");
    }

    @Test
    void aNextFlightAfterMidnightShowsItsDay() {
        FlightLeg late = dohaLondon();
        late.estimatedDep = at("2026-10-12T00:40+03:00");
        late.estimatedArr = at("2026-10-12T05:55+01:00");

        ConnectionCheck check = checker.check(delhiDoha(0), late, null);

        assertThat(check.reason).startsWith("You land in Doha at 19:35, your next flight leaves at 00:40 on 12 Oct.");
    }

    // ---- Unknown and demo values ----

    @Test
    void unknownMinimumConnectionTimeIsUnknownNotAGuess() {
        ConnectionCheck check = checker.check(puneDelhi(60), delhiLeh(), null);

        assertThat(check.status).isEqualTo(ConnectionStatus.UNKNOWN);
        assertThat(check.certainty()).isEqualTo(Certainty.UNKNOWN);
        assertThat(check.connectionType).isEqualTo(ConnectionType.DOM_DOM);
        assertThat(check.mctMinutes).isNull();
        assertThat(check.slackMinutes).isNull();
        assertThat(check.basedOnDemoValue).isFalse();
        assertThat(check.reason).isEqualTo("You land in Delhi at 09:20, your next flight leaves at 10:15. "
                + "OnBoard has no official minimum connection time for Delhi (domestic to domestic), so it can't tell "
                + "whether 55 min is enough. Ask the airline.");
    }

    @Test
    void aNextFlightLeavingBeforeLandingIsLikelyMissedEvenWithoutAMinimum() {
        ConnectionCheck check = checker.check(puneDelhi(150), delhiLeh(), null); // lands 10:50, Leh flight 10:15

        assertThat(check.status).isEqualTo(ConnectionStatus.LIKELY_MISSED);
        assertThat(check.availableMinutes).isEqualTo(-35);
        assertThat(check.basedOnDemoValue).isFalse();
        assertThat(check.reason).isEqualTo("Your next flight is due to leave Delhi at 10:15, before you land at 10:50.");
    }

    @Test
    void demoValueMinimumIsLabelledAndFlagged() {
        ConnectionCheck check = checker.check(delhiDoha(95), dohaLondon(), null);

        assertThat(check.mctKind).isEqualTo(ValueKind.DEMO_VALUE);
        assertThat(check.mctSource).isEqualTo("DEMO VALUE - not an official figure");
        assertThat(check.basedOnDemoValue).isTrue();
        assertThat(check.reason).contains("60 min (demo value)");
    }

    @Test
    void officialMinimumHasNoLabel() {
        AirportRules rules = rules();
        rules.find("DOH").connectionRules.set(0, new ConnectionRule(ConnectionType.INTL_INTL, null, 60,
                ValueKind.OFFICIAL, "Test airport page", "https://example.org/mct", "2026-10-11"));

        ConnectionCheck check = new ConnectionChecker(rules).check(delhiDoha(95), dohaLondon(), null);

        assertThat(check.basedOnDemoValue).isFalse();
        assertThat(check.reason).contains("Doha needs at least 60 min. You have 45 min.");
    }

    // ---- Terminals ----

    @Test
    void terminalChangeUsesTheTerminalChangeMinimum() {
        ConnectionChecker withTerminals = new ConnectionChecker(delhiWithTerminalRules());
        FlightLeg pune = puneDelhi(0); // lands 08:20, the Leh flight leaves 10:15: 115 min
        FlightLeg leh = delhiLeh();

        pune.arrTerminal = "T1";
        leh.depTerminal = "T3";
        ConnectionCheck change = withTerminals.check(pune, leh, null);
        assertThat(change.terminalChange).isTrue();
        assertThat(change.mctMinutes).isEqualTo(120);
        assertThat(change.status).isEqualTo(ConnectionStatus.LIKELY_MISSED);

        pune.arrTerminal = "T3";
        ConnectionCheck same = withTerminals.check(pune, leh, null);
        assertThat(same.terminalChange).isFalse();
        assertThat(same.mctMinutes).isEqualTo(60);
        assertThat(same.status).isEqualTo(ConnectionStatus.OK);
    }

    @Test
    void unknownTerminalsUseTheStricterMinimumAndSaySo() {
        FlightLeg pune = puneDelhi(0);
        pune.arrTerminal = null;

        ConnectionCheck check = new ConnectionChecker(delhiWithTerminalRules()).check(pune, delhiLeh(), null);

        assertThat(check.terminalChange).isNull();
        assertThat(check.mctMinutes).isEqualTo(120);
        assertThat(check.mctAssumesTerminalChange).isTrue();
        assertThat(check.reason).contains("Your terminals are not known yet, so this uses the minimum for changing "
                + "terminals.");
    }

    // ---- Confirmed facts ----

    @Test
    void nextFlightLeavingWhileStillInTheAirIsConfirmedMissed() {
        FlightLeg inbound = delhiDoha(95);
        inbound.phase = FlightPhase.IN_AIR;
        FlightLeg outbound = dohaLondon();
        outbound.phase = FlightPhase.IN_AIR;

        ConnectionCheck check = checker.check(inbound, outbound, null);

        assertThat(check.status).isEqualTo(ConnectionStatus.CONFIRMED_MISSED);
        assertThat(check.certainty()).isEqualTo(Certainty.CONFIRMED);
        assertThat(check.basedOnDemoValue).isFalse(); // a fact, not a calculation
        assertThat(check.reason).isEqualTo("Your next flight MJ 31 has left Doha before you could get there.");
    }

    @Test
    void nextFlightLeavingAfterLandingIsNotConfirmedByItself() {
        // Both landed / left: the traveller may have run and made it, so it stays a prediction.
        FlightLeg inbound = delhiDoha(95);
        inbound.phase = FlightPhase.LANDED;
        FlightLeg outbound = dohaLondon();
        outbound.phase = FlightPhase.IN_AIR;

        assertThat(checker.check(inbound, outbound, null).status).isEqualTo(ConnectionStatus.LIKELY_MISSED);

        ConnectionFacts confirmedEarlier = new ConnectionFacts();
        confirmedEarlier.missConfirmed = true;
        assertThat(checker.check(inbound, outbound, confirmedEarlier).status)
                .isEqualTo(ConnectionStatus.CONFIRMED_MISSED);
    }

    @Test
    void cancelledFlightsAreCancelled() {
        FlightLeg cancelledFirst = delhiDoha(0);
        cancelledFirst.cancelled = true;
        ConnectionCheck before = checker.check(cancelledFirst, dohaLondon(), null);
        assertThat(before.status).isEqualTo(ConnectionStatus.CANCELLED);
        assertThat(before.certainty()).isEqualTo(Certainty.CONFIRMED);
        assertThat(before.reason).isEqualTo("Your flight IJ 1907 from Delhi to Doha is cancelled.");

        FlightLeg cancelledOnward = dohaLondon();
        cancelledOnward.cancelled = true;
        ConnectionCheck atHub = checker.check(delhiDoha(0), cancelledOnward, null);
        assertThat(atHub.status).isEqualTo(ConnectionStatus.CANCELLED);
        assertThat(atHub.reason).isEqualTo("Your onward flight MJ 31 from Doha to London is cancelled.");
    }

    @Test
    void confirmedRebookingAndResolutionWin() {
        ConnectionFacts facts = new ConnectionFacts();
        facts.rebookedFlight = "MJ 33";
        ConnectionCheck rebooked = checker.check(delhiDoha(95), dohaLondon(), facts);
        assertThat(rebooked.status).isEqualTo(ConnectionStatus.REBOOKED_BY_AIRLINE);
        assertThat(rebooked.reason).isEqualTo("The airline has rebooked you on MJ 33.");

        facts.resolved = true;
        assertThat(checker.check(delhiDoha(95), dohaLondon(), facts).status).isEqualTo(ConnectionStatus.RESOLVED);
    }

    @Test
    void everyStatusIsEitherPredictedConfirmedOrUnknown() {
        List<ConnectionStatus> predicted = new ArrayList<>();
        List<ConnectionStatus> confirmed = new ArrayList<>();
        for (ConnectionStatus status : ConnectionStatus.values()) {
            if (status.certainty() == Certainty.PREDICTED) {
                predicted.add(status);
            } else if (status.certainty() == Certainty.CONFIRMED) {
                confirmed.add(status);
            }
        }
        assertThat(predicted).containsExactly(ConnectionStatus.OK, ConnectionStatus.AT_RISK,
                ConnectionStatus.LIKELY_MISSED);
        assertThat(confirmed).containsExactly(ConnectionStatus.CONFIRMED_MISSED, ConnectionStatus.CANCELLED,
                ConnectionStatus.REBOOKED_BY_AIRLINE, ConnectionStatus.RESOLVED);
        assertThat(ConnectionStatus.UNKNOWN.certainty()).isEqualTo(Certainty.UNKNOWN);
    }

    // ---- Smaller things ----

    @Test
    void minimumForTheRippleEngineMatchesTheCheck() {
        assertThat(checker.minimumConnectionMinutes(delhiDoha(0), dohaLondon())).isEqualTo(60);
        assertThat(checker.minimumConnectionMinutes(puneDelhi(0), delhiLeh())).isNull();
    }

    @Test
    void aLanguageWithoutSentencesFallsBackToEnglish() {
        assertThat(checker.check(delhiDoha(95), dohaLondon(), null, "ta").reason)
                .startsWith("You land in Doha at 21:10");
    }

    // ---- Test data ----

    /** IJ 1907 Delhi 17:50 to Doha 19:35 (local times), {@code minutesLate} later. */
    private static FlightLeg delhiDoha(int minutesLate) {
        return late(leg("IJ 1907", "DEL", INDIA, "T3", "2026-10-11T17:50+05:30",
                "DOH", QATAR, null, "2026-10-11T19:35+03:00"), minutesLate);
    }

    /** MJ 31 Doha 21:55 to London 03:10 the next morning (local times). */
    private static FlightLeg dohaLondon() {
        return leg("MJ 31", "DOH", QATAR, null, "2026-10-11T21:55+03:00",
                "LHR", LONDON, null, "2026-10-12T03:10+01:00");
    }

    /** SM 231 Pune 06:10 to Delhi 08:20, {@code minutesLate} later. */
    private static FlightLeg puneDelhi(int minutesLate) {
        return late(leg("SM 231", "PNQ", INDIA, null, "2026-10-11T06:10+05:30",
                "DEL", INDIA, "T3", "2026-10-11T08:20+05:30"), minutesLate);
    }

    /** SM 445 Delhi 10:15 to Leh 11:40. */
    private static FlightLeg delhiLeh() {
        return leg("SM 445", "DEL", INDIA, "T3", "2026-10-11T10:15+05:30",
                "IXL", INDIA, null, "2026-10-11T11:40+05:30");
    }

    private static FlightLeg leg(String flightNo, String from, ZoneId fromZone, String fromTerminal, String departs,
                                 String to, ZoneId toZone, String toTerminal, String arrives) {
        FlightLeg leg = new FlightLeg();
        leg.flightNo = flightNo;
        leg.depAirport = from;
        leg.depZone = fromZone;
        leg.depTerminal = fromTerminal;
        leg.scheduledDep = at(departs);
        leg.arrAirport = to;
        leg.arrZone = toZone;
        leg.arrTerminal = toTerminal;
        leg.scheduledArr = at(arrives);
        return leg;
    }

    /** The flight runs {@code minutes} later than its timetable (0 = no estimate). */
    private static FlightLeg late(FlightLeg leg, int minutes) {
        if (minutes > 0) {
            leg.estimatedDep = leg.scheduledDep.plus(Duration.ofMinutes(minutes));
            leg.estimatedArr = leg.scheduledArr.plus(Duration.ofMinutes(minutes));
        }
        return leg;
    }

    private static java.time.Instant at(String localTimeWithOffset) {
        return OffsetDateTime.parse(localTimeWithOffset).toInstant();
    }

    /** The same values as mock-data/recovery/airport-rules.json, for the airports these tests use. */
    static AirportRules rules() {
        AirportRules rules = new AirportRules();
        rules.lastChecked = "2026-10-11";
        rules.defaults = new RulesDefaults();
        rules.defaults.transfer = new TransferEstimates(10, 10, 20, ValueKind.ESTIMATE, "Team estimate");
        rules.defaults.gateClose = new SourcedMinutes(20, ValueKind.ESTIMATE, "Team estimate", null, "2026-10-11");
        rules.defaults.atRiskMarginMinutes = 15;

        AirportPack doha = airport("DOH", "Doha", "QA", "Asia/Qatar");
        doha.connectionRules.add(new ConnectionRule(ConnectionType.INTL_INTL, null, 60, ValueKind.DEMO_VALUE,
                "DEMO VALUE - not an official figure", null, "2026-10-11"));
        doha.gateClose = new SourcedMinutes(20, ValueKind.OFFICIAL, "Hamad International Airport, Transfers page",
                "https://dohahamadairport.com/airport-guide/at-the-airport/transfers", "2026-10-11");

        rules.airports = new ArrayList<>(Arrays.asList(
                airport("DEL", "Delhi", "IN", "Asia/Kolkata"), doha, airport("LHR", "London", "GB", "Europe/London"),
                airport("PNQ", "Pune", "IN", "Asia/Kolkata"), airport("IXL", "Leh", "IN", "Asia/Kolkata")));
        return rules;
    }

    /** {@link #rules()} with test-only Delhi rules: 60 min within a terminal, 120 min to change terminals. */
    private static AirportRules delhiWithTerminalRules() {
        AirportRules rules = rules();
        rules.find("DEL").connectionRules.add(new ConnectionRule(ConnectionType.DOM_DOM, false, 60,
                ValueKind.OFFICIAL, "Test page", "https://example.org/mct", "2026-10-11"));
        rules.find("DEL").connectionRules.add(new ConnectionRule(ConnectionType.DOM_DOM, true, 120,
                ValueKind.OFFICIAL, "Test page", "https://example.org/mct", "2026-10-11"));
        return rules;
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
