package com.safarsaathi.backend.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.AppProperties;
import com.safarsaathi.backend.demo.DemoTripFile.SeedBlock;
import com.safarsaathi.backend.demo.DemoTripFile.SeedLink;
import com.safarsaathi.backend.demo.DemoTripFile.SeedTrip;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.Link;
import com.safarsaathi.backend.trip.LinkRepository;
import com.safarsaathi.backend.trip.Trip;
import com.safarsaathi.backend.trip.TripRepository;

/**
 * Saves the two demo trips from {@code mock-data/demo-trips.json} into the database.
 *
 * <p>At startup ({@link ApplicationRunner}: Spring calls {@link #run} once the app is up) it saves them, but ONLY when
 * the database has no trips at all, so a restart never duplicates or overwrites your data. On an empty database the
 * ids follow the file: trip 1 = the business trip (blocks 1-6, links 1-5), trip 2 = the Leh trip (blocks 7-11,
 * links 6-9). The Android app and the demo buttons rely on those numbers.
 *
 * <p>The file has no dates, only "day 0, 08:00". The trip day is worked out here: today in India (from the shared
 * {@link Clock}) + safarsaathi.seed.day-offset days (default 1 = tomorrow). So the demo never goes stale.
 *
 * <p>Where the file comes from: Maven copies the repo-root mock-data folder into the jar as {@code mock-data/} (see
 * the {@code <resources>} part of pom.xml), so {@code classpath:mock-data/demo-trips.json} works everywhere: with
 * ./mvnw spring-boot:run, in the tests and in a deployed jar.
 */
@Component
public class DemoTripSeeder implements ApplicationRunner {

    /** The seed file's place on the classpath (inside the jar, or target/classes while developing). */
    static final String SEED_FILE = "mock-data/demo-trips.json";

    private static final Logger log = LoggerFactory.getLogger(DemoTripSeeder.class);

    private final TripRepository tripRepository;
    private final BlockRepository blockRepository;
    private final LinkRepository linkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final Clock clock;

    public DemoTripSeeder(TripRepository tripRepository, BlockRepository blockRepository,
                          LinkRepository linkRepository, JdbcTemplate jdbcTemplate,
                          TransactionTemplate transactionTemplate, ObjectMapper objectMapper,
                          AppProperties properties, Clock clock) {
        this.tripRepository = tripRepository;
        this.blockRepository = blockRepository;
        this.linkRepository = linkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Called by Spring Boot once at startup. */
    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    /**
     * Saves the demo trips if seeding is on (safarsaathi.seed.enabled) and the database has no trips yet.
     *
     * <p>Why {@code transactionTemplate.execute(...)} instead of {@code @Transactional}? {@link #run} calls this method
     * from inside the same class, and Spring's {@code @Transactional} only works when the call comes from ANOTHER
     * class. The template always works: everything inside the lambda is ONE transaction, so either all rows are
     * saved or, on any error, none.
     *
     * @return how many trips were saved: 2, or 0 when nothing was done
     */
    public int seedIfEmpty() {
        if (!properties.seed().enabled()) {
            log.info("Demo trips not loaded: safarsaathi.seed.enabled is false");
            return 0;
        }
        Integer saved = transactionTemplate.execute(status -> {
            long existing = tripRepository.count();
            if (existing > 0) {
                log.info("Demo trips not loaded again: the database already has {} trip(s)", existing);
                return 0;
            }
            return saveDemoTrips();
        });
        return saved == null ? 0 : saved; // execute() is declared "may return null"; our lambda never does
    }

    /**
     * Deletes EVERY trip (with its blocks and links) and saves the demo trips again: fresh dates, a new
     * {@code createdAt}, version 1 and every block OK. Used by {@code POST /api/demo/reset} (Phase 2).
     * Works even when safarsaathi.seed.enabled is false, because someone asked for it explicitly.
     *
     * <p>The id counters start again at 1, so the reloaded trips get the same ids as on an empty database
     * (trip 1, blocks 1-11, links 1-9). Otherwise the app, which always opens trip 1, would find nothing.
     *
     * @return how many trips were saved (2)
     */
    public int deleteAllAndReseed() {
        Integer saved = transactionTemplate.execute(status -> {
            // One DELETE for all trips. The database deletes their blocks and links itself: the tables say
            // ON DELETE CASCADE (see V1__create_trips_blocks_links.sql).
            tripRepository.deleteAllInBatch();
            restartIdCounters();
            return saveDemoTrips();
        });
        return saved == null ? 0 : saved;
    }

    /**
     * The trip day: today in India + dayOffset days. Package-private and static so a test can check it directly.
     * "Today" is taken in {@code zone}, not in the server's own zone: at 01:30 in India it is still yesterday in UTC.
     */
    static LocalDate tripDay(Clock clock, ZoneId zone, int dayOffset) {
        return LocalDate.now(clock.withZone(zone)).plusDays(dayOffset);
    }

    /**
     * Makes the next new trip, block and link get id 1 again. Plain SQL, because JPA has no command for this.
     * The same statement works on PostgreSQL and on H2 (the test database). Only safe right after deleting every
     * row, which {@link #deleteAllAndReseed} does.
     * On PostgreSQL the whole reset is one transaction. H2 commits by itself at every ALTER TABLE, so there the
     * delete is saved first; that only matters in tests.
     */
    private void restartIdCounters() {
        jdbcTemplate.execute("ALTER TABLE links ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.execute("ALTER TABLE blocks ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.execute("ALTER TABLE trips ALTER COLUMN id RESTART WITH 1");
    }

    /** Reads the file and saves every trip in file order. Must run inside a transaction (see callers). */
    private int saveDemoTrips() {
        DemoTripFile file = readSeedFile();
        ZoneId zone = ZoneId.of(properties.zone());
        LocalDate tripDay = tripDay(clock, zone, properties.seed().dayOffset());
        // Whole seconds, like the API shows it, so the stored value and the JSON value are exactly the same.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        for (SeedTrip seedTrip : file.trips()) {
            saveTrip(seedTrip, tripDay, zone, now);
        }
        return file.trips().size();
    }

    private DemoTripFile readSeedFile() {
        ClassPathResource resource = new ClassPathResource(SEED_FILE);
        if (!resource.exists()) {
            throw new IllegalStateException("Demo seed file classpath:" + SEED_FILE + " not found. Maven copies it "
                    + "from the repo-root mock-data folder (see pom.xml): rebuild with ./mvnw clean package");
        }
        try (InputStream in = resource.getInputStream()) {
            // FAIL_ON_UNKNOWN_PROPERTIES: a misspelt field name (e.g. "freeChangeUntil") stops the app with an error
            // that names it, instead of being skipped silently and leaving the value empty.
            return objectMapper.readerFor(DemoTripFile.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SEED_FILE + ": " + e.getMessage(), e);
        }
    }

    /**
     * Saves one trip, then its blocks, then its links, each in file order. With IDENTITY ids the database hands out
     * each id at the moment of the save, so file order = id order.
     */
    private void saveTrip(SeedTrip seed, LocalDate tripDay, ZoneId zone, Instant now) {
        Trip trip = new Trip();
        trip.setTitle(seed.title());
        trip.setPurpose(seed.purpose());
        trip.setPersona(seed.persona());
        trip.setLanguage(seed.language());
        trip.setTravellers(seed.travellers());
        trip.setStatus(seed.status());
        trip.setVersion(1); // every plan change adds 1 (from Phase 2)
        trip.setCreatedAt(now);
        trip.setUpdatedAt(now);
        tripRepository.save(trip); // the database fills in trip.getId()

        // Links name their blocks by key ("flight"), the database by id: remember which key got which id.
        Map<String, Long> blockIdsByKey = new HashMap<>();
        for (SeedBlock seedBlock : seed.blocks()) {
            Block block = toBlock(seedBlock, trip.getId(), tripDay, zone, seed.key());
            blockRepository.save(block);
            if (blockIdsByKey.put(seedBlock.key(), block.getId()) != null) {
                throw new IllegalStateException(SEED_FILE + ": trip '" + seed.key() + "' has two blocks with key '"
                        + seedBlock.key() + "'");
            }
        }

        for (SeedLink seedLink : seed.links()) {
            Link link = new Link();
            link.setTripId(trip.getId());
            link.setFromBlockId(blockId(blockIdsByKey, seedLink.from(), seed.key()));
            link.setToBlockId(blockId(blockIdsByKey, seedLink.to(), seed.key()));
            link.setMinGapMinutes(seedLink.minGapMinutes());
            link.setTravelMinutes(seedLink.travelMinutes());
            link.setTravelMode(seedLink.travelMode());
            linkRepository.save(link);
        }

        log.info("Demo trip {} saved: \"{}\" ({} blocks, {} links, trip day {})",
                trip.getId(), trip.getTitle(), seed.blocks().size(), seed.links().size(), tripDay);
    }

    private static Block toBlock(SeedBlock seed, Long tripId, LocalDate tripDay, ZoneId zone, String tripKey) {
        if (seed.start() == null || seed.end() == null) {
            throw new IllegalStateException(SEED_FILE + ": block '" + seed.key() + "' of trip '" + tripKey
                    + "' needs both \"start\" and \"end\"");
        }
        Instant start = atTripDay(tripDay, seed.day(), seed.start(), zone);
        Integer endDayInFile = seed.endDay(); // usually missing: then the block ends on the day it starts
        int endDay = endDayInFile != null ? endDayInFile : seed.day();
        Instant end = atTripDay(tripDay, endDay, seed.end(), zone);
        if (end.isBefore(start)) {
            throw new IllegalStateException(SEED_FILE + ": block '" + seed.key() + "' of trip '" + tripKey
                    + "' ends before it starts (add \"endDay\" if it ends on a later day)");
        }

        Block block = new Block();
        block.setTripId(tripId);
        block.setType(seed.type());
        block.setTitle(seed.title());
        block.setStartTime(start);
        block.setEndTime(end);
        block.setLocationName(seed.locationName());
        block.setLat(seed.lat());
        block.setLng(seed.lng());
        block.setProvider(seed.provider());
        block.setProviderRef(seed.providerRef());
        block.setFlex(seed.flex());
        block.setAnchor(seed.anchor());
        block.setStatus(BlockStatus.OK); // every demo block starts fine; the ripple engine changes this
        block.setFreeChangeUntilMinutes(seed.freeChangeUntilMinutes());
        block.setChangeFeeInr(seed.changeFeeInr());
        block.setNotes(seed.notes());
        return block;
    }

    /** "Day 1 at 08:00" as an exact moment: the date is the trip day + {@code day}, the clock time is Indian time. */
    private static Instant atTripDay(LocalDate tripDay, int day, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(tripDay.plusDays(day), time, zone).toInstant();
    }

    private static Long blockId(Map<String, Long> blockIdsByKey, String key, String tripKey) {
        Long id = blockIdsByKey.get(key);
        if (id == null) {
            throw new IllegalStateException(SEED_FILE + ": a link of trip '" + tripKey + "' points at block '"
                    + key + "', but that trip has no block with this key");
        }
        return id;
    }
}
