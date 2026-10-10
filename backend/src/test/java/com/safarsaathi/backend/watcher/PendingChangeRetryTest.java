package com.safarsaathi.backend.watcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.AppProperties;
import com.safarsaathi.backend.config.ProviderStatus;
import com.safarsaathi.backend.demo.DemoService;
import com.safarsaathi.backend.demo.DemoTripSeeder;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.BlockType;
import com.safarsaathi.backend.trip.DisruptionKind;
import com.safarsaathi.backend.trip.Trip;
import com.safarsaathi.backend.trip.TripRepository;
import com.safarsaathi.backend.trip.TripStatus;

/**
 * A reported flight change is never lost and never saved twice when saving fails (e.g. the database is down).
 * The real {@link FlightWatcher}, {@link MockFlightStatusProvider} and {@link DemoService}, with a
 * {@link DisruptionService} stand-in that fails on purpose. No Spring, no database.
 */
class PendingChangeRetryTest {

    private final MockFlightStatusProvider mockFlightStatus = new MockFlightStatusProvider();
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final DisruptionService disruptionService = mock(DisruptionService.class);
    private final Block flight = MockFlightStatusProviderTest.flight(1, "2026-10-11T08:00:00+05:30",
            "2026-10-11T09:45:00+05:30");
    private FlightWatcher watcher;

    @BeforeEach
    void setUp() {
        flight.setType(BlockType.FLIGHT);
        flight.setStatus(BlockStatus.OK);
        Trip trip = new Trip();
        ReflectionTestUtils.setField(trip, "id", 1L);
        trip.setStatus(TripStatus.PLANNED);
        when(tripRepository.findAll(any(Sort.class))).thenReturn(List.of(trip));
        when(blockRepository.findByTripIdOrderByStartTimeAscIdAsc(1L)).thenReturn(List.of(flight));
        when(blockRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(disruptionService.findBlockToChange(1L, 1L)).thenReturn(flight);

        AppProperties settings = new AppProperties("demo", null, new AppProperties.Providers(
                new AppProperties.Gemini(null, null), new AppProperties.ApiKey(null), new AppProperties.ApiKey(null),
                new AppProperties.CredentialsFile(null), new AppProperties.CredentialsFile(null),
                new AppProperties.ApiKey(null)), new AppProperties.Seed(true, 1));
        watcher = new FlightWatcher(tripRepository, blockRepository, disruptionService, mockFlightStatus,
                new ProviderStatus(settings), settings, new ObjectMapper(), Clock.system(ZoneId.of("Asia/Kolkata")),
                Duration.ofSeconds(30));
    }

    @Test
    void timerKeepsTheChangeWhenSavingFailsAndSavesItOnceOnTheNextRun() {
        mockFlightStatus.reportDelay(1, 90);
        when(disruptionService.apply(eq(1L), eq(1L), any()))
                .thenThrow(new DataAccessResourceFailureException("database down"))
                .thenReturn(saved());

        watcher.checkFlights(); // fails inside; the timer logs it and carries on
        assertThat(mockFlightStatus.pendingFor(1)).contains(new MockFlightStatusProvider.Pending(90, false));

        watcher.checkFlights(); // retry: saved this time
        assertThat(mockFlightStatus.pendingCount()).isZero();

        watcher.checkFlights(); // nothing left: no third save
        ArgumentCaptor<FlightStatusChange> changes = ArgumentCaptor.forClass(FlightStatusChange.class);
        verify(disruptionService, times(2)).apply(eq(1L), eq(1L), changes.capture());
        assertThat(changes.getAllValues()).allMatch(change -> change.delayMinutes() == 90); // the same +90, not +180
    }

    @Test
    void demoButtonTakesItsReportBackWhenSavingFails() {
        DemoService demo = new DemoService(watcher, mockFlightStatus, disruptionService, mock(DemoTripSeeder.class));
        when(disruptionService.apply(eq(1L), eq(1L), any()))
                .thenThrow(new DataAccessResourceFailureException("database down"))
                .thenReturn(saved());

        assertThatThrownBy(() -> demo.triggerDelay(1L, 1L, 90)).isInstanceOf(DataAccessResourceFailureException.class);
        // The caller got an error, so nothing may be left behind for the timer to apply later.
        assertThat(mockFlightStatus.pendingCount()).isZero();

        demo.triggerDelay(1L, 1L, 90); // the user presses again: +90 once, not +180
        ArgumentCaptor<FlightStatusChange> changes = ArgumentCaptor.forClass(FlightStatusChange.class);
        verify(disruptionService, times(2)).apply(anyLong(), anyLong(), changes.capture());
        assertThat(changes.getAllValues().get(1).delayMinutes()).isEqualTo(90);
        assertThat(mockFlightStatus.pendingCount()).isZero();
    }

    @Test
    void demoButtonFailureKeepsAChangeThatWasAlreadyWaiting() {
        DemoService demo = new DemoService(watcher, mockFlightStatus, disruptionService, mock(DemoTripSeeder.class));
        mockFlightStatus.reportDelay(1, 30); // e.g. left over from a timer run that failed
        when(disruptionService.apply(eq(1L), eq(1L), any()))
                .thenThrow(new DataAccessResourceFailureException("database down"));

        assertThatThrownBy(() -> demo.triggerCancel(1L, 1L)).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(mockFlightStatus.pendingFor(1)).contains(new MockFlightStatusProvider.Pending(30, false));
    }

    private static DisruptionResult saved() {
        return new DisruptionResult(1L, 2, 1L, DisruptionKind.DELAY, List.of(), List.of());
    }
}
