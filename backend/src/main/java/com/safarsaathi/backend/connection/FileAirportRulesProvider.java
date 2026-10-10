package com.safarsaathi.backend.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.MockDataFile;
import com.safarsaathi.recovery.rules.AirportRules;

/**
 * Reads {@code mock-data/recovery/airport-rules.json} once at startup (Maven copies the repo-root mock-data folder into
 * the jar, see pom.xml) and checks it with {@link AirportRules#problems()}. A broken file stops the app with a message
 * that names every problem, so a number without a source can never reach a traveller.
 */
@Component
public class FileAirportRulesProvider implements AirportRulesProvider {

    static final String RULES_FILE = "mock-data/recovery/airport-rules.json";

    private static final Logger log = LoggerFactory.getLogger(FileAirportRulesProvider.class);

    private final AirportRules rules;

    public FileAirportRulesProvider(ObjectMapper objectMapper) {
        this.rules = MockDataFile.read(objectMapper, RULES_FILE, AirportRules.class);
        MockDataFile.requireNoProblems(RULES_FILE, rules.problems());
        long withMinimum = rules.airports.stream().filter(airport -> !airport.connectionRules.isEmpty()).count();
        log.info("Airport rules: {} airports, {} with a minimum connection time (file {}, checked {})",
                rules.airports.size(), withMinimum, RULES_FILE, rules.lastChecked);
    }

    @Override
    public AirportRules rules() {
        return rules;
    }

    @Override
    public String name() {
        return "FileAirportRulesProvider (" + RULES_FILE + ")";
    }
}
