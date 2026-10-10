package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.data.SyncDecision;
import com.safarsaathi.app.data.local.TripEntity;

import org.junit.Test;

import java.time.Instant;

public class DemoTripOnPhoneTest {

    @Test
    public void demoTrip_isTomorrow_atTheBackendDemoTimes_andSaysItIsADemo() {
        Instant now = AskFixtures.ist("2026-10-11T21:30");
        DemoTripOnPhone demo = DemoTripOnPhone.build(now);

        assertEquals(AppConfig.DEMO_TRIP_ID, demo.trip.id);
        assertTrue(demo.trip.title.contains("demo"));
        assertEquals(6, demo.blocks.size());
        assertEquals(AskFixtures.ist("2026-10-12T08:00"), demo.blocks.get(0).startTime);
        assertEquals("Flight IJ 512 Mumbai to Bengaluru", demo.blocks.get(0).title);

        // Ask answers from it like from a backend copy.
        Answer answer = AskEngine.standard().ask("Where is my gate?", AskLanguage.EN, LanguageHint.NONE,
                TripFacts.from(demo.trip, demo.blocks, null, now));
        assertEquals("The gate for IJ 512 is not announced yet. Your flight departs at 08:00 tomorrow.", answer.text);
    }

    @Test
    public void theNextRealSync_replacesTheDemoTrip() {
        DemoTripOnPhone demo = DemoTripOnPhone.build(AskFixtures.ist("2026-10-11T21:30"));
        TripEntity fromServer = new TripEntity();
        fromServer.id = AppConfig.DEMO_TRIP_ID;
        fromServer.version = 1;
        fromServer.createdAt = AskFixtures.ist("2026-10-10T10:00");
        assertNotEquals(fromServer.createdAt, demo.trip.createdAt);
        assertTrue(SyncDecision.shouldReplace(demo.trip, fromServer));
    }
}
