package com.safarsaathi.app.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.data.TripMapper;
import com.safarsaathi.app.testing.TestTrips;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the real Room database code (TripDao) on a phone/emulator, in a throw-away in-memory database:
 * {@code ./gradlew connectedDebugAndroidTest}.
 */
@RunWith(AndroidJUnit4.class)
public class TripDaoTest {

    private AppDatabase database;
    private TripDao dao;

    @Before
    public void createDatabase() {
        // In memory: gone when the test ends. allowMainThreadQueries only because tests may block.
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.tripDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void replaceTrip_savesEverything_andBlocksComeBackInTimeOrder() {
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        List<BlockEntity> shuffled = new ArrayList<>(trip.blocks);
        Collections.reverse(shuffled);

        dao.replaceTrip(trip.trip, shuffled, trip.links);

        assertEquals(trip.trip, dao.getTrip(TestTrips.TRIP_ID));
        assertEquals("blocks ordered by start time, then id", trip.blocks, dao.getBlocks(TestTrips.TRIP_ID));
        assertEquals(trip.links, dao.getLinks(TestTrips.TRIP_ID));
    }

    @Test
    public void sameStartTime_isOrderedById() {
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        // Block 2 (bags) gets the same start as block 1 (flight): id decides.
        trip.blocks.get(1).startTime = trip.blocks.get(0).startTime;
        dao.replaceTrip(trip.trip, trip.blocks, trip.links);

        List<BlockEntity> saved = dao.getBlocks(TestTrips.TRIP_ID);

        assertEquals(1, saved.get(0).id);
        assertEquals(2, saved.get(1).id);
    }

    @Test
    public void replaceTrip_again_dropsBlocksAndLinksTheServerNoLongerHas() {
        TripMapper.MappedTrip first = TestTrips.demoTrip1();
        dao.replaceTrip(first.trip, first.blocks, first.links);

        // After /api/demo/reset the server may send new ids: here only 2 blocks and 1 link remain.
        TripMapper.MappedTrip second = TestTrips.demoTrip1();
        second.trip.version = 1;
        second.trip.createdAt = Instant.parse("2026-10-10T13:15:00Z");
        List<BlockEntity> newBlocks = new ArrayList<>(second.blocks.subList(0, 2));
        newBlocks.get(0).id = 12;
        newBlocks.get(1).id = 13;
        List<LinkEntity> newLinks = new ArrayList<>(second.links.subList(0, 1));
        newLinks.get(0).id = 9;
        newLinks.get(0).fromBlockId = 12;
        newLinks.get(0).toBlockId = 13;
        dao.replaceTrip(second.trip, newBlocks, newLinks);

        assertEquals(second.trip.createdAt, dao.getTrip(TestTrips.TRIP_ID).createdAt);
        List<BlockEntity> savedBlocks = dao.getBlocks(TestTrips.TRIP_ID);
        assertEquals(2, savedBlocks.size());
        assertEquals(12, savedBlocks.get(0).id);
        assertEquals(13, savedBlocks.get(1).id);
        assertEquals(1, dao.getLinks(TestTrips.TRIP_ID).size());
    }

    @Test
    public void replaceTrip_leavesOtherTripsAlone() {
        TripMapper.MappedTrip trip1 = TestTrips.demoTrip1();
        dao.replaceTrip(trip1.trip, trip1.blocks, trip1.links);

        TripEntity trip2 = new TripEntity();
        trip2.id = 2;
        trip2.title = "Leh holiday via Delhi";
        trip2.version = 1;
        dao.replaceTrip(trip2, Collections.emptyList(), Collections.emptyList());

        assertEquals(6, dao.getBlocks(TestTrips.TRIP_ID).size());
        assertEquals(5, dao.getLinks(TestTrips.TRIP_ID).size());
        assertEquals("Leh holiday via Delhi", dao.getTrip(2).title);
    }

    @Test
    public void updateLastSyncedAt_onlyChangesThatColumn() {
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        dao.replaceTrip(trip.trip, trip.blocks, trip.links);
        Instant now = Instant.parse("2026-10-10T12:30:00Z");

        dao.updateLastSyncedAt(TestTrips.TRIP_ID, now);

        TripEntity saved = dao.getTrip(TestTrips.TRIP_ID);
        assertEquals(now, saved.lastSyncedAt);
        assertEquals(trip.trip.version, saved.version);
        assertEquals(trip.blocks, dao.getBlocks(TestTrips.TRIP_ID));
    }

    @Test
    public void unknownTrip_isNull_withNoBlocks() {
        assertNull(dao.getTrip(42));
        assertTrue(dao.getBlocks(42).isEmpty());
    }
}
