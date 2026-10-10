package com.safarsaathi.backend.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        this.rules = read(objectMapper);
        List<String> problems = rules.problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException(RULES_FILE + " breaks the rules for sourced data: "
                    + String.join("; ", problems));
        }
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

    private static AirportRules read(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RULES_FILE);
        if (!resource.exists()) {
            throw new IllegalStateException("classpath:" + RULES_FILE + " not found. Maven copies it from the "
                    + "repo-root mock-data folder (see pom.xml): rebuild with ./mvnw clean package");
        }
        try (InputStream in = resource.getInputStream()) {
            // FAIL_ON_UNKNOWN_PROPERTIES: a misspelt field name stops the app instead of leaving a value empty.
            return objectMapper.readerFor(AirportRules.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RULES_FILE + ": " + e.getMessage(), e);
        }
    }
}
