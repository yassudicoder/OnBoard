package com.safarsaathi.app.ui.navigate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.safarsaathi.app.AppSettings;
import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.airport.AirportMapRepository;
import com.safarsaathi.app.data.local.AppDatabase;
import com.safarsaathi.app.testing.LocalNetworkAccess;
import com.safarsaathi.app.testing.OfflineApi;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UI test of the Navigate tab (G1-G3), with an empty database and NO network: the map inside the app is shown and
 * labelled as a demo schematic; search finds places; Directions asks where you are, previews the route with its time,
 * and Start shows Google-Maps-style turn-by-turn with an ongoing notification; steps past a turn ask "Missed a turn?";
 * the demo walk arrives at the gate.
 */
@RunWith(AndroidJUnit4.class)
public class NavigateFragmentTest {

    private AppDatabase database;
    private ExecutorService ioExecutor;

    @BeforeClass
    public static void allowPermissions() {
        LocalNetworkAccess.allowForTests();
        // Step counting: answered in advance, so the question never covers the screen (on phones that count steps).
        String app = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(app, Manifest.permission.ACTIVITY_RECOGNITION);
        }
        // The navigation notification (Android 13+), same reason.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(app, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Before
    public void emptyDatabaseAndNoNetwork() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        ioExecutor = Executors.newSingleThreadExecutor();
        ServiceLocator.get().setAirportMapRepositoryForTests(new AirportMapRepository(database.airportMapDao(),
                new OfflineApi(), NavigateFragmentTest::appAsset, ioExecutor, Clock.systemUTC()));
        // Same route settings every run, and no talking during tests.
        AppSettings.setStepFree(context, false);
        AppSettings.setSlowPace(context, false);
        AppSettings.setVoiceGuidance(context, false);
    }

    @After
    public void restoreRealRepository() {
        ServiceLocator.get().setAirportMapRepositoryForTests(null);
        ioExecutor.shutdownNow();
        database.close();
        AppSettings.setVoiceGuidance(ApplicationProvider.getApplicationContext(), true);
    }

    @Test
    public void bundledMap_isShownOffline_andSaysItIsADemoSchematic() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();

            onView(withId(R.id.navigate_demo_label)).check(matches(allOf(isDisplayed(),
                    withText("BOM T2 · Demo schematic, not an official airport map"))));
            onView(withText("Departures")).check(matches(isDisplayed()));
            onView(withText("Arrivals")).check(matches(isDisplayed()));
            eventually(() -> onView(withId(R.id.navigate_status))
                    .check(matches(withText("Offline · map v2 saved on this phone"))));
        }
    }

    @Test
    public void search_findsAGate_andShowsItsCard() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();
            searchAndPick("32", "Gate 32");

            onView(withId(R.id.navigate_place_name)).check(matches(allOf(isDisplayed(), withText("Gate 32"))));
            onView(withId(R.id.navigate_place_detail)).check(matches(withText("Gate · Departures · after security")));
        }
    }

    @Test
    public void search_findsLostAndFound_onArrivals_withItsHint() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();
            searchAndPick("Lost", "Lost & Found");

            onView(withId(R.id.navigate_place_detail))
                    .check(matches(withText("Lost & Found · Arrivals · before security")));
            onView(withId(R.id.navigate_place_hint)).check(matches(withText(containsString("Ground Transportation Lobby"))));
        }
    }

    @Test
    public void directions_askWhereYouAre_thenPreviewTheRoute() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();
            searchAndPick("32", "Gate 32");
            onView(withId(R.id.navigate_place_directions)).perform(click());

            // The app doesn't know where the traveller is: it asks instead of guessing.
            onView(withId(R.id.navigate_need_start)).check(matches(isDisplayed()));
            onView(withId(R.id.navigate_need_choose)).perform(click());
            onView(withText("Entry Gate 4")).perform(click());

            onView(withId(R.id.navigate_from)).check(matches(withText("Your place: Entry Gate 4")));
            onView(withId(R.id.navigate_to)).check(matches(withText("To: Gate 32")));
            onView(withId(R.id.navigate_preview_time)).check(matches(withText("4 min")));
            onView(withId(R.id.navigate_preview_distance)).check(matches(withText("(280 m)")));
            onView(withId(R.id.navigate_preview_detail)).check(matches(withText("Quickest route · via Security check")));
            onView(withId(R.id.navigate_preview_note))
                    .check(matches(withText("Walking time only, an estimate · security queues not included")));
        }
    }

    @Test
    public void start_showsTheTurnBanner_withoutTabs_andEndingBringsThemBack() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();
            planFrom("Entry Gate 4", "32", "Gate 32");
            onView(withId(R.id.navigate_start)).perform(click());

            onView(withId(R.id.navigate_banner_text)).check(matches(allOf(isDisplayed(),
                    withText("Head towards Check-in row D"))));
            onView(withId(R.id.navigate_banner_distance)).check(matches(withText("30 m")));
            onView(withId(R.id.navigate_eta_detail)).check(matches(withText(containsString("280 m · arrive"))));
            onView(withId(R.id.bottom_nav)).check(matches(not(isDisplayed())));
            onView(withId(R.id.navigate_demo_label)).check(matches(isDisplayed())); // still labelled

            onView(withId(R.id.navigate_exit)).perform(click());
            onView(withId(R.id.navigate_search_bar)).check(matches(isDisplayed()));
            onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void demoWalk_arrivesAtTheGate() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();
            planFrom("Pier C sign", "31", "Gate 31"); // 20 m
            onView(withId(R.id.navigate_start)).perform(click());
            onView(withId(R.id.navigate_demo_speed)).perform(click()); // x4 -> x8
            onView(withId(R.id.navigate_demo_walk)).perform(click());

            eventually(10_000, () -> onView(withId(R.id.navigate_arrived_title))
                    .check(matches(allOf(isDisplayed(), withText("You have arrived at Gate 31")))));
            onView(withId(R.id.navigate_arrived_done)).perform(click());
            onView(withId(R.id.navigate_search_bar)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void navigating_showsTheOngoingNotification_andEndingRemovesIt() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();
            planFrom("Entry Gate 4", "32", "Gate 32");
            onView(withId(R.id.navigate_start)).perform(click());

            eventually(() -> {
                Notification shown = navNotification();
                assertNotNull("no navigation notification", shown);
                assertEquals("Gate 32 · 4 min", String.valueOf(shown.extras.getCharSequence(Notification.EXTRA_TITLE)));
                assertEquals("30 m · Head towards Check-in row D",
                        String.valueOf(shown.extras.getCharSequence(Notification.EXTRA_TEXT)));
                assertTrue(String.valueOf(shown.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
                        .matches("Arrive \\d\\d:\\d\\d \\(estimate\\)"));
                assertTrue((shown.flags & Notification.FLAG_ONGOING_EVENT) != 0);
            });

            onView(withId(R.id.navigate_exit)).perform(click());
            eventually(() -> assertNull(navNotification()));
        }
    }

    @Test
    public void stepsPastATurn_askMissedATurn_andImHereClearsIt() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openNavigate();
            planFrom("Entry Gate 4", "32", "Gate 32");
            onView(withId(R.id.navigate_start)).perform(click());
            onView(withId(R.id.navigate_missed_turn)).check(matches(not(isDisplayed())));

            // The phone reports a direction, then counts 80 steps (56 m) straight on: 26 m past the left turn at
            // Check-in row D, and the phone never turned. (All on the main thread at once, so the emulator's own
            // sensors can't come in between.)
            scenario.onActivity(activity -> {
                MapViewModel model = navigateViewModel(activity);
                model.onHeading(100);
                for (int i = 0; i < 80; i++) {
                    model.onStep();
                }
            });

            onView(withId(R.id.navigate_missed_turn)).check(matches(isDisplayed()));
            onView(withId(R.id.navigate_missed_turn_title))
                    .check(matches(withText("Missed a turn? Scan a QR code or tap \"I'm here\".")));
            onView(withId(R.id.navigate_missed_turn_detail))
                    .check(matches(withText("Expected: Turn left at Check-in row D")));

            onView(withId(R.id.navigate_missed_turn_here)).perform(click());
            onView(withText("Pier C sign")).perform(click()); // off the route: new route from there
            onView(withId(R.id.navigate_missed_turn)).check(matches(not(isDisplayed())));
        }
    }

    // ---- Helpers ----

    /** The Navigate tab's own ViewModel (the one the screen uses). */
    private static MapViewModel navigateViewModel(MainActivity activity) {
        NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        Fragment screen = host.getChildFragmentManager().getPrimaryNavigationFragment();
        return new ViewModelProvider(screen).get(MapViewModel.class);
    }

    /** The navigation notification on show, or null. */
    private static Notification navNotification() {
        NotificationManager manager = ApplicationProvider.getApplicationContext()
                .getSystemService(NotificationManager.class);
        for (StatusBarNotification shown : manager.getActiveNotifications()) {
            if (shown.getId() == NavNotification.ID) {
                return shown.getNotification();
            }
        }
        return null;
    }

    private static void openNavigate() {
        onView(withId(R.id.navigation_navigate)).perform(click());
        eventually(() -> onView(withText("Departures")).check(matches(isDisplayed())));
    }

    private static void searchAndPick(String query, String place) {
        onView(withId(R.id.navigate_search)).perform(click());
        onView(withId(R.id.place_search_input)).perform(typeText(query), closeSoftKeyboard());
        onView(allOf(withId(R.id.place_name), withText(place))).perform(click());
    }

    /** Destination by search, then "Choose place" for where the traveller is. */
    private static void planFrom(String start, String query, String destination) {
        searchAndPick(query, destination);
        onView(withId(R.id.navigate_place_directions)).perform(click());
        onView(withId(R.id.navigate_need_choose)).perform(click());
        onView(withText(start)).perform(click());
        onView(withId(R.id.navigate_start)).check(matches(isDisplayed()));
    }

    /** The app's own assets (the bundled maps). */
    private static String appAsset(String path) throws IOException {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets().open(path)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void eventually(Runnable check) {
        eventually(5_000, check);
    }

    /** Room, the network and the demo walk run on their own time, which Espresso does not wait for: retry a check. */
    private static void eventually(long timeoutMs, Runnable check) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
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
