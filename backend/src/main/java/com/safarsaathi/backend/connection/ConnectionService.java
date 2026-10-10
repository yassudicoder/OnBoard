package com.safarsaathi.backend.connection;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.BlockType;
import com.safarsaathi.backend.trip.TripMapper;
import com.safarsaathi.backend.trip.TripNotFoundException;
import com.safarsaathi.backend.trip.TripRepository;
import com.safarsaathi.recovery.connection.ConnectionCheck;
import com.safarsaathi.recovery.connection.ConnectionChecker;
import com.safarsaathi.recovery.connection.ConnectionFacts;
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.FlightLeg;
import com.safarsaathi.recovery.connection.FlightPhase;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * Keeps the {@code connections} table up to date: creates a connection for every link between two flights, checks it
 * with the shared {@link ConnectionChecker} (recovery-core, the same code the app uses offline) and saves the result.
 *
 * <p>Checks happen inside the caller's transaction: {@code watcher.DisruptionService} rechecks a trip's connections in
 * the same transaction that saves a delay, so the trip's new version, its blocks and its connections always match.
 */
@Service
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final TripRepository tripRepository;
    private final BlockRepository blockRepository;
    private final TripMapper mapper;
    private final Clock clock;
    private final ConnectionChecker checker;

    public ConnectionService(ConnectionRepository connectionRepository, TripRepository tripRepository,
                             BlockRepository blockRepository, TripMapper mapper, Clock clock,
                             AirportRulesProvider rulesProvider) {
        this.connectionRepository = connectionRepository;
        this.tripRepository = tripRepository;
        this.blockRepository = blockRepository;
        this.mapper = mapper;
        this.clock = clock;
        // The checker is plain Java with no state of its own, so one shared copy is enough.
        this.checker = new ConnectionChecker(rulesProvider.rules());
    }

    /**
     * The minimum connection time between two flight blocks, or null when unknown (or when they are not two flights
     * with airports). DisruptionService gives it to the ripple engine as the gap a flight-to-flight link needs, so
     * the engine's "can't make it" matches the checker's LIKELY_MISSED.
     */
    public Integer minimumConnectionMinutes(Block inbound, Block outbound) {
        if (!isFlightWithAirports(inbound) || !isFlightWithAirports(outbound)) {
            return null;
        }
        return checker.minimumConnectionMinutes(toLeg(inbound), toLeg(outbound));
    }

    /**
     * Saves a new connection from flight {@code inbound} to flight {@code outbound} and checks it at once.
     * Must run inside a transaction (the demo seeder's).
     *
     * @param dataAsOf when the flight times it is checked against were known (for seeded trips: the seeding time)
     */
    public Connection create(Block inbound, Block outbound, TicketType ticketType, Instant dataAsOf) {
        Connection connection = new Connection();
        connection.setTripId(inbound.getTripId());
        connection.setInboundBlockId(inbound.getId());
        connection.setOutboundBlockId(outbound.getId());
        connection.setTicketType(ticketType == null ? TicketType.UNKNOWN : ticketType);
        applyCheck(connection, inbound, outbound, dataAsOf);
        return connectionRepository.save(connection);
    }

    /**
     * Checks every connection of the trip again with the blocks as they are now, and saves the results.
     * Runs inside the caller's transaction, after the caller locked the trip row (DisruptionService.apply).
     *
     * @param blocksById every block of the trip, already updated
     * @param dataAsOf   when the flight data behind this check arrived
     */
    public List<Connection> recheck(long tripId, Map<Long, Block> blocksById, Instant dataAsOf) {
        List<Connection> connections = connectionRepository.findByTripIdOrderByIdAsc(tripId);
        for (Connection connection : connections) {
            Block inbound = blocksById.get(connection.getInboundBlockId());
            Block outbound = blocksById.get(connection.getOutboundBlockId());
            applyCheck(connection, inbound, outbound, dataAsOf);
        }
        return connectionRepository.saveAll(connections);
    }

    /**
     * Same, in its own transaction: locks the trip row, loads the blocks and rechecks with the data as of now.
     * For changes that move no times (e.g. a flight phase, from C2) and for tests.
     */
    @Transactional
    public List<Connection> recheck(long tripId) {
        tripRepository.findByIdForUpdate(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        Map<Long, Block> blocksById = new HashMap<>();
        for (Block block : blockRepository.findByTripIdOrderByStartTimeAscIdAsc(tripId)) {
            blocksById.put(block.getId(), block);
        }
        return recheck(tripId, blocksById, clock.instant().truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * The trip's connections for the API (in id order; empty when the trip has none).
     *
     * @throws TripNotFoundException (404) unknown trip
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ConnectionResponse> connectionsOf(long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new TripNotFoundException(tripId);
        }
        Map<Long, Block> blocksById = new HashMap<>();
        for (Block block : blockRepository.findByTripIdOrderByStartTimeAscIdAsc(tripId)) {
            blocksById.put(block.getId(), block);
        }
        return connectionRepository.findByTripIdOrderByIdAsc(tripId).stream()
                .map(connection -> toResponse(connection, blocksById.get(connection.getInboundBlockId()),
                        blocksById.get(connection.getOutboundBlockId())))
                .toList();
    }

    /** Runs the checker and copies its verdict onto the row. Keeps a confirmed miss confirmed. */
    private void applyCheck(Connection connection, Block inbound, Block outbound, Instant dataAsOf) {
        ConnectionFacts facts = new ConnectionFacts();
        facts.missConfirmed = connection.getMissConfirmedAt() != null;
        ConnectionCheck check = checker.check(toLeg(inbound), toLeg(outbound), facts);

        connection.setAirport(check.airport);
        connection.setConnectionType(check.connectionType);
        connection.setTerminalChange(check.terminalChange);
        connection.setMctMinutes(check.mctMinutes);
        connection.setMctKind(check.mctKind);
        connection.setMctSource(check.mctSource);
        connection.setMctSourceUrl(check.mctSourceUrl);
        connection.setMctLastChecked(check.mctLastChecked);
        connection.setTransferMinutes(check.transferMinutes);
        connection.setTransferSource(check.transferSource);
        connection.setGateCloseMinutes(check.gateCloseMinutes);
        connection.setGateCloseKind(check.gateCloseKind);
        connection.setGateCloseSource(check.gateCloseSource);
        connection.setGateCloseSourceUrl(check.gateCloseSourceUrl);
        connection.setGateCloseLastChecked(check.gateCloseLastChecked);
        connection.setStatus(check.status);
        connection.setAvailableMinutes(check.availableMinutes);
        connection.setProjectedSlackMinutes(check.slackMinutes);
        connection.setBasedOnDemoValue(check.basedOnDemoValue);
        connection.setReason(check.reason);
        if (check.status == ConnectionStatus.CONFIRMED_MISSED && connection.getMissConfirmedAt() == null) {
            connection.setMissConfirmedAt(dataAsOf); // from now on a fact, even after both flights have landed
        }
        connection.setUpdatedAt(clock.instant().truncatedTo(ChronoUnit.SECONDS));
        connection.setDataAsOf(dataAsOf);
    }

    /**
     * A flight block as the checker sees it. The block's start/end are the latest times (the estimate after a delay),
     * its scheduled times the timetable.
     */
    static FlightLeg toLeg(Block flight) {
        FlightLeg leg = new FlightLeg();
        leg.flightNo = flight.getFlightNo();
        leg.depAirport = flight.getDepAirport();
        leg.arrAirport = flight.getArrAirport();
        leg.depTerminal = flight.getDepTerminal();
        leg.arrTerminal = flight.getArrTerminal();
        leg.depZone = ZoneId.of(flight.getStartZone());
        leg.arrZone = ZoneId.of(flight.getEndZone());
        leg.scheduledDep = flight.getScheduledStart();
        leg.scheduledArr = flight.getScheduledEnd();
        leg.estimatedDep = flight.getStartTime();
        leg.estimatedArr = flight.getEndTime();
        leg.phase = flight.getFlightPhase() != null ? flight.getFlightPhase() : FlightPhase.SCHEDULED;
        leg.cancelled = flight.getStatus() == BlockStatus.CANCELLED;
        return leg;
    }

    /** A FLIGHT block that knows its airports (rows from before V3 don't). */
    static boolean isFlightWithAirports(Block block) {
        return block != null && block.getType() == BlockType.FLIGHT && block.getDepAirport() != null
                && block.getArrAirport() != null;
    }

    private ConnectionResponse toResponse(Connection connection, Block inbound, Block outbound) {
        ConnectionResponse.Sourced mct = connection.getMctMinutes() == null ? null
                : new ConnectionResponse.Sourced(connection.getMctMinutes(), connection.getMctKind(),
                        connection.getMctSource(), connection.getMctSourceUrl(), connection.getMctLastChecked());
        ConnectionResponse.Sourced transfer = new ConnectionResponse.Sourced(connection.getTransferMinutes(),
                ValueKind.ESTIMATE, connection.getTransferSource(), null, null);
        ConnectionResponse.Sourced gateClose = new ConnectionResponse.Sourced(connection.getGateCloseMinutes(),
                connection.getGateCloseKind(), connection.getGateCloseSource(), connection.getGateCloseSourceUrl(),
                connection.getGateCloseLastChecked());
        String airportZone = inbound.getEndZone(); // the connection airport = where flight 1 lands
        return new ConnectionResponse(
                connection.getId(),
                connection.getTripId(),
                connection.getInboundBlockId(),
                connection.getOutboundBlockId(),
                inbound.getFlightNo(),
                outbound.getFlightNo(),
                connection.getAirport(),
                airportZone,
                connection.getConnectionType(),
                connection.getTerminalChange(),
                connection.getStatus(),
                connection.getStatus().certainty(),
                mapper.toApiTime(inbound.getEndTime(), airportZone),
                mapper.toApiTime(outbound.getStartTime(), airportZone),
                connection.getAvailableMinutes(),
                connection.getMctMinutes(),
                connection.getProjectedSlackMinutes(),
                mct,
                transfer,
                gateClose,
                connection.getTicketType(),
                connection.isBasedOnDemoValue(),
                connection.getReason(),
                mapper.toApiTime(connection.getMissConfirmedAt()),
                mapper.toApiTime(connection.getUpdatedAt()),
                mapper.toApiTime(connection.getDataAsOf()));
    }
}
