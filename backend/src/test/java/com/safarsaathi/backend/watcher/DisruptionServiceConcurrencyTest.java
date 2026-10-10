package com.safarsaathi.backend.watcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.safarsaathi.backend.FixedClockConfig;
import com.safarsaathi.backend.demo.DemoTripSeeder;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.DisruptionEvent;
import com.safarsaathi.backend.trip.DisruptionEventRepository;
import com.safarsaathi.backend.trip.DisruptionKind;
import com.safarsaathi.backend.trip.TripRepository;

/**
 * Several writers change the same trip at the same moment, the way two copies of the backend (or the timer and a
 * future booking orchestrator) could. Each writer calls {@link DisruptionService#apply} directly, so the watcher's
 * in-memory lock does NOT help here: only the database row lock on the trip keeps them apart.
 *
 * <p>Without that lock they would all read version 1 and the flight at 08:00, and the last one to save would win:
 * lost version numbers (the app might miss a refresh) and lost delays.
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@Import(FixedClockConfig.class)
class DisruptionServiceConcurrencyTest {

    private static final int WRITERS = 8;
    private static final int MINUTES_EACH = 10;

    @Autowired
    private DisruptionService disruptionService;

    @Autowired
    private DemoTripSeeder seeder;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private DisruptionEventRepository eventRepository;

    @BeforeEach
    @AfterEach // other test classes may share this Spring context (and its database): leave fresh demo data behind
    void freshDemoData() {
        seeder.deleteAllAndReseed();
    }

    @Test
    void competingDelaysOnOneTripAreAllSavedAndAddUp() throws Exception {
        ExecutorService writers = Executors.newFixedThreadPool(WRITERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<DisruptionResult>> answers = new ArrayList<>();
        for (int i = 0; i < WRITERS; i++) {
            answers.add(writers.submit(() -> {
                go.await(); // all writers start together, to make them collide
                return disruptionService.apply(1, 1, FlightStatusChange.delayedBy(MINUTES_EACH, "Delayed (test)"));
            }));
        }
        go.countDown();
        List<Integer> versionsSeen = new ArrayList<>();
        for (Future<DisruptionResult> answer : answers) {
            versionsSeen.add(answer.get(30, TimeUnit.SECONDS).version());
        }
        writers.shutdown();

        // Every change counted: version 1 + 8, and each writer got its own version number (2..9), none lost.
        assertThat(tripRepository.findById(1L).orElseThrow().getVersion()).isEqualTo(1 + WRITERS);
        assertThat(versionsSeen).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(2, 1 + WRITERS).boxed().toList());

        // Every delay counted: 8 x 10 minutes = 80 minutes later.
        Block flight = blockRepository.findById(1L).orElseThrow();
        Duration total = Duration.ofMinutes((long) WRITERS * MINUTES_EACH);
        assertThat(flight.getStartTime()).isEqualTo(at("2026-10-11T08:00:00+05:30").plus(total));
        assertThat(flight.getEndTime()).isEqualTo(at("2026-10-11T09:45:00+05:30").plus(total));

        // The rest of the trip matches the final flight: bag claim starts exactly when it lands.
        assertThat(blockRepository.findById(2L).orElseThrow().getStartTime()).isEqualTo(flight.getEndTime());

        // One disruption event per change.
        List<DisruptionEvent> events = eventRepository.findByTripIdOrderByIdAsc(1L);
        assertThat(events).hasSize(WRITERS).allMatch(event -> event.getKind() == DisruptionKind.DELAY);
    }

    private static Instant at(String isoTime) {
        return OffsetDateTime.parse(isoTime).toInstant();
    }
}
