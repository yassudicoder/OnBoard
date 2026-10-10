/**
 * Connections (Disruption Recovery, phase C1): one row per change of planes in a trip (a link between two flights),
 * checked with the shared connection checker from recovery-core and saved with its status and reason.
 *
 * <p>Working: the check after every disruption (inside {@code watcher.DisruptionService}'s transaction) and
 * {@code GET /api/trips/{id}/connections}. Data: {@code mock-data/recovery/airport-rules.json} (official values with
 * their source, one team-set DEMO VALUE for Doha, team estimates), read through {@link AirportRulesProvider}.
 * Future: a real minimum-connection-time feed would be another {@link AirportRulesProvider}.
 */
package com.safarsaathi.backend.connection;
