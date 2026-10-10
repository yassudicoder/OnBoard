package com.safarsaathi.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.testing.LocalNetworkAccess;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI test (Espresso). It runs on an emulator or a phone, not on the laptop:
 * {@code ./gradlew connectedDebugAndroidTest} with a device connected.
 * Tip: turn off animations in the device's Developer options for steadier UI tests.
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    /** Runs once, BEFORE the rule below opens MainActivity, so its local-network question never appears. */
    @BeforeClass
    public static void allowLocalNetwork() {
        LocalNetworkAccess.allowForTests();
    }

    /** Opens MainActivity before each test and closes it afterwards. */
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void appOpensOnHomeTab() {
        assertTabIsShown(R.string.title_home, R.string.home_headline);
    }

    @Test
    public void eachBottomNavItem_showsItsOwnTab() {
        // The Trip tab is a real screen since Phase 1. What it lists depends on whether a backend is
        // running (TripFragmentTest checks the content), so here we only check that the screen opened.
        clickTab(R.id.navigation_trip);
        assertToolbarTitle(R.string.title_trip);
        onView(withId(R.id.trip_root)).check(matches(isDisplayed()));

        clickTab(R.id.navigation_assistant);
        assertTabIsShown(R.string.title_assistant, R.string.assistant_headline);

        clickTab(R.id.navigation_wallet);
        assertTabIsShown(R.string.title_wallet, R.string.wallet_headline);

        clickTab(R.id.navigation_profile);
        assertTabIsShown(R.string.title_profile, R.string.profile_headline);

        clickTab(R.id.navigation_home);
        assertTabIsShown(R.string.title_home, R.string.home_headline);
    }

    /** Taps a bottom-bar item. Each item view carries its menu item id (R.id.navigation_...). */
    private static void clickTab(int tabId) {
        onView(withId(tabId)).perform(click());
    }

    /** Checks a placeholder tab's headline on screen and its title in the top bar. */
    private static void assertTabIsShown(int titleRes, int headlineRes) {
        // Matching id AND text means only the new tab's headline counts, even while the old tab
        // is still fading out.
        onView(allOf(withId(R.id.placeholder_headline), withText(headlineRes)))
                .check(matches(isDisplayed()));
        assertToolbarTitle(titleRes);
    }

    /** Checks the title in the top bar. */
    private static void assertToolbarTitle(int titleRes) {
        onView(allOf(isDescendantOfA(withId(R.id.toolbar)), withText(titleRes)))
                .check(matches(isDisplayed()));
    }
}
