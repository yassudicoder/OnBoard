package com.safarsaathi.recovery.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The real rules file (mock-data/recovery/airport-rules.json, copied onto the classpath by the backend's pom.xml)
 * follows every rule, and the checks catch a broken entry. Runs in the backend's build, which lends it Jackson.
 */
class AirportRulesFileTest {

    /** Strict reader: a misspelt field name fails here instead of being skipped. */
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void theRealFileHasNoProblems() throws IOException {
        AirportRules rules = load();

        assertThat(rules.problems()).isEmpty();
        assertThat(rules.airports).extracting(airport -> airport.iata)
                .contains("DEL", "DOH", "LHR", "DXB", "BOM", "BLR", "PNQ", "IXL");
    }

    @Test
    void dohaUsesTheAgreedDemoValueAndTheOfficialGateClosing() throws IOException {
        AirportPack doha = load().find("DOH");

        assertThat(doha.zone().getId()).isEqualTo("Asia/Qatar");
        assertThat(doha.connectionRules).singleElement().satisfies(rule -> {
            assertThat(rule.type).isEqualTo(ConnectionType.INTL_INTL);
            assertThat(rule.minutes).isEqualTo(60);
            assertThat(rule.kind).isEqualTo(ValueKind.DEMO_VALUE);
            assertThat(rule.source).isEqualTo("DEMO VALUE - not an official figure");
        });
        assertThat(doha.gateClose.kind).isEqualTo(ValueKind.OFFICIAL);
        assertThat(doha.gateClose.minutes).isEqualTo(20);
        assertThat(doha.gateClose.sourceUrl).startsWith("https://dohahamadairport.com/");
    }

    @Test
    void airportsWithoutAnOfficialFigureHaveNoRules() throws IOException {
        AirportRules rules = load();

        assertThat(rules.find("DEL").connectionRules).isEmpty();
        assertThat(rules.find("DXB").connectionRules).isEmpty();
    }

    @Test
    void theChecksCatchBrokenEntries() throws IOException {
        AirportRules rules = load();
        AirportPack doha = rules.find("DOH");
        doha.connectionRules.get(0).source = "Team guess"; // a demo value that does not say so
        doha.gateClose.sourceUrl = null;                  // an official value without its page
        rules.find("LHR").timeZone = "Europe/Atlantis";
        NearbyAirport nowhere = new NearbyAirport();
        nowhere.iata = "XXX";
        nowhere.groundTransferMinutes = 90;
        nowhere.source = "Test";
        rules.find("DEL").nearbyAirports.add(nowhere);

        assertThat(rules.problems()).containsExactlyInAnyOrder(
                "airport DOH rule INTL_INTL: a DEMO_VALUE's source must start with \"DEMO VALUE\"",
                "airport DOH gateClose: an OFFICIAL value needs the sourceUrl of the official page",
                "airport LHR: unknown time zone Europe/Atlantis",
                "airport DEL: nearby airport XXX is not in the file");
    }

    private static AirportRules load() throws IOException {
        try (InputStream in = AirportRulesFileTest.class.getClassLoader()
                .getResourceAsStream("mock-data/recovery/airport-rules.json")) {
            assertThat(in).as("airport-rules.json on the classpath").isNotNull();
            return JSON.readValue(in, AirportRules.class);
        }
    }
}
