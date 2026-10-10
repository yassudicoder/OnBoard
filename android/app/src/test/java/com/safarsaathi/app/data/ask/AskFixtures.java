package com.safarsaathi.app.data.ask;

import com.safarsaathi.app.data.airport.MapJson;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.routing.map.AirportMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.safarsaathi.app.AppConfig;

/**
 * Test data for Ask OnBoard: the demo trip's flight IJ 512 (Mumbai 08:00 to Bengaluru 09:45, Indian time, on
 * 12 October 2026), asked about at 06:00 the same morning, plus the real bundled terminal map.
 */
final class AskFixtures {

    /** 06:00 in India on the flight day. */
    static final Instant NOW = ist("2026-10-12T06:00");
    /** When the phone last heard from the server: 05:42. */
    static final Instant SYNCED = ist("2026-10-12T05:42");

    private AskFixtures() {
    }

    static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(AppConfig.TRIP_ZONE).toInstant();
    }

    static TripEntity trip() {
        TripEntity trip = new TripEntity();
        trip.id = 1;
        trip.title = "Client visit: Mumbai to Bengaluru";
        trip.version = 1;
        trip.lastSyncedAt = SYNCED;
        return trip;
    }

    static BlockEntity block(long id, String type, String title, String start, String end, String status) {
        BlockEntity block = new BlockEntity();
        block.id = id;
        block.tripId = 1;
        block.type = type;
        block.title = title;
        block.startTime = ist(start);
        block.endTime = ist(end);
        block.status = status;
        block.plannedStartTime = block.startTime;
        block.plannedEndTime = block.endTime;
        return block;
    }

    static BlockEntity flight() {
        BlockEntity flight = block(10, BlockEntity.TYPE_FLIGHT, "Flight IJ 512 Mumbai to Bengaluru",
                "2026-10-12T08:00", "2026-10-12T09:45", BlockEntity.STATUS_OK);
        flight.locationName = "Mumbai Airport T2 (BOM)";
        return flight;
    }

    static BlockEntity bagClaim() {
        return block(11, "BAG_CLAIM", "Collect bags at Bengaluru T1", "2026-10-12T09:45", "2026-10-12T10:05",
                BlockEntity.STATUS_OK);
    }

    /** The demo trip as the phone saves it: the flight and the bag claim after it. */
    static TripFacts facts(BlockEntity... blocks) {
        List<BlockEntity> list = new ArrayList<>(Arrays.asList(blocks));
        return TripFacts.from(trip(), list, map(), NOW);
    }

    static TripFacts demoFacts() {
        return facts(flight(), bagClaim());
    }

    /** The real bundled map (repo-root mock-data; unit tests run with android/app as the working folder). */
    static AirportMap map() {
        try {
            return MapJson.parse(MapJson.combine(read("map.json"), read("areas.geojson"))).map;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(String file) throws IOException {
        return new String(Files.readAllBytes(Paths.get("../../mock-data/airports/BOM-T2/" + file)),
                StandardCharsets.UTF_8);
    }
}
