package com.safarsaathi.backend.planb;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.MockDataFile;
import com.safarsaathi.recovery.plan.RecoverySettings;

/**
 * The product choices of recovery plans (ranking weights, overnight threshold, search window), read once at startup
 * from {@code mock-data/recovery/recovery-settings.json} and checked with {@link RecoverySettings#problems()}.
 */
@Component
public class FileRecoverySettings {

    static final String SETTINGS_FILE = "mock-data/recovery/recovery-settings.json";

    private final RecoverySettings settings;

    public FileRecoverySettings(ObjectMapper objectMapper) {
        this.settings = MockDataFile.read(objectMapper, SETTINGS_FILE, RecoverySettings.class);
        MockDataFile.requireNoProblems(SETTINGS_FILE, settings.problems());
    }

    public RecoverySettings settings() {
        return settings;
    }
}
