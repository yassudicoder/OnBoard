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
import com.safarsaathi.backend.demo.DemoTripSeeder;
import com.safarsaathi.backend.trip.DisruptionEvent;
import com.safarsaathi.backend.trip.DisruptionEventRepository;
import com.safarsaathi.backend.trip.DisruptionKind;

/**
 * MISSED events, on the seeded demo trips. Trip 2 (Leh): block 7 = Pune-Delhi flight 06:10-08:20, block 8 =
 * Delhi-Leh flight at 10:15, with at least 75 minutes to change planes in Delhi (40 minutes to spare).
 * Trip 1: block 1 = the Mumbai-Bengaluru flight, block 4 = the client meeting.
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

    @BeforeEach
    @AfterEach // other test classes may share this Spring context (and its database): leave fresh demo data behind
    void freshDemoData() {
        seeder.deleteAllAndReseed();
    }

    @Test
    void lateFirstFlightRecordsTheMissedConnectingFlight() {
        disruptionService.apply(2, 7, FlightStatusChange.delayedBy(60, "Delayed (test)"));

        // Pune-Delhi now lands 09:20; +75 minutes = 10:35, but Delhi-Leh leaves 10:15: 20 minutes short.
        List<DisruptionEvent> events = eventRepository.findByTripIdOrderByIdAsc(2L);
        assertThat(events).extracting(DisruptionEvent::getKind, DisruptionEvent::getBlockId)
                .containsExactly(tuple(DisruptionKind.DELAY, 7L), tuple(DisruptionKind.MISSED, 8L));
        assertThat(events.get(1).getDetail()).isEqualTo("Missed connection: Flight SM 445 Delhi to Leh leaves "
                + "before you can get there from Flight SM 231 Pune to Delhi (20 minutes short).");
        assertThat(events.get(1).getDetectedAt()).isEqualTo(events.get(0).getDetectedAt());
    }

    @Test
    void connectionThatStillWorksIsNotMissed() {
        disruptionService.apply(2, 7, FlightStatusChange.delayedBy(30, "Delayed (test)")); // 10 minutes to spare

        assertThat(eventRepository.findByTripIdOrderByIdAsc(2L)).extracting(DisruptionEvent::getKind)
                .containsExactly(DisruptionKind.DELAY);
    }

    @Test
    void aLateMeetingIsNotAMissedConnection() {
        // +90: the meeting becomes NEEDS_USER with a broken link, but a meeting waits for you: no MISSED event.
        disruptionService.apply(1, 1, FlightStatusChange.delayedBy(90, "Delayed (test)"));

        assertThat(eventRepository.findByTripIdOrderByIdAsc(1L)).extracting(DisruptionEvent::getKind)
                .containsExactly(DisruptionKind.DELAY);
    }

    @Test
    void theSameMissedConnectionIsRecordedOnlyOnce() {
        disruptionService.apply(2, 7, FlightStatusChange.delayedBy(60, "Delayed (test)"));
        disruptionService.apply(2, 7, FlightStatusChange.delayedBy(30, "Delayed again (test)")); // still missed

        assertThat(eventRepository.findByTripIdOrderByIdAsc(2L))
                .extracting(DisruptionEvent::getKind, DisruptionEvent::getBlockId)
                .containsExactly(tuple(DisruptionKind.DELAY, 7L), tuple(DisruptionKind.MISSED, 8L),
                        tuple(DisruptionKind.DELAY, 7L));
    }
}
