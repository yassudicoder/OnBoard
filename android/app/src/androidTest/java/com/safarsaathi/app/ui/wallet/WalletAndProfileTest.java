package com.safarsaathi.app.ui.wallet;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import androidx.core.widget.NestedScrollView;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.TripMapper;
import com.safarsaathi.app.data.TripRepository;
import com.safarsaathi.app.data.WalletRepository;
import com.safarsaathi.app.data.local.AppDatabase;
import com.safarsaathi.app.testing.LocalNetworkAccess;
import com.safarsaathi.app.testing.OfflineApi;
import com.safarsaathi.app.testing.Screens;
import com.safarsaathi.app.testing.TestTrips;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UI tests of the Wallet and Profile tabs (Espresso). Like the other screen tests, the app gets an in-memory
 * database (demo trip 1, flight booked as K7Q2ZM) and a backend that is always offline, so everything shown must
 * come from what is saved on the phone.
 */
@RunWith(AndroidJUnit4.class)
public class WalletAndProfileTest {

    /** A real-format boarding pass code (IATA BCBP) for flight IJ 512, booking K7Q2ZM, seat 14A. */
    private static final String BOARDING_PASS = "M1SHARMA/ADITI        EK7Q2ZM BOMBLRIJ 0512 284Y014A0001 100";

    private AppDatabase database;
    private ExecutorService ioExecutor;

    @BeforeClass
    public static void allowLocalNetwork() {
        LocalNetworkAccess.allowForTests();
    }

    @Before
    public void useInMemoryData() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        trip.blocks.get(0).provider = "MOCK_AIR";
        trip.blocks.get(0).providerRef = "K7Q2ZM";
        database.tripDao().replaceTrip(trip.trip, trip.blocks, trip.links);
        ioExecutor = Executors.newSingleThreadExecutor();
        ServiceLocator.get().setTripRepositoryForTests(
                new TripRepository(database.tripDao(), new OfflineApi(), ioExecutor, Clock.systemUTC()));
        ServiceLocator.get().setWalletRepositoryForTests(
                new WalletRepository(database.walletDao(), ioExecutor, Clock.systemUTC()));
    }

    @After
    public void restoreRealRepositories() {
        ServiceLocator.get().setTripRepositoryForTests(null);
        ServiceLocator.get().setWalletRepositoryForTests(null);
        ioExecutor.shutdownNow();
        database.close();
    }

    @Test
    public void wallet_showsTheTripsBookingReference_andSavesAPastedBoardingPass() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_wallet); // opened from Profile, not a tab
            eventually(() -> onView(withId(R.id.wallet_bookings_list))
                    .check(matches(hasDescendant(withText("MockAir (demo airline) · booking ref K7Q2ZM")))));
            onView(withId(R.id.wallet_passes_empty)).check(matches(isDisplayed()));

            onView(withId(R.id.wallet_paste)).perform(click());
            onView(withId(R.id.paste_input)).perform(replaceText(BOARDING_PASS), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());

            eventually(() -> onView(withId(R.id.wallet_passes_list))
                    .check(matches(allOf(hasDescendant(withText("IJ 512")), hasDescendant(withText("14A")),
                            hasDescendant(withText("Matches your trip's booking K7Q2ZM"))))));
        }
    }

    @Test
    public void wallet_refusesTextThatIsNotABoardingPass() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_wallet); // opened from Profile, not a tab
            onView(withId(R.id.wallet_paste)).perform(click());
            onView(withId(R.id.paste_input)).perform(replaceText("HELLO"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());

            onView(withText("This is not a boarding pass code: it is too short.")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void wallet_savesAadhaarAndPan_andShowsThemOnlyMasked() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.navigation_wallet); // opened from Profile, not a tab

            // Aadhaar: a full 12-digit number cannot even be typed in (4 characters max) and is refused.
            onView(withId(R.id.wallet_add_id)).perform(scrollIntoView(), click());
            onView(withText(R.string.ids_kind_aadhaar)).perform(click());
            onView(withId(R.id.id_aadhaar_last4)).perform(replaceText("12"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withText(R.string.ids_error_aadhaar)).check(matches(isDisplayed()));
            onView(withId(R.id.id_aadhaar_last4)).perform(replaceText("1234"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());

            onView(withId(R.id.wallet_add_id)).perform(scrollIntoView(), click());
            onView(withText(R.string.ids_kind_pan)).perform(click());
            onView(withId(R.id.id_pan)).perform(replaceText("abcde1234f"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());

            eventually(() -> onView(withId(R.id.wallet_ids_list)).check(matches(allOf(
                    hasDescendant(withText("XXXX XXXX 1234")),
                    hasDescendant(withText("XXXXX1234F")),
                    not(hasDescendant(withText("ABCDE1234F")))))));
        }
    }

    @Test
    public void profile_showsTheTrip_andSavesOnlyTheLast4Digits() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.navigation_profile)).perform(click());
            onView(withId(R.id.profile_name)).check(matches(withText("Aditi")));
            eventually(() -> onView(withId(R.id.profile_purpose)).check(matches(withText(R.string.purpose_business))));
            onView(withId(R.id.profile_persona)).check(matches(withText(R.string.persona_business)));
            // The Wallet is linked from Profile > More.
            onView(withId(R.id.profile_open_wallet)).check(matches(withText(R.string.profile_open_wallet)));

            onView(withId(R.id.profile_add_card)).perform(scrollIntoView(), click());
            onView(withId(R.id.card_bank)).perform(replaceText("HDFC Bank"));
            onView(withId(R.id.card_last4)).perform(replaceText("12"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withText(R.string.profile_card_last4_error)).check(matches(isDisplayed()));

            onView(withId(R.id.card_last4)).perform(replaceText("1234"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());
            eventually(() -> onView(withId(R.id.profile_cards_list))
                    .check(matches(allOf(hasDescendant(withText("HDFC Bank · Credit card")),
                            hasDescendant(withText("•••• 1234"))))));
        }
    }

    /**
     * Scrolls a view of a NestedScrollView screen into sight (Espresso 3.7's scrollTo() only knows ScrollView).
     * The view asks its scrolling parents to show its whole area, like a screen reader does.
     */
    private static ViewAction scrollIntoView() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDescendantOfA(isAssignableFrom(NestedScrollView.class));
            }

            @Override
            public String getDescription() {
                return "scroll into view inside a NestedScrollView";
            }

            @Override
            public void perform(UiController uiController, View view) {
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    /** Room writes and reads on background threads Espresso does not wait for: retry a check for up to 5 s. */
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
