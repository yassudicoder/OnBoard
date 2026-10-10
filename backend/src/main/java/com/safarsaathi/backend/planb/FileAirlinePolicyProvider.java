package com.safarsaathi.backend.planb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.MockDataFile;
import com.safarsaathi.recovery.policy.AirlinePolicies;

/**
 * Reads {@code mock-data/recovery/airline-policies.json} once at startup and checks it with
 * {@link AirlinePolicies#problems()}: SAMPLE texts say so, fictional airlines have no phone numbers and only
 * {@code .example} addresses, official entries have their page. A broken file stops the app.
 */
@Component
public class FileAirlinePolicyProvider implements AirlinePolicyProvider {

    static final String POLICY_FILE = "mock-data/recovery/airline-policies.json";

    private static final Logger log = LoggerFactory.getLogger(FileAirlinePolicyProvider.class);

    private final AirlinePolicies policies;

    public FileAirlinePolicyProvider(ObjectMapper objectMapper) {
        this.policies = MockDataFile.read(objectMapper, POLICY_FILE, AirlinePolicies.class);
        MockDataFile.requireNoProblems(POLICY_FILE, policies.problems());
        long sample = policies.airlines.stream().filter(airline -> "SAMPLE".equals(airline.kind)).count();
        log.info("Airline policies: {} airlines ({} SAMPLE, fictional) from {}", policies.airlines.size(), sample,
                POLICY_FILE);
    }

    @Override
    public AirlinePolicies policies() {
        return policies;
    }

    @Override
    public String name() {
        return "FileAirlinePolicyProvider (" + POLICY_FILE + ")";
    }
}
