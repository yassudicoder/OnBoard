package com.safarsaathi.app.ui.home;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
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
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
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
import com.safarsaathi.app.testing.TestTrips;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UI test of the Home tab (the flight card). The app gets an in-memory database and an always-offline backend, so
 * everything shown must come from the saved trip, and the gate must honestly say "Not announced yet".
 */
@RunWith(AndroidJUnit4.class)
public class HomeFragmentTest {

    private AppDatabase database;
    private ExecutorService ioExecutor;

    private void useSavedTrip(@Nullable TripMapper.MappedTrip trip) {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        if (trip != null) {
            database.tripDao().replaceTrip(trip.trip, trip.blocks, trip.links);
        }
        ioExecutor = Executors.newSingleThreadExecutor();
        ServiceLocator.get().setTripRepositoryForTests(
                new TripRepository(database.tripDao(), new OfflineApi(), ioExecutor, Clock.systemUTC()));
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
    public void flightCard_showsTheSavedFlight_andNoInventedGate() {
        useSavedTrip(TestTrips.demoTrip1());
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            eventually(() -> onView(withId(R.id.home_flight_title))
                    .check(matches(allOf(isDisplayed(), withText(TestTrips.FLIGHT_TITLE)))));
            onView(withId(R.id.home_greeting)).check(matches(withText(containsString("Aditi"))));
            onView(withId(R.id.home_departs_time)).check(matches(withText("08:00")));
            onView(withId(R.id.home_arrives_time)).check(matches(withText("09:45")));
            onView(withId(R.id.home_gate)).check(matches(withText(R.string.home_not_announced)));
            onView(withId(R.id.home_boarding)).check(matches(withText(R.string.home_not_announced)));
            eventually(() -> onView(withId(R.id.home_sync))
                    .check(matches(withText(startsWith("Offline · showing your saved plan from")))));
        }
    }

    @Test
    public void navigateToGate_opensTheNavigateTab() {
        useSavedTrip(TestTrips.demoTrip1());
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            eventually(() -> onView(withId(R.id.home_navigate)).check(matches(isDisplayed())));
            onView(withId(R.id.home_navigate)).perform(click());
            eventually(() -> onView(withId(R.id.navigate_map)).check(matches(isDisplayed())));
        }
    }

    @Test
    public void rideButtons_toTheTerminalAndToTheHotelFromTheTrip() {
        useSavedTrip(TestTrips.demoTrip1());
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            eventually(() -> onView(allOf(withId(R.id.ride_button), isDescendantOfA(withId(R.id.home_ride_departure))))
                    .check(matches(allOf(withText("Get a ride to Mumbai T2"), isEnabled()))));
            // The T2 point is not checked by the team yet, so the screen says so.
            onView(allOf(withId(R.id.ride_note), isDescendantOfA(withId(R.id.home_ride_departure))))
                    .check(matches(withText(containsString("Approximate drop-off point"))));
            // ("Leave by" is not checked here: the test trip's date is fixed, so that time may have passed already.
            //  RidePointsTest checks the arithmetic.)
            // After you land: the hotel comes from the saved trip.
            onView(withId(R.id.home_arrival_place)).check(matches(withText("Place of block 5 · from your trip")));
            onView(allOf(withId(R.id.ride_button), isDescendantOfA(withId(R.id.home_ride_arrival))))
                    .check(matches(allOf(withText(R.string.ride_to_hotel), isEnabled())));
        }
    }

    @Test
    public void cancelledFlight_hidesTheRideToItsTerminal() {
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        trip.blocks.get(0).status = BlockEntity.STATUS_CANCELLED; // block 1 = the flight
        useSavedTrip(trip);
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            eventually(() -> onView(withId(R.id.home_arrival_place))
                    .check(matches(withText("Place of block 5 · from your trip"))));
            onView(withId(R.id.home_ride_departure)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test
    public void nothingSaved_andNoNetwork_explainsAndOffersRetry() {
        useSavedTrip(null);
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            eventually(() -> onView(withId(R.id.home_state_title))
                    .check(matches(allOf(isDisplayed(), withText(R.string.trip_empty_error_title)))));
            onView(withId(R.id.home_retry)).check(matches(isDisplayed()));
            onView(withId(R.id.home_content)).check(matches(not(isDisplayed())));
        }
    }

    /** Room loads on a background thread Espresso does not wait for: retry a check for up to 5 seconds. */
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
