package com.safarsaathi.backend.watcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.safarsaathi.backend.FixedClockConfig;
import com.safarsaathi.backend.connection.ConnectionRepository;
import com.safarsaathi.backend.demo.DemoTripSeeder;
import com.safarsaathi.backend.trip.DisruptionEvent;
import com.safarsaathi.backend.trip.DisruptionEventRepository;
import com.safarsaathi.backend.trip.DisruptionKind;
import com.safarsaathi.recovery.connection.ConnectionStatus;

/**
 * LIKELY_MISSED events (predictions; called MISSED before Disruption Recovery), on the seeded demo trips.
 * <ul>
 *   <li>Trip 3: block 12 = Delhi-Doha flight landing 19:35 Doha time, block 13 = Doha-London flight at 21:55;
 *       Doha needs at least 60 minutes (a DEMO VALUE in the airport rules file), so 80 minutes to spare.
 *       Connection 2.</li>
 *   <li>Trip 2: block 7 = Pune-Delhi flight landing 08:20, block 8 = Delhi-Leh flight at 10:15. Delhi has no
 *       minimum connection time in the rules file, so that connection (1) is UNKNOWN unless the next flight leaves
 *       before you land.</li>
 *   <li>Trip 1: block 1 = the Mumbai-Bengaluru flight, block 4 = the client meeting.</li>
 * </ul>
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@Import(FixedClockConfig.class)
class MissedConnectionTest {

    @Autowired
    private DisruptionService disruptionService;

    @Autowired
    private DemoTripSeeder seeder;

    @Autowired
    private DisruptionEventRepository eventRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @BeforeEach
    @AfterEach // other test classes may share this Spring context (and its database): leave fresh demo data behind
    void freshDemoData() {
        seeder.deleteAllAndReseed();
    }

    @Test
    void lateFirstFlightRecordsALikelyMissedConnection() {
        disruptionService.apply(3, 12, FlightStatusChange.delayedBy(95, "Delayed (test)"));

        // Delhi-Doha now lands 21:10 Doha time; +60 minutes = 22:10, but Doha-London leaves 21:55: 15 minutes short.
        List<DisruptionEvent> events = eventRepository.findByTripIdOrderByIdAsc(3L);
        assertThat(events).extracting(DisruptionEvent::getKind, DisruptionEvent::getBlockId)
                .containsExactly(tuple(DisruptionKind.DELAY, 12L), tuple(DisruptionKind.LIKELY_MISSED, 13L));
        assertThat(events.get(1).getDetail()).isEqualTo("Likely to miss the connection (prediction): Flight MJ 31 "
                + "Doha to London leaves before you can get there from Flight IJ 1907 Delhi to Doha (15 minutes short).");
        assertThat(events.get(1).getDetectedAt()).isEqualTo(events.get(0).getDetectedAt());
        assertThat(connectionRepository.findById(2L).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.LIKELY_MISSED);
    }

    @Test
    void connectionThatStillWorksIsNotMissed() {
        disruptionService.apply(3, 12, FlightStatusChange.delayedBy(30, "Delayed (test)")); // 110 minutes left

        assertThat(eventRepository.findByTripIdOrderByIdAsc(3L)).extracting(DisruptionEvent::getKind)
                .containsExactly(DisruptionKind.DELAY);
        assertThat(connectionRepository.findById(2L).orElseThrow().getStatus()).isEqualTo(ConnectionStatus.OK);
    }

    @Test
    void withoutAMinimumConnectionTimeNothingIsPredicted() {
        // Pune-Delhi lands 09:20, Delhi-Leh leaves 10:15: 55 minutes. Delhi has no official minimum, so OnBoard
        // does not guess: no LIKELY_MISSED event, and the connection says UNKNOWN.
        disruptionService.apply(2, 7, FlightStatusChange.delayedBy(60, "Delayed (test)"));

        assertThat(eventRepository.findByTripIdOrderByIdAsc(2L)).extracting(DisruptionEvent::getKind)
                .containsExactly(DisruptionKind.DELAY);
        assertThat(connectionRepository.findById(1L).orElseThrow().getStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
    }

    @Test
    void aNextFlightLeavingBeforeLandingIsLikelyMissedEvenWithoutAMinimum() {
        disruptionService.apply(2, 7, FlightStatusChange.delayedBy(150, "Delayed (test)")); // lands 10:50

        List<DisruptionEvent> events = eventRepository.findByTripIdOrderByIdAsc(2L);
        assertThat(events).extracting(DisruptionEvent::getKind, DisruptionEvent::getBlockId)
                .containsExactly(tuple(DisruptionKind.DELAY, 7L), tuple(DisruptionKind.LIKELY_MISSED, 8L));
        assertThat(events.get(1).getDetail()).endsWith("(35 minutes short).");
        assertThat(connectionRepository.findById(1L).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.LIKELY_MISSED);
    }

    @Test
    void aLateMeetingIsNotAMissedConnection() {
        // +90: the meeting becomes NEEDS_USER with a broken link, but a meeting waits for you: no event.
        disruptionService.apply(1, 1, FlightStatusChange.delayedBy(90, "Delayed (test)"));

        assertThat(eventRepository.findByTripIdOrderByIdAsc(1L)).extracting(DisruptionEvent::getKind)
                .containsExactly(DisruptionKind.DELAY);
    }

    @Test
    void theSameLikelyMissIsRecordedOnlyOnce() {
        disruptionService.apply(3, 12, FlightStatusChange.delayedBy(95, "Delayed (test)"));
        disruptionService.apply(3, 12, FlightStatusChange.delayedBy(30, "Delayed again (test)")); // still missed

        assertThat(eventRepository.findByTripIdOrderByIdAsc(3L))
                .extracting(DisruptionEvent::getKind, DisruptionEvent::getBlockId)
                .containsExactly(tuple(DisruptionKind.DELAY, 12L), tuple(DisruptionKind.LIKELY_MISSED, 13L),
                        tuple(DisruptionKind.DELAY, 12L));
    }
}
