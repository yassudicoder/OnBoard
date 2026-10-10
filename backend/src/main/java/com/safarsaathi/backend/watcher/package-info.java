/**
 * The flight watcher and everything that applies a disruption to a trip (Phase 2).
 *
 * <ul>
 *   <li>{@code FlightWatcher}: a {@code @Scheduled} job, every 30 s in demo mode and every 5 min in prod mode, that
 *       asks a {@code FlightStatusProvider} about every FLIGHT block of every trip that is not DONE.</li>
 *   <li>{@code FlightStatusProvider}: {@code MockFlightStatusProvider} (the default; the demo buttons feed it) or
 *       {@code AeroDataBoxProvider} (real, only when AERODATABOX_API_KEY is set; any error = no change).</li>
 *   <li>{@code DisruptionService}: runs the ripple engine for one change and saves the new times and statuses, a
 *       {@code DisruptionEvent} and the trip's new version, all in one transaction.</li>
 * </ul>
 */
package com.safarsaathi.backend.watcher;
