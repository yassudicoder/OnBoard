/**
 * The booking orchestrator ({@code BookingOrchestrator}): applies the chosen fix to every affected booking, scarcest
 * first (flight, hotel, meeting, cab, restaurant), logs every call and, if one change fails, rolls back the changes
 * already made, in reverse order. Built in Phase 4.
 */
package com.safarsaathi.backend.orchestrator;
