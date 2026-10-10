package com.safarsaathi.app.ui.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;

import org.junit.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link TripRowBuilder}, using demo trip 1 from mock-data/demo-trips.json
 * (trip day 11 Oct 2026, times in Indian time).
 */
public class TripRowBuilderTest {

    private static BlockEntity block(long id, String type, String day, String start, String end, String status) {
        BlockEntity block = new BlockEntity();
        block.id = id;
        block.tripId = 1;
        block.type = type;
        block.title = type + " " + id;
        block.startTime = OffsetDateTime.parse(day + "T" + start + ":00+05:30").toInstant();
        block.endTime = OffsetDateTime.parse(day + "T" + end + ":00+05:30").toInstant();
        block.status = status;
        return block;
    }

    private static LinkEntity link(long id, long from, long to, int minGap, int travel, String mode) {
        LinkEntity link = new LinkEntity();
        link.id = id;
        link.tripId = 1;
        link.fromBlockId = from;
        link.toBlockId = to;
        link.minGapMinutes = minGap;
        link.travelMinutes = travel;
        link.travelMode = mode;
        return link;
    }

    private static final String DAY = "2026-10-11";

    /** Demo trip 1: flight, bags, cab, meeting (anchor), hotel, dinner, with the 5 links from the seed file. */
    private static List<BlockEntity> demoBlocks() {
        return Arrays.asList(
                block(1, "FLIGHT", DAY, "08:00", "09:45", "OK"),
                block(2, "BAG_CLAIM", DAY, "09:45", "10:05", "OK"),
                block(3, "CAB", DAY, "10:05", "10:05", "OK"),
                block(4, "MEETING", DAY, "12:00", "13:00", "OK"),
                block(5, "HOTEL", DAY, "14:00", "14:15", "OK"),
                block(6, "RESTAURANT", DAY, "20:00", "21:30", "OK"));
    }

    private static List<LinkEntity> demoLinks() {
        return Arrays.asList(
                link(1, 1, 2, 0, 0, "NONE"),
                link(2, 2, 3, 0, 0, "WALK"),
                link(3, 3, 4, 0, 75, "CAB"),
                link(4, 4, 5, 0, 40, "CAB"),
                link(5, 5, 6, 0, 20, "CAB"));
    }

    private static List<String> keys(List<TripRow> rows) {
        List<String> keys = new ArrayList<>();
        for (TripRow row : rows) {
            keys.add(row.key());
        }
        return keys;
    }

    @Test
    public void demoTrip1_oneDayHeader_blocksInOrder_linkRowsOnlyWhereTimeIsNeeded() {
        List<TripRow> rows = TripRowBuilder.build(demoBlocks(), demoLinks());

        // flight -> bags and bags -> cab take 0 minutes, so they get no link row.
        assertEquals(Arrays.asList(
                "day:2026-10-11",
                "block:1", "block:2", "block:3",
                "link:3", "block:4",
                "link:4", "block:5",
                "link:5", "block:6"), keys(rows));
        assertEquals(LocalDate.of(2026, 10, 11), rows.get(0).day);
    }

    @Test
    public void blockRows_carryTheirIndianTimes() {
        List<TripRow> rows = TripRowBuilder.build(demoBlocks(), demoLinks());

        assertEquals("08:00–09:45", rows.get(1).timeText); // flight
        assertEquals("10:05", rows.get(3).timeText);            // cab pickup: one moment
    }

    @Test
    public void unsortedBlocks_areShownByStartTimeThenId() {
        List<BlockEntity> shuffled = new ArrayList<>(demoBlocks());
        Collections.reverse(shuffled);
        // Two blocks starting at the same moment: the lower id comes first.
        shuffled.add(block(9, "OTHER", DAY, "08:00", "08:30", "OK"));

        List<TripRow> rows = TripRowBuilder.build(shuffled, Collections.emptyList());

        assertEquals(Arrays.asList("day:2026-10-11", "block:1", "block:9", "block:2", "block:3", "block:4",
                "block:5", "block:6"), keys(rows));
    }

    @Test
    public void blocksOnTwoDays_getTwoDayHeaders_andTheLinkRowStaysAfterItsFromBlock() {
        List<BlockEntity> blocks = Arrays.asList(
                block(1, "HOTEL", DAY, "21:00", "21:15", "OK"),
                block(2, "CAB", "2026-10-12", "09:00", "09:00", "OK"));
        List<LinkEntity> links = Collections.singletonList(link(7, 1, 2, 0, 30, "CAB"));

        List<TripRow> rows = TripRowBuilder.build(blocks, links);

        assertEquals(Arrays.asList("day:2026-10-11", "block:1", "link:7", "day:2026-10-12", "block:2"),
                keys(rows));
    }

    @Test
    public void connectionBetweenTwoFlights_isMarkedAsAPlaneChange() {
        // Demo trip 2: Pune -> Delhi, then Delhi -> Leh with at least 75 minutes to change planes.
        List<BlockEntity> blocks = Arrays.asList(
                block(7, "FLIGHT", DAY, "06:10", "08:20", "OK"),
                block(8, "FLIGHT", DAY, "10:15", "11:40", "OK"),
                block(9, "BAG_CLAIM", DAY, "11:40", "12:00", "OK"));
        List<LinkEntity> links = Arrays.asList(link(6, 7, 8, 75, 0, "WALK"), link(7, 8, 9, 5, 0, "NONE"));

        List<TripRow> rows = TripRowBuilder.build(blocks, links);

        TripRow connection = rows.get(2);
        assertEquals("link:6", connection.key());
        assertTrue(connection.planeChange);
        assertEquals(75, connection.link.minGapMinutes);
        TripRow toBags = rows.get(4);
        assertEquals("link:7", toBags.key());
        assertFalse("flight -> bag claim is not a plane change", toBags.planeChange);
    }

    @Test
    public void linksBetweenBlocksThatAreNotNeighbours_orToMissingBlocks_addNoRow() {
        List<LinkEntity> links = Arrays.asList(
                link(10, 1, 4, 0, 90, "CAB"),   // flight -> meeting: not next to each other on the list
                link(11, 6, 99, 0, 15, "WALK")); // block 99 does not exist (e.g. mid-way through a refresh)

        List<TripRow> rows = TripRowBuilder.build(demoBlocks(), links);

        for (TripRow row : rows) {
            assertTrue(row.key(), row.kind != TripRow.Kind.LINK);
        }
    }

    @Test
    public void needingDecision_listsOnlyNeedsUserBlocks_inTimeOrder() {
        List<BlockEntity> blocks = new ArrayList<>(demoBlocks());
        blocks.get(4).status = "NEEDS_USER"; // hotel 14:00
        blocks.get(3).status = "NEEDS_USER"; // meeting 12:00
        blocks.get(2).status = "AUTO_ADJUSTED";
        Collections.reverse(blocks);

        List<BlockEntity> needDecision = TripRowBuilder.needingDecision(blocks);

        assertEquals(2, needDecision.size());
        assertEquals(4, needDecision.get(0).id);
        assertEquals(5, needDecision.get(1).id);
    }

    @Test
    public void noBlocks_noRows() {
        assertTrue(TripRowBuilder.build(Collections.emptyList(), demoLinks()).isEmpty());
    }
}
