package com.safarsaathi.app.data.ask;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DEBUG BUILDS ONLY: the backend's demo trip 1 (mock-data/demo-trips.json, "Client visit: Mumbai to Bengaluru",
 * flight IJ 512 at 08:00 tomorrow), built on the phone, so Ask OnBoard's trip answers can be shown on an emulator or
 * a phone without the backend. Same invented demo data as the backend's seed, with the same "trip day = tomorrow"
 * rule; the trip title says it was loaded on the phone.
 *
 * <p>Saved like a server copy (TripDao.replaceTrip). Its createdAt is the moment it was loaded, so the next real sync
 * with the backend replaces it (SyncDecision: a different createdAt = new data). Plain Java, checked by DemoTripOnPhoneTest.
 */
public final class DemoTripOnPhone {

    public static final String TITLE = "Client visit: Mumbai to Bengaluru (demo, loaded on this phone)";

    public final TripEntity trip;
    public final List<BlockEntity> blocks;

    private DemoTripOnPhone(TripEntity trip, List<BlockEntity> blocks) {
        this.trip = trip;
        this.blocks = blocks;
    }

    /** The demo trip with its day = tomorrow (in Indian time) as seen at {@code now}. */
    public static DemoTripOnPhone build(Instant now) {
        LocalDate day = now.atZone(AppConfig.TRIP_ZONE).toLocalDate().plusDays(1);
        TripEntity trip = new TripEntity();
        trip.id = AppConfig.DEMO_TRIP_ID;
        trip.title = TITLE;
        trip.purpose = "BUSINESS";
        trip.persona = "BUSINESS";
        trip.language = "en";
        trip.travellers = 1;
        trip.status = "PLANNED";
        trip.version = 1;
        trip.createdAt = now;
        trip.updatedAt = now;
        trip.lastSyncedAt = now;

        List<BlockEntity> blocks = new ArrayList<>();
        blocks.add(block(1, "FLIGHT", "Flight IJ 512 Mumbai to Bengaluru", day, "08:00", "09:45",
                "Mumbai Airport T2 (BOM)", 19.0896, 72.8656, "MOCK_AIR", "K7Q2ZM", "FIXED", false));
        blocks.add(block(2, "BAG_CLAIM", "Collect bags at Bengaluru T1", day, "09:45", "10:05",
                "Bengaluru Airport T1 (BLR), baggage belt", 13.1989, 77.7068, "NONE", null, "AUTO_SHIFT", false));
        blocks.add(block(3, "CAB", "Cab pickup to the Whitefield office", day, "10:05", "10:05",
                "Bengaluru Airport T1 pickup zone", 13.1993, 77.7074, "MOCK_CAB", "CAB-BLR-4471", "AUTO_SHIFT", false));
        blocks.add(block(4, "MEETING", "Client meeting in Whitefield", day, "12:00", "13:00",
                "Client office, ITPL Main Road, Whitefield", 12.9857, 77.7365, "GOOGLE_CALENDAR",
                "evt-demo-client-meeting", "ASK_USER", true));
        blocks.add(block(5, "HOTEL", "Hotel check-in: Whitefield Residency", day, "14:00", "14:15",
                "Whitefield Residency, Whitefield", 12.9698, 77.7499, "MOCK_STAY", "STAY-WFR-2231", "AUTO_SHIFT", false));
        blocks.add(block(6, "RESTAURANT", "Team dinner at Tandoor House", day, "20:00", "21:30",
                "Tandoor House, Whitefield", 12.9784, 77.7408, "MOCK_TABLE", "TBL-TH-0912", "AUTO_SHIFT", false));
        return new DemoTripOnPhone(trip, blocks);
    }

    private static BlockEntity block(long id, String type, String title, LocalDate day, String start, String end,
                                     String place, double lat, double lng, String provider, String providerRef,
                                     String flex, boolean anchor) {
        BlockEntity block = new BlockEntity();
        block.id = id;
        block.tripId = AppConfig.DEMO_TRIP_ID;
        block.type = type;
        block.title = title;
        block.startTime = at(day, start);
        block.endTime = at(day, end);
        block.locationName = place;
        block.lat = lat;
        block.lng = lng;
        block.provider = provider;
        block.providerRef = providerRef;
        block.flex = flex;
        block.anchor = anchor;
        block.status = BlockEntity.STATUS_OK;
        // A trip seen at version 1: its times ARE the original plan (same rule as PlannedTimes).
        block.plannedStartTime = block.startTime;
        block.plannedEndTime = block.endTime;
        return block;
    }

    private static Instant at(LocalDate day, String hoursMinutes) {
        return day.atTime(LocalTime.parse(hoursMinutes)).atZone(AppConfig.TRIP_ZONE).toInstant();
    }
}
