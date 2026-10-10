package com.safarsaathi.backend.ripple;

import static com.safarsaathi.backend.ripple.DemoTrips.BAGS;
import static com.safarsaathi.backend.ripple.DemoTrips.CAB;
import static com.safarsaathi.backend.ripple.DemoTrips.DELHI_LEH;
import static com.safarsaathi.backend.ripple.DemoTrips.DINNER;
import static com.safarsaathi.backend.ripple.DemoTrips.FLIGHT;
import static com.safarsaathi.backend.ripple.DemoTrips.HOTEL;
import static com.safarsaathi.backend.ripple.DemoTrips.INDIA;
import static com.safarsaathi.backend.ripple.DemoTrips.MEETING;
import static com.safarsaathi.backend.ripple.DemoTrips.NOW;
import static com.safarsaathi.backend.ripple.DemoTrips.PUNE_DELHI;
import static com.safarsaathi.backend.ripple.DemoTrips.TRIP_DAY;
import static com.safarsaathi.backend.ripple.DemoTrips.at;
import static com.safarsaathi.backend.ripple.DemoTrips.block;
import static com.safarsaathi.backend.ripple.DemoTrips.businessTripBlocks;
import static com.safarsaathi.backend.ripple.DemoTrips.businessTripLinks;
import static com.safarsaathi.backend.ripple.DemoTrips.find;
import static com.safarsaathi.backend.ripple.DemoTrips.holidayTripBlocks;
import static com.safarsaathi.backend.ripple.DemoTrips.holidayTripLinks;
import static com.safarsaathi.backend.ripple.DemoTrips.hotel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.Flex;

/**
 * Unit tests for the ripple engine (no Spring, no database). Most tests use the demo trips from
 * mock-data/demo-trips.json (see {@link DemoTrips}) with "now" = the evening before the trip.
 *
 * <p>Tip: {@link #summary} turns a result into one short line per block, e.g. "2 AUTO_ADJUSTED 11:15-11:35 late 90"
 * (block id, status, new times if it moved, minutes late), so a whole ripple fits in one assertion.
 */
class RippleEngineTest {

    private final RippleEngine engine = new RippleEngine(INDIA);

    // ---- The six cases from CLAUDE.md 4.1 ----

    @Test
    void noDelayKeepsEverythingAsPlanned() {
        RippleResult result = delayBusinessFlight(0, NOW);

        assertThat(summary(result)).containsExactly(
                "1 OK 08:00-09:45 late 0",
                "2 OK late 0",
                "3 OK late 0",
                "4 OK late 0",
                "5 OK late 0",
                "6 OK late 0");
        assertThat(result.brokenLinks()).isEmpty();
    }

    @Test
    void smallDelayMovesBagsAndCabForFree() {
        RippleResult result = delayBusinessFlight(20, NOW);

        assertThat(summary(result)).containsExactly(
                "1 OK 08:20-10:05 late 20",
                "2 AUTO_ADJUSTED 10:05-10:25 late 20",
                "3 AUTO_ADJUSTED 10:25-10:25 late 20",
                "4 OK late 0", // the cab still reaches the office at 11:40, before the 12:00 meeting
                "5 OK late 0",
                "6 OK late 0");
        assertThat(result.brokenLinks()).isEmpty();
        assertThat(result.outcomeFor(CAB).orElseThrow().reason())
                .isEqualTo("Moved from 10:05 to 10:25, free of charge, to fit the new plan.");
    }

    /** The demo story: flight +90 minutes. This is the exact expectation in the Phase 2 contract. */
    @Test
    void mediumDelayLeavesTheMeetingToTheTraveller() {
        RippleResult result = delayBusinessFlight(90, NOW);

        assertThat(result.changedBlockId()).isEqualTo(FLIGHT);
        assertThat(summary(result)).containsExactly(
                "1 OK 09:30-11:15 late 90",
                "2 AUTO_ADJUSTED 11:15-11:35 late 90",
                "3 AUTO_ADJUSTED 11:35-11:35 late 90",
                "4 NEEDS_USER late 50",  // the anchor: never moved automatically
                "5 OK late 30",          // arrives 14:30 instead of 14:00: fine, check-in is a window
                "6 OK late 0");
        assertThat(result.brokenLinks()).containsExactly(new BrokenLink(CAB, MEETING, 50));

        assertThat(result.outcomeFor(FLIGHT).orElseThrow().reason())
                .isEqualTo("Now 09:30 to 11:15, 90 minutes later than planned.");
        assertThat(result.outcomeFor(MEETING).orElseThrow().reason())
                .isEqualTo("The earliest you can make it is 12:50, 50 minutes late. "
                        + "This is the main reason for your trip, so you decide.");
        assertThat(result.outcomeFor(HOTEL).orElseThrow().reason())
                .isEqualTo("You now arrive around 14:30. Check-in is open until 23:00, so nothing changes.");
    }

    @Test
    void bigDelayMissesTheAnchorAndTheHotelGetsALateArrivalNote() {
        RippleResult result = delayBusinessFlight(630, NOW); // 10 h 30 min

        assertThat(summary(result)).containsExactly(
                "1 OK 18:30-20:15 late 630",
                "2 AUTO_ADJUSTED 20:15-20:35 late 630",
                "3 AUTO_ADJUSTED 20:35-20:35 late 630",
                "4 NEEDS_USER late 590",
                // the meeting would end at 22:50, + 40 minutes to the hotel = 23:30: after 23:00, before midnight
                "5 AUTO_ADJUSTED 23:30-23:45 late 570",
                // the dinner is AUTO_SHIFT and its free-change window (until 18:00) is still open, so it moves too
                "6 AUTO_ADJUSTED 12 Oct 00:05-12 Oct 01:35 late 245");
        assertThat(result.brokenLinks()).containsExactly(new BrokenLink(CAB, MEETING, 590));
        assertThat(result.outcomeFor(HOTEL).orElseThrow().reason())
                .isEqualTo("You now arrive at 23:30, after 23:00. "
                        + "Check-in moved to then, with a late-arrival note for the hotel.");
    }

    @Test
    void cancelledFlightLeavesEveryLaterBlockToTheTraveller() {
        RippleResult result = engine.run(businessTripBlocks(), businessTripLinks(), RippleChange.cancel(FLIGHT), NOW);

        assertThat(summary(result)).containsExactly(
                "1 CANCELLED late 0",
                "2 NEEDS_USER late 0",
                "3 NEEDS_USER late 0",
                "4 NEEDS_USER late 0",
                "5 NEEDS_USER late 0",
                "6 NEEDS_USER late 0");
        assertThat(result.outcomes().subList(1, 6)).allSatisfy(outcome -> assertThat(outcome.reason())
                .isEqualTo("Depends on a cancelled booking earlier in the trip, so you decide."));
        assertThat(result.brokenLinks()).isEmpty(); // broken links are only for timing problems
    }

    @Test
    void missedConnectionNeedsTheTravellerAndShowsTheBrokenLink() {
        RippleResult result = delayHolidayFlight(60);

        assertThat(summary(result)).containsExactly(
                "7 OK 07:10-09:20 late 60",
                "8 NEEDS_USER late 20",               // 09:20 + 75 min connection = 10:35, the flight leaves 10:15
                "9 AUTO_ADJUSTED 12:00-12:20 late 20", // knock-on: judged as if the Leh flight lands 20 min late
                "10 AUTO_ADJUSTED 12:20-12:20 late 20",
                "11 OK late 0");                       // 12:20 + 20 min ride = 12:40, before the 13:00 check-in
        assertThat(result.brokenLinks()).containsExactly(new BrokenLink(PUNE_DELHI, DELHI_LEH, 20));
        assertThat(result.outcomeFor(DELHI_LEH).orElseThrow().reason())
                .isEqualTo("The earliest you can make it is 10:35, 20 minutes late. "
                        + "It cannot be moved automatically, so you decide.");
    }

    // ---- More rules from the contract ----

    /** The Delhi connection has 40 minutes of spare time on top of the 75-minute minimum. */
    @ParameterizedTest
    @CsvSource({
            "40, OK,         0, 0",
            "41, NEEDS_USER, 1, 1"
    })
    void connectionBreaksOnlyWhenTheMinimumConnectionTimeIsNoLongerThere(
            int delayMinutes, BlockStatus expectedStatus, int expectedMinutesLate, int expectedBrokenLinks) {
        RippleResult result = delayHolidayFlight(delayMinutes);

        BlockOutcome connection = result.outcomeFor(DELHI_LEH).orElseThrow();
        assertThat(connection.status()).isEqualTo(expectedStatus);
        assertThat(connection.minutesLate()).isEqualTo(expectedMinutesLate);
        assertThat(result.brokenLinks()).hasSize(expectedBrokenLinks);
    }

    /** Flight lands 13:00 + delay, 60 minutes to the hotel, check-in planned 14:00 (so arrival = 14:00 + delay). */
    @ParameterizedTest
    @CsvSource({
            "0,   OK,            ",      // arrives 14:00: on time
            "540, OK,            ",      // arrives 23:00: still fine
            "541, AUTO_ADJUSTED, 23:01", // arrives 23:01: late-arrival note
            "599, AUTO_ADJUSTED, 23:59",
            "600, NEEDS_USER,    ",      // arrives 00:00, i.e. midnight: possible no-show
            "640, NEEDS_USER,    "
    })
    void hotelCheckInIsAWindowUntil23AndALateNoteUntilMidnight(
            int delayMinutes, BlockStatus expectedStatus, String expectedNewStart) {
        List<RippleBlock> blocks = List.of(
                block(1, "12:00", "13:00", Flex.FIXED, false, null),
                hotel(2, "14:00", "14:15", -1440));
        List<RippleLink> links = List.of(new RippleLink(1, 2, 0, 60));

        RippleResult result = engine.run(blocks, links, RippleChange.delay(blocks.get(0), delayMinutes), NOW);

        BlockOutcome hotel = result.outcomeFor(2).orElseThrow();
        assertThat(hotel.status()).isEqualTo(expectedStatus);
        assertThat(hotel.newStart()).isEqualTo(expectedNewStart == null ? null : at(expectedNewStart));
    }

    @Test
    void hotelArrivalAfterMidnightOnTheDemoTripNeedsTheTraveller() {
        RippleResult result = delayBusinessFlight(700, NOW); // the meeting would end at midnight

        assertThat(summary(result)).contains("4 NEEDS_USER late 660", "5 NEEDS_USER late 640");
        assertThat(result.brokenLinks()).containsExactly(
                new BrokenLink(CAB, MEETING, 660),
                new BrokenLink(MEETING, HOTEL, 640));
        assertThat(result.outcomeFor(HOTEL).orElseThrow().reason())
                .isEqualTo("You now arrive at 00:40 on 12 Oct, after midnight. "
                        + "The hotel may treat it as a no-show, so you decide.");
    }

    @Test
    void closedFreeChangeWindowMeansTheTravellerDecides() {
        // 09:40 on the trip day: the cab's free change ended at 09:35, the bags' window runs until 09:45.
        RippleResult result = delayBusinessFlight(90, at("09:40"));

        assertThat(summary(result)).containsExactly(
                "1 OK 09:30-11:15 late 90",
                "2 AUTO_ADJUSTED 11:15-11:35 late 90",
                "3 NEEDS_USER late 90",
                "4 NEEDS_USER late 50", // knock-on from the cab's would-be time, 11:35
                "5 OK late 30",
                "6 OK late 0");
        assertThat(result.brokenLinks()).containsExactly(
                new BrokenLink(BAGS, CAB, 90),
                new BrokenLink(CAB, MEETING, 50));
        assertThat(result.outcomeFor(CAB).orElseThrow().reason())
                .isEqualTo("The earliest you can make it is 11:35, 90 minutes late. "
                        + "The free-change time is over, so you decide.");
    }

    /** Block 2 must start 90 minutes later. Only an AUTO_SHIFT, non-anchor block with an open window moves. */
    @ParameterizedTest
    @CsvSource({
            "AUTO_SHIFT, false, -30, AUTO_ADJUSTED",
            "AUTO_SHIFT, true,  -30, NEEDS_USER", // the anchor is never moved automatically
            "AUTO_SHIFT, false,    , NEEDS_USER", // no free-change window at all
            "FIXED,      false, -30, NEEDS_USER",
            "ASK_USER,   false, -30, NEEDS_USER"
    })
    void onlyFreeAutoShiftBlocksMoveByThemselves(
            Flex flex, boolean anchor, Integer freeChangeUntilMinutes, BlockStatus expectedStatus) {
        List<RippleBlock> blocks = List.of(
                block(1, "10:00", "11:00", Flex.FIXED, false, null),
                block(2, "12:00", "13:00", flex, anchor, freeChangeUntilMinutes));
        List<RippleLink> links = List.of(new RippleLink(1, 2, 0, 30));

        RippleResult result = engine.run(blocks, links, RippleChange.delay(blocks.get(0), 120), NOW);

        BlockOutcome second = result.outcomeFor(2).orElseThrow();
        assertThat(second.status()).isEqualTo(expectedStatus);
        assertThat(second.minutesLate()).isEqualTo(90);
        assertThat(second.moved()).isEqualTo(expectedStatus == BlockStatus.AUTO_ADJUSTED);
    }

    @Test
    void blocksBeforeTheChangeAreNotListedAndKeepTheirStatus() {
        RippleResult result = engine.run(businessTripBlocks(), businessTripLinks(), RippleChange.cancel(CAB), NOW);

        assertThat(summary(result)).containsExactly(
                "3 CANCELLED late 0",
                "4 NEEDS_USER late 0",
                "5 NEEDS_USER late 0",
                "6 NEEDS_USER late 0");
        assertThat(result.outcomeFor(FLIGHT)).isEmpty();
        assertThat(result.outcomeFor(BAGS)).isEmpty();
    }

    /**
     * Block 4 waits for two blocks: it must wait for the later of the two. Block 3 starts before block 2, so it
     * comes first in the trip order even though its id is higher. Block 5 comes before the change: not listed.
     */
    @Test
    void blockWithTwoIncomingLinksWaitsForTheLatestOne() {
        List<RippleBlock> blocks = List.of(
                block(1, "10:00", "11:00", Flex.FIXED, false, null),      // the block that changes
                block(2, "12:00", "12:30", Flex.AUTO_SHIFT, false, 0),
                block(3, "11:30", "12:00", Flex.FIXED, false, null),
                block(4, "13:00", "14:00", Flex.ASK_USER, false, null),
                block(5, "09:00", "09:30", Flex.FIXED, false, null));
        List<RippleLink> links = List.of(
                new RippleLink(5, 1, 0, 0),
                new RippleLink(1, 2, 0, 15),
                new RippleLink(1, 3, 0, 0),
                new RippleLink(2, 4, 0, 10),  // block 2 moves to 12:15-12:45, so 4 could start 12:55: fine
                new RippleLink(3, 4, 0, 45)); // block 3 would end 12:30, so 4 can start 13:15 at the earliest

        RippleResult result = engine.run(blocks, links, RippleChange.delay(blocks.get(0), 60), NOW);

        assertThat(summary(result)).containsExactly(
                "1 OK 11:00-12:00 late 60",
                "3 NEEDS_USER late 30",
                "2 AUTO_ADJUSTED 12:15-12:45 late 15",
                "4 NEEDS_USER late 15");
        assertThat(result.brokenLinks()).containsExactly(
                new BrokenLink(1, 3, 30),
                new BrokenLink(3, 4, 15)); // not 2 -> 4: that link still works
    }

    @Test
    void linksThatGoRoundInALoopAreRejected() {
        List<RippleBlock> blocks = List.of(
                block(1, "10:00", "11:00", Flex.FIXED, false, null),
                block(2, "12:00", "13:00", Flex.AUTO_SHIFT, false, 0));
        List<RippleLink> loop = List.of(new RippleLink(1, 2, 0, 0), new RippleLink(2, 1, 0, 0));
        List<RippleLink> selfLoop = List.of(new RippleLink(1, 1, 0, 0));
        RippleChange change = RippleChange.delay(blocks.get(0), 30);

        assertThatThrownBy(() -> engine.run(blocks, loop, change, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loop");
        assertThatThrownBy(() -> engine.run(blocks, selfLoop, change, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loop");
    }

    @Test
    void unknownBlocksAndDuplicateIdsAreRejected() {
        List<RippleBlock> blocks = businessTripBlocks();
        List<RippleLink> links = businessTripLinks();

        assertThatThrownBy(() -> engine.run(blocks, links, RippleChange.cancel(99), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Block 99 is not part of this trip");

        List<RippleLink> badLinks = new ArrayList<>(links);
        badLinks.add(new RippleLink(FLIGHT, 99, 0, 0));
        assertThatThrownBy(() -> engine.run(blocks, badLinks, RippleChange.cancel(FLIGHT), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not part of this trip");

        List<RippleBlock> twice = new ArrayList<>(blocks);
        twice.add(find(blocks, DINNER));
        assertThatThrownBy(() -> engine.run(twice, links, RippleChange.cancel(FLIGHT), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Block 6 is listed twice");
    }

    @Test
    void sameInputAlwaysGivesTheSameResultWhateverTheListOrder() {
        RippleChange change = RippleChange.delay(find(businessTripBlocks(), FLIGHT), 90);
        RippleResult first = engine.run(businessTripBlocks(), businessTripLinks(), change, NOW);
        RippleResult again = engine.run(businessTripBlocks(), businessTripLinks(), change, NOW);
        RippleResult reversed = engine.run(reversed(businessTripBlocks()), reversed(businessTripLinks()), change, NOW);

        assertThat(again).isEqualTo(first);
        assertThat(reversed).isEqualTo(first);
    }

    @Test
    void twoBrokenLinksIntoOneBlockComeInTheSameOrderWhateverTheListOrder() {
        List<RippleBlock> blocks = List.of(
                block(1, "10:00", "11:00", Flex.FIXED, false, null),
                block(2, "10:30", "11:00", Flex.FIXED, false, null),
                block(3, "11:30", "12:00", Flex.FIXED, false, null)); // waits for both 1 and 2
        List<RippleLink> links = List.of(
                new RippleLink(1, 2, 0, 0),
                new RippleLink(1, 3, 0, 30),
                new RippleLink(2, 3, 0, 30));
        RippleChange change = RippleChange.delay(blocks.get(0), 60);

        RippleResult first = engine.run(blocks, links, change, NOW);
        RippleResult reversed = engine.run(reversed(blocks), reversed(links), change, NOW);

        assertThat(first.brokenLinks()).containsExactly(
                new BrokenLink(1, 2, 90),
                new BrokenLink(1, 3, 60),
                new BrokenLink(2, 3, 90));
        assertThat(reversed).isEqualTo(first);
    }

    // ---- Time zones (Disruption Recovery): messages in each airport's local time ----

    @Test
    void messagesShowEachTimeInItsOwnAirportsLocalTime() {
        // Delhi 17:50 (+05:30) to Doha 19:35 (+03:00), then Doha 21:55 to London 03:10 next day (+01:00), 60 minutes
        // to change planes in Doha. 95 minutes late: leaves Delhi 19:25 Delhi time, lands 21:10 Doha time.
        RippleResult result = engine.run(dohaTrip(), List.of(new RippleLink(1, 2, 60, 0)),
                RippleChange.delay(dohaTrip().get(0), 95), NOW);

        assertThat(result.outcomeFor(1).orElseThrow().reason())
                .isEqualTo("Now 19:25 to 21:10, 95 minutes later than planned.");
        assertThat(result.outcomeFor(2).orElseThrow().reason())
                .isEqualTo("The earliest you can make it is 22:10, 15 minutes late. It cannot be moved automatically, "
                        + "so you decide.");
        assertThat(result.brokenLinks()).containsExactly(new BrokenLink(1, 2, 15));
    }

    @Test
    void anArrivalOnTheNextLocalDayShowsItsDate() {
        RippleResult result = engine.run(dohaTrip(), List.of(new RippleLink(1, 2, 60, 0)),
                RippleChange.delay(dohaTrip().get(1), 30), NOW);

        // Leaves Doha 22:25 on 11 Oct (Doha time), lands 03:40 on 12 Oct (London time).
        assertThat(result.outcomeFor(2).orElseThrow().reason())
                .isEqualTo("Now 22:25 to 03:40 on 12 Oct, 30 minutes later than planned.");
    }

    /** Two flights with their airports' zones (ids 1 and 2). */
    private static List<RippleBlock> dohaTrip() {
        ZoneId qatar = ZoneId.of("Asia/Qatar");
        return List.of(
                new RippleBlock(1, false, local("2026-10-11T17:50+05:30"), local("2026-10-11T19:35+03:00"),
                        Flex.FIXED, false, null, BlockStatus.OK, INDIA, qatar),
                new RippleBlock(2, false, local("2026-10-11T21:55+03:00"), local("2026-10-12T03:10+01:00"),
                        Flex.FIXED, false, null, BlockStatus.OK, qatar, ZoneId.of("Europe/London")));
    }

    private static Instant local(String timeWithOffset) {
        return OffsetDateTime.parse(timeWithOffset).toInstant();
    }

    // ---- Helpers ----

    private RippleResult delayBusinessFlight(int minutes, Instant now) {
        RippleChange change = RippleChange.delay(find(businessTripBlocks(), FLIGHT), minutes);
        return engine.run(businessTripBlocks(), businessTripLinks(), change, now);
    }

    private RippleResult delayHolidayFlight(int minutes) {
        RippleChange change = RippleChange.delay(find(holidayTripBlocks(), PUNE_DELHI), minutes);
        return engine.run(holidayTripBlocks(), holidayTripLinks(), change, NOW);
    }

    /** A copy of the list in the opposite order. */
    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    /** One line per outcome: "id STATUS [newStart-newEnd] late N". */
    private static List<String> summary(RippleResult result) {
        return result.outcomes().stream()
                .map(outcome -> outcome.blockId() + " " + outcome.status()
                        + (outcome.moved() ? " " + clock(outcome.newStart()) + "-" + clock(outcome.newEnd()) : "")
                        + " late " + outcome.minutesLate())
                .toList();
    }

    /** "11:15" on the trip day, "12 Oct 00:05" on another day (Indian time). */
    private static String clock(Instant instant) {
        ZonedDateTime local = instant.atZone(INDIA);
        String pattern = local.toLocalDate().equals(TRIP_DAY) ? "HH:mm" : "d MMM HH:mm";
        return local.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }
}
