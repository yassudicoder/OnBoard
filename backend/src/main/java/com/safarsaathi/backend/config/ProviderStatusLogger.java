package com.safarsaathi.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints one tidy block at startup that says which outside services are MOCK and which are REAL, and why.
 *
 * <p>{@code @EventListener} on {@link ApplicationReadyEvent}: Spring calls {@link #onReady} once, after the app
 * has fully started, so the block appears at the end of the startup log where it is easy to find.
 * Key values are never printed - only whether each key is set.
 */
@Component
public class ProviderStatusLogger {

    private static final Logger log = LoggerFactory.getLogger(ProviderStatusLogger.class);

    /** Column layout of the table: provider, service, mode, reason. */
    private static final String ROW = "  %-13s %-16s %-5s %s";

    private final AppProperties properties;
    private final ProviderStatus providerStatus;

    public ProviderStatusLogger(AppProperties properties, ProviderStatus providerStatus) {
        this.properties = properties;
        this.providerStatus = providerStatus;
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        // Spring Boot stores the port the web server really listens on as "local.server.port".
        // It is missing in tests that use a mock web environment; then the "Try it" line is left out.
        String port = event.getApplicationContext().getEnvironment().getProperty("local.server.port");
        log.info(buildReport(port));
    }

    /** Builds the text of the block. Package-private so a unit test can check it without starting Spring. */
    String buildReport(String port) {
        StringBuilder report = new StringBuilder();
        report.append("Safar Saathi backend is ready. App mode: ").append(properties.mode())
                .append(", time zone: ").append(properties.zone());
        addLine(report, String.format(ROW, "PROVIDER", "SERVICE", "MODE", "WHY"));
        for (ProviderStatus.Provider provider : providerStatus.providers()) {
            addLine(report, String.format(ROW, provider.key(), provider.service(), provider.mode(), provider.reason()));
        }
        addLine(report, "  Gemini model: " + providerStatus.geminiModel() + " (used only when ai is REAL)");
        addLine(report, "  All MOCK is fine - the full demo works without any keys.");
        addLine(report, "  To make a service REAL, put its key in the root .env file (see .env.example) and restart.");
        if (port != null) {
            addLine(report, "  Try it: http://localhost:" + port + "/api/health");
        }
        return report.toString();
    }

    private static void addLine(StringBuilder report, String line) {
        report.append(System.lineSeparator()).append(line);
    }
}
