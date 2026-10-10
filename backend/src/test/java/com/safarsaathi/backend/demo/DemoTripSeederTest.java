package com.safarsaathi.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.safarsaathi.backend.FixedClockConfig;
import com.safarsaathi.backend.connection.Connection;
import com.safarsaathi.backend.connection.ConnectionRepository;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.BlockType;
import com.safarsaathi.backend.trip.Link;
import com.safarsaathi.backend.trip.LinkRepository;
import com.safarsaathi.backend.trip.Trip;
import com.safarsaathi.backend.trip.TripRepository;
import com.safarsaathi.backend.trip.TripStatus;
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.FlightPhase;
import com.safarsaathi.recovery.connection.TicketType;

/**
 * The demo seed on a real (in-memory H2) database: Flyway creates the tables, and at startup the seeder saves the three
 * demo trips from mock-data/demo-trips.json. The clock is stopped at 10 Oct 2026 17:30:05 in India
 * ({@link FixedClockConfig}), so the trip day is 11 Oct 2026.
 *
 * <p>This test class gets its own empty database (see test-database.properties), so the ids start at 1.
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@Import(FixedClockConfig.class)
class DemoTripSeederTest {

    @Autowired
    private DemoTripSeeder seeder;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Test
    void startupSavesTheThreeDemoTripsWithIdsInFileOrder() {
        List<Trip> trips = tripRepository.findAll();
        assertThat(trips)
                .extracting(Trip::getId, Trip::getTitle, Trip::getVersion, Trip::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(1L, "Client visit: Mumbai to Bengaluru", 1, TripStatus.PLANNED),
                        tuple(2L, "Leh holiday via Delhi", 1, TripStatus.PLANNED),
                        tuple(3L, "Delhi to London via Doha", 1, TripStatus.PLANNED));
        assertThat(trips).allSatisfy(trip -> {
            assertThat(trip.getCreatedAt()).isEqualTo(FixedClockConfig.NOW);
            assertThat(trip.getUpdatedAt()).isEqualTo(FixedClockConfig.NOW);
        });

        // Trip 1: blocks 1-6 and links 1-5, in the order of the file.
        assertThat(blockRepository.findByTripIdOrderByStartTimeAscIdAsc(1L))
                .extracting(Block::getId, Block::getType)
                .containsExactly(
                        tuple(1L, BlockType.FLIGHT),
                        tuple(2L, BlockType.BAG_CLAIM),
                        tuple(3L, BlockType.CAB),
                        tuple(4L, BlockType.MEETING),
                        tuple(5L, BlockType.HOTEL),
                        tuple(6L, BlockType.RESTAURANT));
        assertThat(linkRepository.findByTripIdOrderByIdAsc(1L))
                .extracting(Link::getId, Link::getFromBlockId, Link::getToBlockId, Link::getTravelMinutes)
                .containsExactly(
                        tuple(1L, 1L, 2L, 0),
                        tuple(2L, 2L, 3L, 0),
                        tuple(3L, 3L, 4L, 75),
                        tuple(4L, 4L, 5L, 40),
                        tuple(5L, 5L, 6L, 20));

        // Trip 2: blocks 7-11 and links 6-9. Link 6 joins two flights: its minimum connection time comes from the
        // airport rules file, so the link itself says 0.
        assertThat(blockRepository.findByTripIdOrderByStartTimeAscIdAsc(2L))
                .extracting(Block::getId)
                .containsExactly(7L, 8L, 9L, 10L, 11L);
        assertThat(linkRepository.findByTripIdOrderByIdAsc(2L))
                .extracting(Link::getId, Link::getFromBlockId, Link::getToBlockId, Link::getMinGapMinutes)
                .containsExactly(
                        tuple(6L, 7L, 8L, 0),
                        tuple(7L, 8L, 9L, 0),
                        tuple(8L, 9L, 10L, 0),
                        tuple(9L, 10L, 11L, 0));

        // Trip 3: blocks 12-13 and link 10.
        assertThat(blockRepository.findByTripIdOrderByStartTimeAscIdAsc(3L))
                .extracting(Block::getId, Block::getFlightNo)
                .containsExactly(tuple(12L, "IJ 1907"), tuple(13L, "MJ 31"));
        assertThat(linkRepository.findByTripIdOrderByIdAsc(3L))
                .extracting(Link::getId, Link::getFromBlockId, Link::getToBlockId, Link::getMinGapMinutes)
                .containsExactly(tuple(10L, 12L, 13L, 0));

        assertThat(blockRepository.findAll()).hasSize(13).allSatisfy(block ->
                assertThat(block.getStatus()).isEqualTo(BlockStatus.OK));
    }

    @Test
    void everyLinkBetweenTwoFlightsBecomesACheckedConnection() {
        assertThat(connectionRepository.findAll())
                .extracting(Connection::getId, Connection::getTripId, Connection::getInboundBlockId,
                        Connection::getOutboundBlockId, Connection::getAirport, Connection::getTicketType,
                        Connection::getStatus)
                .containsExactlyInAnyOrder(
                        // Delhi has no minimum connection time in the rules file: UNKNOWN, never a guess.
                        tuple(1L, 2L, 7L, 8L, "DEL", TicketType.SINGLE_BOOKING, ConnectionStatus.UNKNOWN),
                        // Doha: 140 minutes against 60 (demo value).
                        tuple(2L, 3L, 12L, 13L, "DOH", TicketType.UNKNOWN, ConnectionStatus.OK));
        assertThat(connectionRepository.findById(2L).orElseThrow().getDataAsOf()).isEqualTo(FixedClockConfig.NOW);
    }

    @Test
    void flightTimesAreLocalToEachAirportAndScheduledTimesAreKept() {
        Block delhiDoha = blockRepository.findById(12L).orElseThrow();
        assertThat(delhiDoha.getStartTime()).isEqualTo(at("2026-10-11T17:50:00+05:30")); // Delhi time
        assertThat(delhiDoha.getEndTime()).isEqualTo(at("2026-10-11T19:35:00+03:00"));   // Doha time
        assertThat(delhiDoha.getStartZone()).isEqualTo("Asia/Kolkata");
        assertThat(delhiDoha.getEndZone()).isEqualTo("Asia/Qatar");
        assertThat(delhiDoha.getScheduledStart()).isEqualTo(delhiDoha.getStartTime());
        assertThat(delhiDoha.getScheduledEnd()).isEqualTo(delhiDoha.getEndTime());
        assertThat(delhiDoha.getDepAirport()).isEqualTo("DEL");
        assertThat(delhiDoha.getArrAirport()).isEqualTo("DOH");
        assertThat(delhiDoha.getDepTerminal()).isEqualTo("T3");
        assertThat(delhiDoha.getArrTerminal()).isNull();
        assertThat(delhiDoha.getFlightPhase()).isEqualTo(FlightPhase.SCHEDULED);

        Block dohaLondon = blockRepository.findById(13L).orElseThrow(); // lands the next morning, London time (BST)
        assertThat(dohaLondon.getEndTime()).isEqualTo(at("2026-10-12T03:10:00+01:00"));
        assertThat(dohaLondon.getEndZone()).isEqualTo("Europe/London");

        Block meeting = blockRepository.findById(4L).orElseThrow(); // not a flight: trip zone, no flight fields
        assertThat(meeting.getStartZone()).isEqualTo("Asia/Kolkata");
        assertThat(meeting.getEndZone()).isEqualTo("Asia/Kolkata");
        assertThat(meeting.getFlightNo()).isNull();
        assertThat(meeting.getFlightPhase()).isNull();
    }

    @Test
    void blockTimesAreOnTheTripDayInIndianTime() {
        Block flight = blockRepository.findById(1L).orElseThrow();
        assertThat(flight.getStartTime()).isEqualTo(at("2026-10-11T08:00:00+05:30"));
        assertThat(flight.getEndTime()).isEqualTo(at("2026-10-11T09:45:00+05:30"));

        Block cab = blockRepository.findById(3L).orElseThrow(); // a pickup: starts and ends at the same moment
        assertThat(cab.getStartTime()).isEqualTo(at("2026-10-11T10:05:00+05:30"));
        assertThat(cab.getEndTime()).isEqualTo(cab.getStartTime());

        Block pune = blockRepository.findById(7L).orElseThrow();
        assertThat(pune.getStartTime()).isEqualTo(at("2026-10-11T06:10:00+05:30"));
    }

    @Test
    void nullableFieldsFromTheFileStayNull() {
        Block flight = blockRepository.findById(1L).orElseThrow();
        assertThat(flight.getFreeChangeUntilMinutes()).isNull(); // "freeChangeUntilMinutes": null
        assertThat(flight.getProviderRef()).isEqualTo("K7Q2ZM");
        assertThat(flight.getLat()).isEqualTo(19.0896);

        Block bags = blockRepository.findById(2L).orElseThrow();
        assertThat(bags.getProviderRef()).isNull(); // "providerRef": null
        assertThat(bags.getFreeChangeUntilMinutes()).isZero();

        Block meeting = blockRepository.findById(4L).orElseThrow();
        assertThat(meeting.isAnchor()).isTrue();
    }

    @Test
    void seedingAgainAddsNothingWhenTripsExist() {
        int saved = seeder.seedIfEmpty();

        assertThat(saved).isZero();
        assertThat(tripRepository.count()).isEqualTo(3);
        assertThat(blockRepository.count()).isEqualTo(13);
        assertThat(linkRepository.count()).isEqualTo(10);
        assertThat(connectionRepository.count()).isEqualTo(2);
    }

    @Test
    void deleteAllAndReseedRestoresFreshDemoDataWithTheSameIds() {
        // Change the data the way a disruption would: a newer version, a red block, an old creation time.
        Trip trip = tripRepository.findById(1L).orElseThrow();
        trip.setVersion(5);
        trip.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        tripRepository.save(trip);
        Block flight = blockRepository.findById(1L).orElseThrow();
        flight.setStatus(BlockStatus.NEEDS_USER);
        blockRepository.save(flight);

        int saved = seeder.deleteAllAndReseed();

        assertThat(saved).isEqualTo(3);
        assertThat(tripRepository.findAll())
                .extracting(Trip::getId, Trip::getVersion, Trip::getCreatedAt)
                .containsExactlyInAnyOrder(
                        tuple(1L, 1, FixedClockConfig.NOW),
                        tuple(2L, 1, FixedClockConfig.NOW),
                        tuple(3L, 1, FixedClockConfig.NOW));
        // The id counters started again at 1: blocks 1-13, links 1-10 and connections 1-2, exactly as on an empty
        // database.
        assertThat(blockRepository.findAll()).extracting(Block::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L);
        assertThat(linkRepository.findAll()).extracting(Link::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(connectionRepository.findAll()).extracting(Connection::getId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(blockRepository.findById(1L).orElseThrow().getStatus()).isEqualTo(BlockStatus.OK);
    }

    @Test
    void tripDayIsCountedInIndianTimeNotInUtc() {
        // 20:00 in UTC on 10 October is already 01:30 on 11 October in India.
        Clock lateEveningUtc = Clock.fixed(Instant.parse("2026-10-10T20:00:00Z"), ZoneOffset.UTC);
        ZoneId india = ZoneId.of("Asia/Kolkata");

        assertThat(DemoTripSeeder.tripDay(lateEveningUtc, india, 0)).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(DemoTripSeeder.tripDay(lateEveningUtc, india, 1)).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    private static Instant at(String isoTime) {
        return OffsetDateTime.parse(isoTime).toInstant();
    }
}
