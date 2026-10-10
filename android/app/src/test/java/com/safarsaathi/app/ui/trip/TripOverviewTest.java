package com.safarsaathi.app.ui.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.local.BlockEntity;

import org.junit.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unit tests for the Home tab's "trip at a glance" rules ({@link TripOverview}), on demo trip 1. */
public class TripOverviewTest {

    /** The evening before the trip day (11 Oct 2026), in India. */
    private static final Instant NIGHT_BEFORE = at("2026-10-10", "22:00");

    private static Instant at(String day, String time) {
        return OffsetDateTime.parse(day + "T" + time + ":00+05:30").toInstant();
    }

    private static BlockEntity block(long id, String type, String start, String end) {
        BlockEntity block = new BlockEntity();
        block.id = id;
        block.tripId = 1;
        block.type = type;
        block.title = type + " " + id;
        block.startTime = at("2026-10-11", start);
        block.endTime = at("2026-10-11", end);
        block.plannedStartTime = block.startTime;
        block.plannedEndTime = block.endTime;
        block.status = BlockEntity.STATUS_OK;
        return block;
    }

    /** Demo trip 1 as seeded: flight 08:00, bags, cab, meeting (anchor), hotel, dinner. All OK. */
    private static List<BlockEntity> demoTrip() {
        List<BlockEntity> blocks = new ArrayList<>();
        blocks.add(block(1, "FLIGHT", "08:00", "09:45"));
        blocks.add(block(2, "BAG_CLAIM", "09:45", "10:05"));
        blocks.add(block(3, "CAB", "10:05", "10:05"));
        blocks.add(block(4, "MEETING", "12:00", "13:00"));
        blocks.add(block(5, "HOTEL", "14:00", "14:15"));
        blocks.add(block(6, "RESTAURANT", "20:00", "21:30"));
        return blocks;
    }

    /** Moves a block by some minutes, keeping its planned times (what a delay does on the server). */
    private static void move(BlockEntity block, int minutes) {
        block.startTime = block.startTime.plusSeconds(minutes * 60L);
        block.endTime = block.endTime.plusSeconds(minutes * 60L);
    }

    @Test
    public void seededTrip_isOnTrack_withTheFlightAsNextStep() {
        List<BlockEntity> blocks = demoTrip();

        TripOverview overview = TripOverview.of(blocks, NIGHT_BEFORE);

        assertEquals(TripOverview.State.ON_TRACK, overview.state);
        assertSame(blocks.get(0), overview.flight);
        assertSame(blocks.get(0), overview.nextUp);
        assertTrue(overview.needingDecision.isEmpty());
        assertTrue(overview.changed.isEmpty());
        assertEquals(BlockEntity.STATUS_OK, TripOverview.displayStatus(blocks.get(0)));
    }

    @Test
    public void delayWithAutoShiftedBookings_isChanged_andTheFlightShowsDelayed() {
        List<BlockEntity> blocks = demoTrip();
        move(blocks.get(0), 30);                                  // flight 08:30, still OK on the server
        move(blocks.get(1), 30);
        blocks.get(1).status = BlockEntity.STATUS_AUTO_ADJUSTED;  // bags
        move(blocks.get(2), 30);
        blocks.get(2).status = BlockEntity.STATUS_AUTO_ADJUSTED;  // cab

        TripOverview overview = TripOverview.of(blocks, NIGHT_BEFORE);

        assertEquals(TripOverview.State.CHANGED, overview.state);
        assertEquals(30, TripOverview.minutesMoved(blocks.get(0)));
        assertEquals(TripOverview.DISPLAY_DELAYED, TripOverview.displayStatus(blocks.get(0)));
        assertEquals(List.of(blocks.get(0), blocks.get(1), blocks.get(2)), overview.changed);
        assertEquals(2, overview.autoAdjustedCount);
    }

    @Test
    public void meetingThatNeedsTheTraveller_winsOverEverythingElse() {
        List<BlockEntity> blocks = demoTrip();
        move(blocks.get(0), 120);
        blocks.get(3).status = BlockEntity.STATUS_NEEDS_USER;

        TripOverview overview = TripOverview.of(blocks, NIGHT_BEFORE);

        assertEquals(TripOverview.State.NEEDS_DECISION, overview.state);
        assertEquals(List.of(blocks.get(3)), overview.needingDecision);
        // A block that needs a decision is listed there only, not again under "changed".
        assertEquals(List.of(blocks.get(0)), overview.changed);
        assertEquals(BlockEntity.STATUS_NEEDS_USER, TripOverview.displayStatus(blocks.get(3)));
    }

    @Test
    public void unknownOriginalPlan_isNeverReportedAsADelay() {
        List<BlockEntity> blocks = demoTrip();
        move(blocks.get(0), 30);
        blocks.get(0).plannedStartTime = null; // the phone first saw this trip after it changed
        blocks.get(0).plannedEndTime = null;

        TripOverview overview = TripOverview.of(blocks, NIGHT_BEFORE);

        assertEquals(0, TripOverview.minutesMoved(blocks.get(0)));
        assertEquals(BlockEntity.STATUS_OK, TripOverview.displayStatus(blocks.get(0)));
        assertEquals(TripOverview.State.ON_TRACK, overview.state);
    }

    @Test
    public void cancelledFlight_isChanged_andNextStepSkipsIt() {
        List<BlockEntity> blocks = demoTrip();
        blocks.get(0).status = BlockEntity.STATUS_CANCELLED;

        TripOverview overview = TripOverview.of(blocks, NIGHT_BEFORE);

        assertEquals(TripOverview.State.CHANGED, overview.state);
        assertEquals(1, overview.cancelledCount);
        assertSame(blocks.get(1), overview.nextUp);
    }

    @Test
    public void nextStep_skipsWhatIsOverOrDone() {
        List<BlockEntity> blocks = demoTrip();
        blocks.get(0).status = BlockEntity.STATUS_DONE;

        TripOverview overview = TripOverview.of(blocks, at("2026-10-11", "10:30"));

        assertSame(blocks.get(3), overview.nextUp); // bags and cab are over by 10:30; the meeting is next
    }

    @Test
    public void blocksInAnyOrder_areReadInTimeOrder() {
        List<BlockEntity> blocks = demoTrip();
        List<BlockEntity> shuffled = new ArrayList<>(blocks);
        Collections.reverse(shuffled);

        TripOverview overview = TripOverview.of(shuffled, NIGHT_BEFORE);

        assertSame(blocks.get(0), overview.flight);
        assertSame(blocks.get(0), overview.nextUp);
    }

    @Test
    public void tripWithoutBookings() {
        TripOverview overview = TripOverview.of(new ArrayList<>(), NIGHT_BEFORE);

        assertEquals(TripOverview.State.NO_BOOKINGS, overview.state);
        assertNull(overview.flight);
        assertNull(overview.nextUp);
    }
}
