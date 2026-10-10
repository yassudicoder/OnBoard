package com.safarsaathi.app.ui.trip;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.TripMapper;
import com.safarsaathi.app.data.TripRepository;
import com.safarsaathi.app.data.local.AppDatabase;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.testing.LocalNetworkAccess;
import com.safarsaathi.app.testing.OfflineApi;
import com.safarsaathi.app.testing.Screens;
import com.safarsaathi.app.testing.TestTrips;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UI test of the PARKED trip status screen (the old Home tab; Espresso, on an emulator or phone). It is opened
 * directly through the navigation graph, so it is tested whatever feature.tripTimeline says. Like TripFragmentTest, the screen gets an in-memory
 * database through the ServiceLocator test hook and a backend that is always offline, so every check below is
 * about what the saved plan says, with or without a real backend running.
 */
@RunWith(AndroidJUnit4.class)
public class TripStatusFragmentTest {

    private static final String BAGS_TITLE = "Collect bags at Bengaluru T1";

    private AppDatabase database;
    private ExecutorService ioExecutor;

    /** Makes the app use a fresh in-memory database holding this trip (null = nothing saved yet). */
    private void useSavedTrip(@Nullable TripMapper.MappedTrip trip) {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        if (trip != null) {
            database.tripDao().replaceTrip(trip.trip, trip.blocks, trip.links);
        }
        ioExecutor = Executors.newSingleThreadExecutor();
        TripRepository repository = new TripRepository(database.tripDao(), new OfflineApi(), ioExecutor,
                Clock.systemUTC());
        ServiceLocator.get().setTripRepositoryForTests(repository);
    }

    /** Demo trip 1 as the phone saves it at version 1: every block's current times are its planned times. */
    private static TripMapper.MappedTrip seededTrip() {
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        for (BlockEntity block : trip.blocks) {
            block.plannedStartTime = block.startTime;
            block.plannedEndTime = block.endTime;
        }
        return trip;
    }

    /** Moves a block later, keeping its planned times (what a delay does). */
    private static void moveLater(BlockEntity block, int minutes) {
        block.startTime = block.startTime.plusSeconds(minutes * 60L);
        block.endTime = block.endTime.plusSeconds(minutes * 60L);
    }

    @BeforeClass
    public static void allowLocalNetwork() {
        LocalNetworkAccess.allowForTests();
    }

    @After
    public void restoreRealRepository() {
        ServiceLocator.get().setTripRepositoryForTests(null);
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void savedTrip_onTrack_showsAditiAndTheRealFlight_offline() {
        useSavedTrip(seededTrip());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_trip_status);
            eventually(() -> onView(withId(R.id.trip_status_status_title))
                    .check(matches(allOf(isDisplayed(), withText(R.string.home_state_on_track)))));
            onView(withId(R.id.trip_status_greeting)).check(matches(withText(containsString("Aditi"))));
            onView(withId(R.id.trip_status_flight_title)).check(matches(withText(TestTrips.FLIGHT_TITLE)));
            onView(withId(R.id.trip_status_departs_time)).check(matches(withText("08:00")));
            onView(withId(R.id.trip_status_arrives_time)).check(matches(withText("09:45")));
            onView(withId(R.id.trip_status_flight_status)).check(matches(withText(R.string.status_ok)));
            onView(withId(R.id.trip_status_flight_planned)).check(matches(not(isDisplayed())));
            // No network: the saved plan is shown, and the screen says so.
            eventually(() -> onView(withId(R.id.trip_status_sync))
                    .check(matches(withText(startsWith("Offline · showing your saved plan from")))));
        }
    }

    @Test
    public void delayedFlight_showsDelayed_andTheMeetingNeedsADecision() {
        TripMapper.MappedTrip trip = seededTrip();
        trip.trip.version = 3;
        moveLater(trip.blocks.get(0), 120);                        // flight 10:00-11:45, still OK on the server
        moveLater(trip.blocks.get(1), 120);
        trip.blocks.get(1).status = BlockEntity.STATUS_AUTO_ADJUSTED; // bags
        moveLater(trip.blocks.get(2), 120);
        trip.blocks.get(2).status = BlockEntity.STATUS_AUTO_ADJUSTED; // cab
        trip.blocks.get(3).status = BlockEntity.STATUS_NEEDS_USER;    // client meeting
        useSavedTrip(trip);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_trip_status);
            eventually(() -> onView(withId(R.id.trip_status_status_title))
                    .check(matches(allOf(isDisplayed(), withText("1 thing needs your decision")))));
            onView(withId(R.id.trip_status_departs_time)).check(matches(withText("10:00")));
            onView(withId(R.id.trip_status_flight_status)).check(matches(withText(R.string.status_delayed)));
            onView(withId(R.id.trip_status_flight_planned))
                    .check(matches(allOf(isDisplayed(), withText("Planned for 08:00 · 2 hours later"))));
            onView(withId(R.id.trip_status_needs_list)).check(matches(hasDescendant(withText(TestTrips.MEETING_TITLE))));
            onView(withId(R.id.trip_status_changed_list)).check(matches(hasDescendant(withText(BAGS_TITLE))));

            // "See details" opens the meeting's details, the same sheet as on the Trip tab.
            onView(withId(R.id.trip_status_status_action)).perform(click());
            eventually(() -> onView(withId(R.id.sheet_title))
                    .check(matches(allOf(isDisplayed(), withText(TestTrips.MEETING_TITLE)))));
        }
    }

    @Test
    public void nothingSaved_andNoNetwork_explainsAndOffersRetry() {
        useSavedTrip(null);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_trip_status);
            eventually(() -> onView(withId(R.id.trip_status_state_title))
                    .check(matches(allOf(isDisplayed(), withText(R.string.trip_empty_error_title)))));
            onView(withId(R.id.trip_status_retry)).check(matches(isDisplayed()));
            onView(withId(R.id.trip_status_content)).check(matches(not(isDisplayed())));
        }
    }

    /**
     * Room loads data on a background thread that Espresso does not wait for, so a check may run a moment
     * too early. Retry it for up to 5 seconds before failing.
     */
    private static void eventually(Runnable check) {
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (true) {
            try {
                check.run();
                return;
            } catch (RuntimeException | AssertionError e) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw e;
                }
                SystemClock.sleep(100);
            }
        }
    }
}
