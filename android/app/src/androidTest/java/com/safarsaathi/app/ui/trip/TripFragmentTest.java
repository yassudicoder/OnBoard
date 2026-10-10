package com.safarsaathi.app.ui.trip;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.startsWith;

import android.os.SystemClock;

import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
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
 * UI test of the PARKED trip timeline screen (Espresso, on an emulator or phone; opened directly, whatever
 * feature.tripTimeline says): a trip saved in Room is shown, with no
 * network. Through the ServiceLocator test hook the screen gets an in-memory database holding demo trip 1
 * and a backend that is always offline, so the result is the same with or without a real backend running.
 */
@RunWith(AndroidJUnit4.class)
public class TripFragmentTest {

    private AppDatabase database;
    private ExecutorService ioExecutor;

    /** Saves the trip in a fresh in-memory database and makes the app use it (call before opening the app). */
    private void useSavedTrip(TripMapper.MappedTrip trip) {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        database.tripDao().replaceTrip(trip.trip, trip.blocks, trip.links);
        ioExecutor = Executors.newSingleThreadExecutor();
        TripRepository repository = new TripRepository(database.tripDao(), new OfflineApi(), ioExecutor,
                Clock.systemUTC());
        ServiceLocator.get().setTripRepositoryForTests(repository);
    }

    /** Before MainActivity opens: answer its local-network question in advance, so it never covers the screen. */
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
    public void savedTrip_isShownOffline_withTitlesAndTheAnchorStar() {
        useSavedTrip(TestTrips.demoTrip1());

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_trip); // parked screen, not a tab

            eventually(() -> onView(withText("Client visit: Mumbai to Bengaluru")).check(matches(isDisplayed())));
            eventually(() -> onView(withText(startsWith("Offline · showing your saved plan from")))
                    .check(matches(isDisplayed())));
            showRow(TestTrips.FLIGHT_TITLE);
            showRow(TestTrips.MEETING_TITLE);
            // Only the anchor (the meeting) shows the star.
            onView(allOf(withContentDescription(R.string.trip_anchor_description), isDisplayed()))
                    .check(matches(isDisplayed()));
            showRow(TestTrips.DINNER_TITLE);
        }
    }

    @Test
    public void blockNeedingDecision_showsTheRedBanner() {
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        trip.blocks.get(3).status = BlockEntity.STATUS_NEEDS_USER; // the client meeting
        useSavedTrip(trip);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_trip); // parked screen, not a tab

            eventually(() -> onView(withId(R.id.trip_attention_title))
                    .check(matches(allOf(isDisplayed(),
                            withText("1 thing needs your decision: " + TestTrips.MEETING_TITLE)))));
        }
    }

    /** Scrolls the timeline to the row with this text and checks it is on screen. */
    private static void showRow(String text) {
        eventually(() -> onView(withId(R.id.trip_list))
                .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(text)))));
        onView(withText(text)).check(matches(isDisplayed()));
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
