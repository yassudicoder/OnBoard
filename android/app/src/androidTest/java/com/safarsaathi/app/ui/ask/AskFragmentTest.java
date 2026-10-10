package com.safarsaathi.app.ui.ask;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;

import android.os.SystemClock;

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
import com.safarsaathi.app.data.ask.AskSettings;
import com.safarsaathi.app.data.local.AppDatabase;
import com.safarsaathi.app.testing.LocalNetworkAccess;
import com.safarsaathi.app.testing.OfflineApi;
import com.safarsaathi.app.testing.TestTrips;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UI test of Ask OnBoard (Layer 1, Quick answers). The app gets an in-memory database with the demo trip and an
 * always-offline backend, so every answer must come from the saved data on the phone, in the question's language,
 * with the "Answered on your phone" chip, and the gate must honestly be "not announced".
 */
@RunWith(AndroidJUnit4.class)
public class AskFragmentTest {

    private AppDatabase database;
    private ExecutorService ioExecutor;

    @BeforeClass
    public static void allowLocalNetwork() {
        LocalNetworkAccess.allowForTests();
    }

    @Before
    public void savedTripAndAutomaticLanguage() {
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        database.tripDao().replaceTrip(trip.trip, trip.blocks, trip.links);
        ioExecutor = Executors.newSingleThreadExecutor();
        ServiceLocator.get().setTripRepositoryForTests(
                new TripRepository(database.tripDao(), new OfflineApi(), ioExecutor, Clock.systemUTC()));
        AskSettings.setReplyLanguage(ApplicationProvider.getApplicationContext(), null);
    }

    @After
    public void restore() {
        ServiceLocator.get().setTripRepositoryForTests(null);
        AskSettings.setReplyLanguage(ApplicationProvider.getApplicationContext(), null);
        ioExecutor.shutdownNow();
        database.close();
    }

    private static void openAskFromHome() {
        eventually(() -> onView(withId(R.id.home_ask_bar)).check(matches(isDisplayed())));
        onView(withId(R.id.home_ask_bar)).perform(click());
        eventually(() -> onView(withId(R.id.ask_input)).check(matches(isDisplayed())));
    }

    private static void ask(String question) {
        // replaceText, not typeText: typeText can only press keys of the English keyboard (no Devanagari).
        onView(withId(R.id.ask_input)).perform(replaceText(question));
        onView(withId(R.id.ask_action)).perform(click());
    }

    @Test
    public void hinglishQuestion_getsAHinglishAnswer_fromThePhone_withNoInventedGate() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openAskFromHome();
            // The chat gets the whole screen: no bottom tabs here.
            onView(withId(R.id.bottom_nav)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            ask("Mera gate kahan hai?");
            eventually(() -> onView(withText(startsWith("Flight IJ 512 ka gate abhi announce nahi hua hai.")))
                    .check(matches(isDisplayed())));
            onView(allOf(withId(R.id.ask_answer_source), withText(startsWith("Aapke phone par jawab"))))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void hindiQuestion_inDevanagari_getsAHindiAnswer() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openAskFromHome();
            ask("बोर्डिंग कब बंद होगी?");
            eventually(() -> onView(withText(startsWith("फ्लाइट IJ 512 की बोर्डिंग बंद होने का समय अभी घोषित नहीं")))
                    .check(matches(isDisplayed())));
        }
    }

    @Test
    public void marathiChosenWithTheLanguageChip_answersInMarathi_evenToEnglish() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openAskFromHome();
            onView(withId(R.id.ask_language_chip)).perform(click());
            onView(withText(R.string.ask_language_mr)).perform(click());
            onView(withId(R.id.ask_language_chip)).check(matches(withText("Language: मरा")));
            ask("Where is my bag?");
            eventually(() -> onView(withText(startsWith("या फोनवर अजून कोणतीही बॅग नोंदवलेली नाही.")))
                    .check(matches(isDisplayed())));
        }
    }

    @Test
    public void washroomChip_answersFromTheDemoMap_andItsButtonOpensTheMap() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openAskFromHome();
            // The chips scroll sideways; this one may start off screen.
            onView(withText("Where are the washrooms?")).perform(scrollTo(), click());
            eventually(() -> onView(withText(containsString("From the demo map, not an official airport map.")))
                    .check(matches(isDisplayed())));
            onView(allOf(withId(R.id.ask_answer_action), withText("Open the map"))).perform(click());
            eventually(() -> onView(withId(R.id.navigate_map)).check(matches(isDisplayed())));
        }
    }

    @Test
    public void hotelSearch_saysItNeedsTheOnlineAssistant_neverInventsHotels() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openAskFromHome();
            ask("Find me a hotel near the airport tonight");
            eventually(() -> onView(withText(startsWith("I can't search flights or hotels on the phone.")))
                    .check(matches(isDisplayed())));
        }
    }

    /** Room and the answer thread run in the background, which Espresso does not wait for: retry for up to 5 s. */
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
