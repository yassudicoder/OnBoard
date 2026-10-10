package com.safarsaathi.backend.ripple;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.Flex;

/**
 * The two demo trips from mock-data/demo-trips.json as ripple-engine input: the same times, flex, anchors,
 * free-change windows and links, and the ids they get on an empty database (trip 1 = blocks 1-6, trip 2 = 7-11).
 * The trip day is a fixed date, so the tests never depend on today's date.
 */
final class DemoTrips {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    static final LocalDate TRIP_DAY = LocalDate.of(2026, 10, 11);

    /** "Now" for most tests: the evening before the trip, so every free-change window is still open. */
    static final Instant NOW = at(TRIP_DAY.minusDays(1), "21:00");

    // Trip 1, "Client visit: Mumbai to Bengaluru"
    static final long FLIGHT = 1;
    static final long BAGS = 2;
    static final long CAB = 3;
    static final long MEETING = 4;
    static final long HOTEL = 5;
    static final long DINNER = 6;

    // Trip 2, "Leh holiday via Delhi"
    static final long PUNE_DELHI = 7;
    static final long DELHI_LEH = 8;
    static final long LEH_BAGS = 9;
    static final long LEH_CAB = 10;
    static final long LEH_HOTEL = 11;

    private DemoTrips() {
    }

    static List<RippleBlock> businessTripBlocks() {
        return List.of(
                block(FLIGHT, "08:00", "09:45", Flex.FIXED, false, null),
                block(BAGS, "09:45", "10:05", Flex.AUTO_SHIFT, false, 0),
                block(CAB, "10:05", "10:05", Flex.AUTO_SHIFT, false, -30),
                block(MEETING, "12:00", "13:00", Flex.ASK_USER, true, null), // the anchor
                hotel(HOTEL, "14:00", "14:15", -1440),
                block(DINNER, "20:00", "21:30", Flex.AUTO_SHIFT, false, -120));
    }

    static List<RippleLink> businessTripLinks() {
        return List.of(
                new RippleLink(FLIGHT, BAGS, 0, 0),
                new RippleLink(BAGS, CAB, 0, 0),
                new RippleLink(CAB, MEETING, 0, 75),   // airport to the office
                new RippleLink(MEETING, HOTEL, 0, 40), // office to the hotel
                new RippleLink(HOTEL, DINNER, 0, 20));
    }

    static List<RippleBlock> holidayTripBlocks() {
        return List.of(
                block(PUNE_DELHI, "06:10", "08:20", Flex.FIXED, false, null),
                block(DELHI_LEH, "10:15", "11:40", Flex.FIXED, false, null),
                block(LEH_BAGS, "11:40", "12:00", Flex.AUTO_SHIFT, false, 0),
                block(LEH_CAB, "12:00", "12:00", Flex.AUTO_SHIFT, false, -30),
                hotel(LEH_HOTEL, "13:00", "13:15", -1440));
    }

    static List<RippleLink> holidayTripLinks() {
        return List.of(
                new RippleLink(PUNE_DELHI, DELHI_LEH, 75, 0), // 75-minute minimum connection time at Delhi
                new RippleLink(DELHI_LEH, LEH_BAGS, 0, 0),
                new RippleLink(LEH_BAGS, LEH_CAB, 0, 0),
                new RippleLink(LEH_CAB, LEH_HOTEL, 0, 20));
    }

    /** A time on the trip day in Indian time, e.g. {@code at("09:30")}. */
    static Instant at(String time) {
        return at(TRIP_DAY, time);
    }

    static Instant at(LocalDate day, String time) {
        return day.atTime(LocalTime.parse(time)).atZone(INDIA).toInstant();
    }

    /** A block on the trip day that is not a hotel, with status OK. */
    static RippleBlock block(long id, String start, String end, Flex flex, boolean anchor,
                             Integer freeChangeUntilMinutes) {
        return new RippleBlock(id, false, at(start), at(end), flex, anchor, freeChangeUntilMinutes, BlockStatus.OK);
    }

    /** A hotel check-in on the trip day (AUTO_SHIFT, like in the seed file), with status OK. */
    static RippleBlock hotel(long id, String start, String end, Integer freeChangeUntilMinutes) {
        return new RippleBlock(id, true, at(start), at(end), Flex.AUTO_SHIFT, false, freeChangeUntilMinutes,
                BlockStatus.OK);
    }

    /** One block of a list, by id. */
    static RippleBlock find(List<RippleBlock> blocks, long id) {
        return blocks.stream()
                .filter(block -> block.id() == id)
                .findFirst()
                .orElseThrow();
    }
}
