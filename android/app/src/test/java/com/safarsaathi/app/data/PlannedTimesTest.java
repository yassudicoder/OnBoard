package com.safarsaathi.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Unit tests for the "original plan" rule ({@link PlannedTimes}): which times count as planned for each block. */
public class PlannedTimesTest {

    private static final Instant CREATED = Instant.parse("2026-10-10T12:00:05Z");
    private static final Instant CREATED_AGAIN = Instant.parse("2026-10-10T13:15:00Z"); // after /api/demo/reset
    private static final Instant EIGHT = Instant.parse("2026-10-11T02:30:00Z");          // 08:00 in India
    private static final Instant NINE_45 = Instant.parse("2026-10-11T04:15:00Z");        // 09:45 in India
    private static final Instant TEN = Instant.parse("2026-10-11T04:30:00Z");            // 10:00 in India
    private static final Instant ELEVEN_45 = Instant.parse("2026-10-11T06:15:00Z");      // 11:45 in India

    private static TripEntity trip(int version, Instant createdAt) {
        TripEntity trip = new TripEntity();
        trip.id = 1;
        trip.version = version;
        trip.createdAt = createdAt;
        return trip;
    }

    private static BlockEntity flight(Instant start, Instant end) {
        BlockEntity block = new BlockEntity();
        block.id = 1;
        block.tripId = 1;
        block.startTime = start;
        block.endTime = end;
        return block;
    }

    @Test
    public void firstCopyAtVersion1_itsTimesAreTheOriginalPlan() {
        BlockEntity fresh = flight(EIGHT, NINE_45);

        PlannedTimes.fill(null, Collections.emptyList(), trip(1, CREATED), List.of(fresh));

        assertEquals(EIGHT, fresh.plannedStartTime);
        assertEquals(NINE_45, fresh.plannedEndTime);
    }

    @Test
    public void firstCopyAfterTheTripChanged_originalPlanIsNotKnown() {
        BlockEntity fresh = flight(TEN, ELEVEN_45);

        PlannedTimes.fill(null, Collections.emptyList(), trip(3, CREATED), List.of(fresh));

        assertNull(fresh.plannedStartTime);
        assertNull(fresh.plannedEndTime);
    }

    @Test
    public void delayedCopyOfTheSameTrip_keepsTheOriginalPlan() {
        BlockEntity saved = flight(EIGHT, NINE_45);
        saved.plannedStartTime = EIGHT;
        saved.plannedEndTime = NINE_45;
        BlockEntity fresh = flight(TEN, ELEVEN_45); // delayed by 2 hours

        PlannedTimes.fill(trip(1, CREATED), List.of(saved), trip(2, CREATED), List.of(fresh));

        assertEquals(EIGHT, fresh.plannedStartTime);
        assertEquals(NINE_45, fresh.plannedEndTime);
    }

    @Test
    public void copySavedBeforeThisRuleAtVersion1_itsTimesBecomeTheOriginalPlan() {
        // The app update added the planned columns as null; that saved copy was still the original plan.
        BlockEntity saved = flight(EIGHT, NINE_45);
        BlockEntity fresh = flight(TEN, ELEVEN_45);

        PlannedTimes.fill(trip(1, CREATED), List.of(saved), trip(2, CREATED), List.of(fresh));

        assertEquals(EIGHT, fresh.plannedStartTime);
        assertEquals(NINE_45, fresh.plannedEndTime);
    }

    @Test
    public void copySavedBeforeThisRuleAfterAChange_staysUnknown() {
        BlockEntity saved = flight(TEN, ELEVEN_45);
        BlockEntity fresh = flight(TEN.plusSeconds(1800), ELEVEN_45.plusSeconds(1800));

        PlannedTimes.fill(trip(2, CREATED), List.of(saved), trip(3, CREATED), List.of(fresh));

        assertNull(fresh.plannedStartTime);
    }

    @Test
    public void demoReset_startsANewOriginalPlan() {
        BlockEntity saved = flight(TEN, ELEVEN_45);
        saved.plannedStartTime = EIGHT;
        saved.plannedEndTime = NINE_45;
        BlockEntity fresh = flight(EIGHT, NINE_45); // reloaded, version 1, new createdAt

        PlannedTimes.fill(trip(2, CREATED), List.of(saved), trip(1, CREATED_AGAIN), List.of(fresh));

        assertEquals(EIGHT, fresh.plannedStartTime);
        assertEquals(NINE_45, fresh.plannedEndTime);
    }

    @Test
    public void blockThatAppearsAfterTheTripChanged_isNotKnown() {
        BlockEntity saved = flight(EIGHT, NINE_45);
        saved.plannedStartTime = EIGHT;
        saved.plannedEndTime = NINE_45;
        BlockEntity newBlock = flight(TEN, ELEVEN_45);
        newBlock.id = 99;

        PlannedTimes.fill(trip(1, CREATED), List.of(saved), trip(2, CREATED), List.of(newBlock));

        assertNull(newBlock.plannedStartTime);
    }
}
