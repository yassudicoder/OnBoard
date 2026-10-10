/**
 * Demo data and demo controls for the live walkthrough.
 *
 * <p>Phase 1: {@code DemoTripSeeder} loads the two demo trips from {@code mock-data/demo-trips.json} into an empty
 * database at startup ({@code seedIfEmpty()}), and can wipe and reload them ({@code deleteAllAndReseed()}).
 *
 * <p>Phase 2: {@code DemoController} + {@code DemoService}. {@code POST /api/demo/trigger-delay} and
 * {@code /api/demo/trigger-cancel} fake a disruption through the mock flight status and run the ripple at once;
 * {@code POST /api/demo/reset} reloads the seed data. The page {@code /demo} (static/demo/index.html) has a button
 * for each.
 */
package com.safarsaathi.backend.demo;
