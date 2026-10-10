package com.safarsaathi.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.data.remote.TripDto;

import org.junit.Test;

import java.time.Instant;

/**
 * Unit tests for {@link TripMapper}: server JSON -> Room rows. The JSON is parsed with Gson, exactly
 * as Retrofit does in the app, so these tests also check that the DTO field names match docs/api.md.
 * Run on the laptop: {@code ./gradlew testDebugUnitTest}.
 */
public class TripMapperTest {

    /** Shape of GET /api/trips/1 (docs/api.md), shortened to two blocks and one link. */
    private static final String TRIP_JSON = """
            {"id":1,"title":"Client visit: Mumbai to Bengaluru","purpose":"BUSINESS","persona":"BUSINESS",
             "language":"en","travellers":1,"status":"PLANNED","version":3,
             "createdAt":"2026-10-10T17:30:05+05:30","updatedAt":"2026-10-10T18:02:00+05:30",
             "blocks":[
               {"id":1,"tripId":1,"type":"FLIGHT","title":"Flight IJ 512 Mumbai to Bengaluru",
                "startTime":"2026-10-11T08:00:00+05:30","endTime":"2026-10-11T09:45:00+05:30",
                "locationName":"Mumbai Airport T2 (BOM)","lat":19.0896,"lng":72.8656,
                "provider":"MOCK_AIR","providerRef":"K7Q2ZM","flex":"FIXED","anchor":false,"status":"OK",
                "freeChangeUntilMinutes":null,"changeFeeInr":2500,"notes":"Arrives at Bengaluru T1 (BLR)."},
               {"id":4,"tripId":1,"type":"MEETING","title":"Client meeting in Whitefield",
                "startTime":"2026-10-11T12:00:00+05:30","endTime":"2026-10-11T13:00:00+05:30",
                "locationName":null,"lat":null,"lng":null,
                "provider":"GOOGLE_CALENDAR","providerRef":null,"flex":"ASK_USER","anchor":true,
                "status":"NEEDS_USER","freeChangeUntilMinutes":-30,"changeFeeInr":0,"notes":null}
             ],
             "links":[{"id":3,"tripId":1,"fromBlockId":1,"toBlockId":4,"minGapMinutes":0,
                       "travelMinutes":75,"travelMode":"CAB"}]}
            """;

    private static TripMapper.MappedTrip map(String json) {
        return TripMapper.fromDto(new Gson().fromJson(json, TripDto.class));
    }

    @Test
    public void tripFields_areCopied_andTimesBecomeExactMoments() {
        TripEntity trip = map(TRIP_JSON).trip;

        assertEquals(1, trip.id);
        assertEquals("Client visit: Mumbai to Bengaluru", trip.title);
        assertEquals("BUSINESS", trip.purpose);
        assertEquals("BUSINESS", trip.persona);
        assertEquals("en", trip.language);
        assertEquals(1, trip.travellers);
        assertEquals("PLANNED", trip.status);
        assertEquals(3, trip.version);
        // 17:30:05 in India (+05:30) is 12:00:05 UTC.
        assertEquals(Instant.parse("2026-10-10T12:00:05Z"), trip.createdAt);
        assertEquals(Instant.parse("2026-10-10T12:32:00Z"), trip.updatedAt);
        assertNull("set by the repository when saving, not by the mapper", trip.lastSyncedAt);
    }

    @Test
    public void blockFields_areCopied_includingNulls() {
        TripMapper.MappedTrip mapped = map(TRIP_JSON);
        assertEquals(2, mapped.blocks.size());

        BlockEntity flight = mapped.blocks.get(0);
        assertEquals(1, flight.id);
        assertEquals(1, flight.tripId);
        assertEquals("FLIGHT", flight.type);
        assertEquals("Flight IJ 512 Mumbai to Bengaluru", flight.title);
        assertEquals(Instant.parse("2026-10-11T02:30:00Z"), flight.startTime);
        assertEquals(Instant.parse("2026-10-11T04:15:00Z"), flight.endTime);
        assertEquals("Mumbai Airport T2 (BOM)", flight.locationName);
        assertEquals(19.0896, flight.lat, 0.0);
        assertEquals(72.8656, flight.lng, 0.0);
        assertEquals("MOCK_AIR", flight.provider);
        assertEquals("K7Q2ZM", flight.providerRef);
        assertEquals("FIXED", flight.flex);
        assertFalse(flight.anchor);
        assertEquals("OK", flight.status);
        assertNull("null = no free-change window", flight.freeChangeUntilMinutes);
        assertEquals(2500, flight.changeFeeInr);
        assertEquals("Arrives at Bengaluru T1 (BLR).", flight.notes);

        BlockEntity meeting = mapped.blocks.get(1);
        assertTrue(meeting.anchor);
        assertEquals("NEEDS_USER", meeting.status);
        assertEquals(Integer.valueOf(-30), meeting.freeChangeUntilMinutes);
        assertNull(meeting.locationName);
        assertNull(meeting.lat);
        assertNull(meeting.lng);
        assertNull(meeting.providerRef);
        assertNull(meeting.notes);
    }

    @Test
    public void linkFields_areCopied() {
        LinkEntity link = map(TRIP_JSON).links.get(0);

        assertEquals(3, link.id);
        assertEquals(1, link.tripId);
        assertEquals(1, link.fromBlockId);
        assertEquals(4, link.toBlockId);
        assertEquals(0, link.minGapMinutes);
        assertEquals(75, link.travelMinutes);
        assertEquals("CAB", link.travelMode);
    }

    @Test
    public void utcTimesWithZ_areAcceptedToo() {
        String json = TRIP_JSON.replace("2026-10-10T17:30:05+05:30", "2026-10-10T12:00:05Z");

        assertEquals(Instant.parse("2026-10-10T12:00:05Z"), map(json).trip.createdAt);
    }

    @Test
    public void missingBlocksAndLinks_giveEmptyLists() {
        TripMapper.MappedTrip mapped = map(
                "{\"id\":2,\"title\":\"Empty\",\"version\":1,\"createdAt\":\"2026-10-10T17:30:05+05:30\"}");

        assertTrue(mapped.blocks.isEmpty());
        assertTrue(mapped.links.isEmpty());
        assertEquals("updatedAt falls back to createdAt", mapped.trip.createdAt, mapped.trip.updatedAt);
    }

    @Test
    public void missingVersion_isRefused() {
        String json = TRIP_JSON.replace("\"version\":3,", "");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> map(json));
        assertTrue(error.getMessage(), error.getMessage().contains("version"));
    }

    @Test
    public void unreadableBlockTime_isRefused_withTheBlockIdInTheMessage() {
        String json = TRIP_JSON.replace("2026-10-11T12:00:00+05:30", "tomorrow at noon");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> map(json));
        assertTrue(error.getMessage(), error.getMessage().contains("block 4 startTime"));
    }

    @Test
    public void emptyAnswer_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> TripMapper.fromDto(null));
    }
}
