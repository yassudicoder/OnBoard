package com.safarsaathi.backend.watcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.safarsaathi.backend.ripple.RippleChange;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.DisruptionKind;

/** Unit tests for the pretend flight-status service (no Spring, no database). */
class MockFlightStatusProviderTest {

    private final MockFlightStatusProvider provider = new MockFlightStatusProvider();

    @Test
    void reportedDelayIsRelativeToTheBlockTimesWhenItIsSaved() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportDelay(1, 90);

        FlightStatusChange change = provider.check(flight).orElseThrow();

        assertThat(change.kind()).isEqualTo(DisruptionKind.DELAY);
        assertThat(change.delayMinutes()).isEqualTo(90);
        assertThat(change.detail()).isEqualTo("Delayed by 90 minutes (mock flight status)");
        RippleChange resolved = change.toRippleChange(flight);
        assertThat(resolved.newStart()).isEqualTo(at("2026-10-11T09:30:00+05:30"));
        assertThat(resolved.newEnd()).isEqualTo(at("2026-10-11T11:15:00+05:30"));
    }

    @Test
    void checkingDoesNotForgetTheChangeUntilItIsSaved() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportDelay(1, 90);

        FlightStatusChange first = provider.check(flight).orElseThrow();
        // Saving failed, so the watcher never called applied(): the next check hands out the same change again.
        assertThat(provider.check(flight)).contains(first);

        provider.applied(flight, first); // saved now
        assertThat(provider.check(flight)).isEmpty();
        assertThat(provider.pendingCount()).isZero();
    }

    @Test
    void aDelayReportedAfterTheCheckIsKeptForTheNextCheck() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportDelay(1, 30);
        FlightStatusChange checked = provider.check(flight).orElseThrow();

        provider.reportDelay(1, 60); // arrives while the +30 is being saved
        provider.applied(flight, checked);

        assertThat(provider.check(flight).orElseThrow().delayMinutes()).isEqualTo(60);
    }

    @Test
    void savingACancellationForgetsEverythingForThatBlock() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportCancel(1);
        FlightStatusChange cancel = provider.check(flight).orElseThrow();
        provider.reportDelay(1, 15); // a cancellation wins over any later delay

        provider.applied(flight, cancel);

        assertThat(provider.check(flight)).isEmpty();
    }

    @Test
    void aCancellationReportedWhileADelayIsSavedIsStillToDo() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportDelay(1, 30);
        FlightStatusChange delay = provider.check(flight).orElseThrow();
        provider.reportCancel(1);

        provider.applied(flight, delay);

        assertThat(provider.check(flight).orElseThrow().cancelled()).isTrue();
    }

    @Test
    void delaysAddUpAndCancelWins() {
        Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");
        provider.reportDelay(1, 30);
        provider.reportDelay(1, 60);
        assertThat(provider.check(flight).orElseThrow().delayMinutes()).isEqualTo(90);

        provider.reportDelay(1, 30);
        provider.reportCancel(1);
        provider.reportDelay(1, 15);
        FlightStatusChange change = provider.check(flight).orElseThrow();
        assertThat(change.cancelled()).isTrue();
        assertThat(change.kind()).isEqualTo(DisruptionKind.CANCEL);
        assertThat(change.delayMinutes()).isNull();
    }

    @Test
    void restorePutsBackWhatWasWaitingBefore() {
        provider.reportDelay(1, 30);
        Optional<MockFlightStatusProvider.Pending> before = provider.pendingFor(1);

        provider.reportDelay(1, 90);
        provider.restore(1, before);
        assertThat(provider.pendingFor(1)).contains(new MockFlightStatusProvider.Pending(30, false));

        provider.restore(1, Optional.empty());
        assertThat(provider.pendingFor(1)).isEmpty();
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
        block.setTripId(1L);
        block.setTitle("Flight IJ 512 Mumbai to Bengaluru");
        block.setStartTime(at(start));
        block.setEndTime(at(end));
        return block;
    }

    static Instant at(String isoTime) {
        return OffsetDateTime.parse(isoTime).toInstant();
    }
}
