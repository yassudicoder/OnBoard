package com.safarsaathi.backend.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.safarsaathi.backend.MovableClockConfig;
import com.safarsaathi.backend.MovableClockConfig.MovableClock;
import com.safarsaathi.backend.demo.DemoTripSeeder;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.FlightPhase;

/**
 * Confirmed facts on trip 3's Doha connection (id 2; block 12 = Delhi-Doha, block 13 = Doha-London), set directly on
 * the blocks. The demo buttons that set flight phases come in phase C2; this checks the rules underneath.
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@Import(MovableClockConfig.class)
class ConnectionServiceTest {

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private DemoTripSeeder seeder;

    @Autowired
    private MovableClock clock;

    @BeforeEach
    @AfterEach // other test classes may share this Spring context (and its database): leave fresh demo data behind
    void freshDemoData() {
        clock.set(MovableClockConfig.START);
        seeder.deleteAllAndReseed();
    }

    @Test
    void nextFlightLeavingWhileTheTravellerIsInTheAirIsConfirmedAndStaysConfirmed() {
        setPhase(12, FlightPhase.IN_AIR);
        setPhase(13, FlightPhase.IN_AIR); // Doha-London has left, the traveller is still on Delhi-Doha
        clock.advance(Duration.ofMinutes(5));
        Instant confirmedAt = clock.instant();

        connectionService.recheck(3);

        Connection connection = connectionRepository.findById(2L).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.CONFIRMED_MISSED);
        assertThat(connection.getMissConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(connection.isBasedOnDemoValue()).isFalse(); // a fact, not a calculation
        assertThat(connection.getReason()).isEqualTo("Your next flight MJ 31 has left Doha before you could get there.");

        // Later both flights have landed: the data alone can no longer tell which came first, but the fact stays.
        setPhase(12, FlightPhase.LANDED);
        clock.advance(Duration.ofHours(2));
        connectionService.recheck(3);

        Connection later = connectionRepository.findById(2L).orElseThrow();
        assertThat(later.getStatus()).isEqualTo(ConnectionStatus.CONFIRMED_MISSED);
        assertThat(later.getMissConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void cancelledOnwardFlightCancelsTheConnection() {
        Block onward = blockRepository.findById(13L).orElseThrow();
        onward.setStatus(BlockStatus.CANCELLED);
        blockRepository.save(onward);

        connectionService.recheck(3);

        Connection connection = connectionRepository.findById(2L).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.CANCELLED);
        assertThat(connection.getReason()).isEqualTo("Your onward flight MJ 31 from Doha to London is cancelled.");
    }

    private void setPhase(long blockId, FlightPhase phase) {
        Block flight = blockRepository.findById(blockId).orElseThrow();
        flight.setFlightPhase(phase);
        blockRepository.save(flight);
    }
}
