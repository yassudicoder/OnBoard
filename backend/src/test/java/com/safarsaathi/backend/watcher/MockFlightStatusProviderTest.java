package com.safarsaathi.backend.watcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.DisruptionKind;

/** Unit tests for the pretend flight-status service (no Spring, no database). */
class MockFlightStatusProviderTest {

    private final MockFlightStatusProvider provider = new MockFlightStatusProvider();

    @Test
    void reportedDelayIsHandedOutOnceRelativeToTheCurrentTimes() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportDelay(1, 90);

        FlightStatusChange change = provider.check(flight).orElseThrow();

        assertThat(change.kind()).isEqualTo(DisruptionKind.DELAY);
        assertThat(change.newStart()).isEqualTo(at("2026-10-11T09:30:00+05:30"));
        assertThat(change.newEnd()).isEqualTo(at("2026-10-11T11:15:00+05:30"));
        assertThat(change.detail()).isEqualTo("Delayed by 90 minutes (mock flight status)");
        assertThat(provider.check(flight)).isEmpty(); // used up
    }

    @Test
    void delaysAddUpAndCancelWins() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportDelay(1, 30);
        provider.reportDelay(1, 60);
        assertThat(provider.check(flight).orElseThrow().newStart()).isEqualTo(at("2026-10-11T09:30:00+05:30"));

        provider.reportDelay(1, 30);
        provider.reportCancel(1);
        provider.reportDelay(1, 15);
        FlightStatusChange change = provider.check(flight).orElseThrow();
        assertThat(change.cancelled()).isTrue();
        assertThat(change.kind()).isEqualTo(DisruptionKind.CANCEL);
        assertThat(change.newStart()).isNull();
    }

    @Test
    void onlyTheReportedBlockChangesAndClearForgetsEverything() {
        provider.reportDelay(1, 30);
        assertThat(provider.check(flight(2, "2026-10-11T10:00:00+05:30", "2026-10-11T11:00:00+05:30"))).isEmpty();

        provider.clear();
        assertThat(provider.pendingCount()).isZero();
        assertThat(provider.check(flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30"))).isEmpty();
    }

    @Test
    void delayMustBePositive() {
        assertThatThrownBy(() -> provider.reportDelay(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A block with an id. Block has no setId (the database hands ids out), so the test sets the field directly. */
    static Block flight(long id, String start, String end) {
        Block block = new Block();
        ReflectionTestUtils.setField(block, "id", id);
        block.setTitle("Flight IJ 512 Mumbai to Bengaluru");
        block.setStartTime(at(start));
        block.setEndTime(at(end));
        return block;
    }

    static Instant at(String isoTime) {
        return OffsetDateTime.parse(isoTime).toInstant();
    }
}
