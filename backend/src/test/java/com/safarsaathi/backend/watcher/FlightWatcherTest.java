package com.safarsaathi.backend.watcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.safarsaathi.backend.MovableClockConfig;
import com.safarsaathi.backend.MovableClockConfig.MovableClock;
import com.safarsaathi.backend.demo.DemoService;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.DisruptionEventRepository;
import com.safarsaathi.backend.trip.DisruptionKind;
import com.safarsaathi.backend.trip.TripRepository;

/**
 * The timer job of the flight watcher with the mock provider, on the seeded demo trips in an in-memory database.
 * The tests call {@link FlightWatcher#checkFlights()} directly instead of waiting 30 seconds for the timer.
 *
 * <p>Same annotations as DemoControllerTest, so both share one Spring context (faster); each test reloads the demo
 * data first.
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@AutoConfigureMockMvc
@Import(MovableClockConfig.class)
class FlightWatcherTest {

    @Autowired
    private FlightWatcher watcher;

    @Autowired
    private MockFlightStatusProvider mockFlightStatus;

    @Autowired
    private DemoService demoService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private DisruptionEventRepository eventRepository;

    @Autowired
    private MovableClock clock;

    @BeforeEach
    void startFromFreshDemoData() {
        clock.set(MovableClockConfig.START);
        demoService.reset();
    }

    @Test
    void usesTheMockProviderWhenNoKeyIsSet() {
        assertThat(watcher.provider()).isSameAs(mockFlightStatus);
    }

    @Test
    void timerAppliesAReportedDelayExactlyOnce() {
        mockFlightStatus.reportDelay(1, 90);

        watcher.checkFlights();

        assertThat(tripRepository.findById(1L).orElseThrow().getVersion()).isEqualTo(2);
        assertThat(block(1).getStartTime()).isEqualTo(at("2026-10-11T09:30:00+05:30"));
        assertThat(block(2).getStatus()).isEqualTo(BlockStatus.AUTO_ADJUSTED);
        assertThat(block(2).getStartTime()).isEqualTo(at("2026-10-11T11:15:00+05:30"));
        assertThat(block(3).getStatus()).isEqualTo(BlockStatus.AUTO_ADJUSTED);
        assertThat(block(4).getStatus()).isEqualTo(BlockStatus.NEEDS_USER);
        assertThat(block(4).getStartTime()).isEqualTo(at("2026-10-11T12:00:00+05:30")); // not moved
        assertThat(block(5).getStatus()).isEqualTo(BlockStatus.OK);
        assertThat(block(6).getStatus()).isEqualTo(BlockStatus.OK);
        assertThat(eventRepository.findByTripIdOrderByIdAsc(1L)).singleElement().satisfies(event -> {
            assertThat(event.getKind()).isEqualTo(DisruptionKind.DELAY);
            assertThat(event.getBlockId()).isEqualTo(1L);
        });

        // The mock hands each change out once, so the next run finds nothing new.
        watcher.checkFlights();

        assertThat(tripRepository.findById(1L).orElseThrow().getVersion()).isEqualTo(2);
        assertThat(block(1).getStartTime()).isEqualTo(at("2026-10-11T09:30:00+05:30"));
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(tripRepository.findById(2L).orElseThrow().getVersion()).isEqualTo(1); // other trip untouched
    }

    @Test
    void timerAppliesACancelledConnectingFlight() {
        mockFlightStatus.reportCancel(8); // trip 2's Delhi-Leh flight

        watcher.checkFlights();

        assertThat(tripRepository.findById(2L).orElseThrow().getVersion()).isEqualTo(2);
        assertThat(block(7).getStatus()).isEqualTo(BlockStatus.OK); // the flight before it is not affected
        assertThat(block(8).getStatus()).isEqualTo(BlockStatus.CANCELLED);
        assertThat(block(9).getStatus()).isEqualTo(BlockStatus.NEEDS_USER);
        assertThat(block(10).getStatus()).isEqualTo(BlockStatus.NEEDS_USER);
        assertThat(block(11).getStatus()).isEqualTo(BlockStatus.NEEDS_USER);
        assertThat(tripRepository.findById(1L).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void cancelledFlightsAreNotCheckedAgain() {
        mockFlightStatus.reportCancel(1);
        watcher.checkFlights();
        assertThat(block(1).getStatus()).isEqualTo(BlockStatus.CANCELLED);

        mockFlightStatus.reportDelay(1, 30);
        watcher.checkFlights();

        // Skipped: still version 2, and the delay is still waiting in the mock.
        assertThat(tripRepository.findById(1L).orElseThrow().getVersion()).isEqualTo(2);
        assertThat(mockFlightStatus.pendingCount()).isEqualTo(1);
        mockFlightStatus.clear(); // leave nothing behind for the next test
    }

    private Block block(long id) {
        return blockRepository.findById(id).orElseThrow();
    }

    private static Instant at(String isoTime) {
        return OffsetDateTime.parse(isoTime).toInstant();
    }
}
