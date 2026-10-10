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
        String address = event.getApplicationContext().getEnvironment().getProperty("server.address");
        log.info(buildReport(address, port));
    }

    /**
     * Builds the text of the block. Package-private so a unit test can check it without starting Spring.
     *
     * @param address server.address (SERVER_ADDRESS); null = every network, like 0.0.0.0
     * @param port    the port the server listens on; null in tests without a real server
     */
    String buildReport(String address, String port) {
        StringBuilder report = new StringBuilder();
        report.append("OnBoard backend is ready. App mode: ").append(properties.mode())
                .append(", time zone: ").append(properties.zone());
        addLine(report, "  " + whoCanReachIt(address));
        if (properties.demoControlsEnabled()) {
            addLine(report, "  Demo controls (POST /api/demo/...): ON, because APP_MODE=demo.");
            if (!isThisComputerOnly(address)) {
                addLine(report, "  WARNING: demo mode on an address other computers can reach: anyone on the network"
                        + " can delay, cancel and reset trips. Use SERVER_ADDRESS=127.0.0.1 or APP_MODE=prod.");
            }
        } else {
            addLine(report, "  Demo controls (POST /api/demo/...): OFF, because APP_MODE=prod (they answer 404).");
        }
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

    /** One line about who can reach the backend, from server.address. */
    private static String whoCanReachIt(String address) {
        if (isThisComputerOnly(address)) {
            return "Listening on " + address + ": this computer only (emulator: http://10.0.2.2:<port>, USB phone:"
                    + " adb reverse). Set SERVER_ADDRESS=0.0.0.0 to open it to other devices.";
        }
        return "Listening on " + (address == null ? "0.0.0.0" : address) + ": other devices on the network can"
                + " reach this backend.";
    }

    /** True for the loopback addresses, which only this computer can use. */
    static boolean isThisComputerOnly(String address) {
        return "127.0.0.1".equals(address) || "localhost".equalsIgnoreCase(address) || "::1".equals(address);
    }

    private static void addLine(StringBuilder report, String line) {
        report.append(System.lineSeparator()).append(line);
    }
}
