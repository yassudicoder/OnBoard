package com.safarsaathi.app.testing;

import com.safarsaathi.app.data.TripMapper;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;
import com.safarsaathi.app.data.local.TripEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ready-made trips for instrumented tests: demo trip 1 from mock-data/demo-trips.json (trip day 11 Oct 2026). */
public final class TestTrips {

    public static final long TRIP_ID = 1;
    public static final String FLIGHT_TITLE = "Flight IJ 512 Mumbai to Bengaluru";
    public static final String MEETING_TITLE = "Client meeting in Whitefield";
    public static final String DINNER_TITLE = "Team dinner at Tandoor House";

    private TestTrips() {
    }

    /** Demo trip 1 with every block OK, as the backend seeds it (version 1). */
    public static TripMapper.MappedTrip demoTrip1() {
        TripEntity trip = new TripEntity();
        trip.id = TRIP_ID;
        trip.title = "Client visit: Mumbai to Bengaluru";
        trip.purpose = "BUSINESS";
        trip.persona = "BUSINESS";
        trip.language = "en";
        trip.travellers = 1;
        trip.status = "PLANNED";
        trip.version = 1;
        trip.createdAt = Instant.parse("2026-10-10T12:00:05Z");
        trip.updatedAt = trip.createdAt;
        trip.lastSyncedAt = Instant.parse("2026-10-10T12:12:00Z"); // 17:42 in India

        List<BlockEntity> blocks = new ArrayList<>();
        blocks.add(block(1, "FLIGHT", FLIGHT_TITLE, "08:00", "09:45", "FIXED", false));
        blocks.add(block(2, "BAG_CLAIM", "Collect bags at Bengaluru T1", "09:45", "10:05", "AUTO_SHIFT", false));
        blocks.add(block(3, "CAB", "Cab pickup to the Whitefield office", "10:05", "10:05", "AUTO_SHIFT", false));
        blocks.add(block(4, "MEETING", MEETING_TITLE, "12:00", "13:00", "ASK_USER", true));
        blocks.add(block(5, "HOTEL", "Hotel check-in: Whitefield Residency", "14:00", "14:15", "AUTO_SHIFT", false));
        blocks.add(block(6, "RESTAURANT", DINNER_TITLE, "20:00", "21:30", "AUTO_SHIFT", false));

        List<LinkEntity> links = new ArrayList<>();
        links.add(link(1, 1, 2, 0, "NONE"));
        links.add(link(2, 2, 3, 0, "WALK"));
        links.add(link(3, 3, 4, 75, "CAB"));
        links.add(link(4, 4, 5, 40, "CAB"));
        links.add(link(5, 5, 6, 20, "CAB"));
        return new TripMapper.MappedTrip(trip, blocks, links);
    }

    private static BlockEntity block(long id, String type, String title, String start, String end,
                                     String flex, boolean anchor) {
        BlockEntity block = new BlockEntity();
        block.id = id;
        block.tripId = TRIP_ID;
        block.type = type;
        block.title = title;
        block.startTime = OffsetDateTime.parse("2026-10-11T" + start + ":00+05:30").toInstant();
        block.endTime = OffsetDateTime.parse("2026-10-11T" + end + ":00+05:30").toInstant();
        block.locationName = "Place of block " + id;
        block.provider = "NONE";
        block.flex = flex;
        block.anchor = anchor;
        block.status = BlockEntity.STATUS_OK;
        return block;
    }

    private static LinkEntity link(long id, long from, long to, int travelMinutes, String mode) {
        LinkEntity link = new LinkEntity();
        link.id = id;
        link.tripId = TRIP_ID;
        link.fromBlockId = from;
        link.toBlockId = to;
        link.travelMinutes = travelMinutes;
        link.travelMode = mode;
        return link;
    }
}
