/**
 * Trips and their parts: {@code Trip}, {@code Block} (one booking or event with a time, e.g. a flight or a cab),
 * {@code Link} ("B starts at least N minutes after A ends"), their enums and repositories, and the
 * {@code /api/trips} endpoints.
 *
 * <p>Phase 1: JPA entities + Flyway migration ({@code db/migration/V1__create_trips_blocks_links.sql}) and
 * {@code GET /api/trips/{id}} ({@code TripController} -> {@code TripService} -> {@code TripMapper}, which turns
 * entities into the JSON records and every time into Indian time, +05:30).
 * The demo trips are loaded by {@code com.safarsaathi.backend.demo.DemoTripSeeder}.
 *
 * <p>Phase 2: {@code DisruptionEvent} (table disruption_events, V2 migration) records every delay or cancellation
 * that {@code watcher.DisruptionService} applied.
 */
package com.safarsaathi.backend.trip;
