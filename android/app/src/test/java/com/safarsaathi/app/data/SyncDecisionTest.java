package com.safarsaathi.app.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.local.TripEntity;

import org.junit.Test;

import java.time.Instant;

/** Unit tests for the sync rule ({@link SyncDecision}): when does the server's copy replace the saved one? */
public class SyncDecisionTest {

    private static final Instant CREATED = Instant.parse("2026-10-10T12:00:05Z");
    private static final Instant CREATED_AGAIN = Instant.parse("2026-10-10T13:15:00Z"); // after /api/demo/reset

    private static TripEntity trip(int version, Instant createdAt) {
        TripEntity trip = new TripEntity();
        trip.id = 1;
        trip.version = version;
        trip.createdAt = createdAt;
        return trip;
    }

    @Test
    public void noSavedCopy_replaces() {
        assertTrue(SyncDecision.shouldReplace(null, trip(1, CREATED)));
    }

    @Test
    public void newerServerVersion_replaces() {
        assertTrue(SyncDecision.shouldReplace(trip(1, CREATED), trip(2, CREATED)));
    }

    @Test
    public void sameVersionAndSameCreatedAt_keepsTheSavedCopy() {
        assertFalse(SyncDecision.shouldReplace(trip(2, CREATED), trip(2, CREATED)));
    }

    @Test
    public void olderServerVersion_keepsTheSavedCopy() {
        assertFalse(SyncDecision.shouldReplace(trip(3, CREATED), trip(2, CREATED)));
    }

    @Test
    public void demoReset_differentCreatedAt_replacesEvenThoughVersionWentBackTo1() {
        // Before the reset the phone saw version 2 (after a delay). The reloaded trip is version 1 again.
        assertTrue(SyncDecision.shouldReplace(trip(2, CREATED), trip(1, CREATED_AGAIN)));
    }

    @Test
    public void demoReset_sameVersionDifferentCreatedAt_replaces() {
        assertTrue(SyncDecision.shouldReplace(trip(1, CREATED), trip(1, CREATED_AGAIN)));
    }
}
