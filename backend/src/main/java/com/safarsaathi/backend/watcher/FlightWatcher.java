package com.safarsaathi.backend.watcher;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.AppProperties;
import com.safarsaathi.backend.config.ProviderStatus;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.BlockType;
import com.safarsaathi.backend.trip.Trip;
import com.safarsaathi.backend.trip.TripRepository;
import com.safarsaathi.backend.trip.TripStatus;

/**
 * The flight watcher: on a timer (every 30 s in demo mode, every 5 min in prod, see {@link WatcherConfig}) it asks
 * the {@link FlightStatusProvider} about every FLIGHT block of every trip that is not DONE, and hands each change to
 * {@link DisruptionService}, which runs the ripple engine and saves the result. The app then sees the new version.
 *
 * <p>Which provider: AeroDataBox when AERODATABOX_API_KEY is set, otherwise the mock. Chosen once at startup and
 * logged, matching the "flightStatus" row of the startup provider table.
 *
 * <p>One lock for everything that changes trips here: the timer's check, the demo buttons and the demo reset all
 * run inside {@code synchronized (lock)}, one at a time. Without it, the timer could pick up a delay that a demo
 * button has just reported, between the button's "report" and its own "check", and the button would get no answer.
 */
@Component
public class FlightWatcher {

    private static final Logger log = LoggerFactory.getLogger(FlightWatcher.class);

    private final Object lock = new Object();
    private final TripRepository tripRepository;
    private final BlockRepository blockRepository;
    private final DisruptionService disruptionService;
    private final FlightStatusProvider provider;

    public FlightWatcher(TripRepository tripRepository, BlockRepository blockRepository,
                         DisruptionService disruptionService, MockFlightStatusProvider mockProvider,
                         ProviderStatus providerStatus, AppProperties properties, ObjectMapper objectMapper,
                         Clock clock, Duration flightCheckInterval) {
        this.tripRepository = tripRepository;
        this.blockRepository = blockRepository;
        this.disruptionService = disruptionService;
        this.provider = chooseProvider(providerStatus, properties, mockProvider, objectMapper, clock);
        log.info("Flight watcher: checks the flights of every trip that is not DONE every {} (APP_MODE={}) with {}",
                readable(flightCheckInterval), properties.mode(), provider.name());
    }

    /**
     * AeroDataBox when the startup check says flight status is REAL (AERODATABOX_API_KEY is set), otherwise the mock.
     * Package-private and static so a test can check the choice without starting Spring.
     */
    static FlightStatusProvider chooseProvider(ProviderStatus providerStatus, AppProperties properties,
                                               MockFlightStatusProvider mockProvider, ObjectMapper objectMapper,
                                               Clock clock) {
        if (providerStatus.provider("flightStatus").mode() == ProviderStatus.Mode.REAL) {
            return new AeroDataBoxProvider(properties.providers().aerodatabox().apiKey(),
                    AeroDataBoxProvider.BASE_URL, objectMapper, clock, ZoneId.of(properties.zone()));
        }
        return mockProvider;
    }

    /** The provider the timer uses (the mock unless AERODATABOX_API_KEY is set). */
    public FlightStatusProvider provider() {
        return provider;
    }

    /**
     * The timer job. "#{@flightCheckInterval}" = ask Spring for the bean named flightCheckInterval (in
     * {@link WatcherConfig}): 30 seconds in demo mode, 5 minutes in prod mode. The first run waits one interval,
     * so the app has started and seeded its demo trips first. fixedDelay = the next run starts one interval after the
     * previous one FINISHED, so runs never overlap.
     */
    @Scheduled(initialDelayString = "#{@flightCheckInterval}", fixedDelayString = "#{@flightCheckInterval}")
    public void checkFlights() {
        synchronized (lock) {
            for (Trip trip : tripRepository.findAll(Sort.by("id"))) {
                if (trip.getStatus() == TripStatus.DONE) {
                    continue; // a finished trip has nothing left to disrupt
                }
                for (Long flightId : flightIds(trip.getId())) {
                    // Read the block again right before checking it: a change to an earlier flight of the same
                    // trip may just have changed it.
                    Optional<Block> flight = blockRepository.findById(flightId);
                    if (flight.isEmpty() || !canChange(flight.get())) {
                        continue;
                    }
                    try {
                        checkBlock(flight.get(), provider);
                    } catch (RuntimeException e) { // one bad flight must not stop the others, or the timer
                        log.warn("Flight watcher: could not apply the change to block {} of trip {}: {}",
                                flightId, trip.getId(), e.toString());
                    }
                }
            }
        }
    }

    /**
     * Checks one block right now with the given provider, and applies the change if there is one.
     * The demo buttons use this with the mock provider, so their answer shows the ripple at once.
     *
     * @return what the disruption did, or empty when the provider reports no change
     */
    public Optional<DisruptionResult> checkBlock(Block block, FlightStatusProvider source) {
        synchronized (lock) {
            Optional<FlightStatusChange> change = source.check(block);
            if (change.isEmpty()) {
                return Optional.empty();
            }
            DisruptionResult result = disruptionService.apply(block.getTripId(), block.getId(), change.get());
            log.info("Flight watcher: {} on block {} of trip {} ({}). Trip {} is now version {}.",
                    result.kind(), block.getId(), block.getTripId(), change.get().detail(), result.tripId(),
                    result.version());
            return Optional.of(result);
        }
    }

    /**
     * Runs {@code work} while no flight check is running, and keeps new checks waiting until it is done.
     * The demo controls use it for "report a change, then check at once" and for the reset.
     * (Java's {@code synchronized} may be entered again by the same thread, so {@code work} may call
     * {@link #checkBlock}.)
     */
    public <T> T runExclusively(Supplier<T> work) {
        synchronized (lock) {
            return work.get();
        }
    }

    /** The ids of the trip's FLIGHT blocks, in timeline order. */
    private List<Long> flightIds(long tripId) {
        return blockRepository.findByTripIdOrderByStartTimeAscIdAsc(tripId).stream()
                .filter(block -> block.getType() == BlockType.FLIGHT)
                .map(Block::getId)
                .toList();
    }

    /** Finished blocks (DONE, CANCELLED) cannot be delayed or cancelled again. */
    private static boolean canChange(Block block) {
        return block.getStatus() != BlockStatus.DONE && block.getStatus() != BlockStatus.CANCELLED;
    }

    /** "30 s" or "5 min" instead of Java's "PT30S". */
    private static String readable(Duration interval) {
        return interval.toSecondsPart() == 0 && interval.toMinutes() > 0
                ? interval.toMinutes() + " min"
                : interval.toSeconds() + " s";
    }
}
