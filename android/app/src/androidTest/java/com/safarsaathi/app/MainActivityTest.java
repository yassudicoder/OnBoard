package com.safarsaathi.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

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
        assertHomeIsShown();
    }

    @Test
    public void eachBottomNavItem_showsItsOwnTab() {
        clickTab(R.id.navigation_navigate);
        // A full-screen map like Google Maps: its own search bar instead of the top bar.
        onView(withId(R.id.navigate_search_bar)).check(matches(isDisplayed()));
        onView(withId(R.id.app_bar)).check(matches(not(isDisplayed())));

        clickTab(R.id.navigation_bags);
        assertTabIsShown(R.string.title_bags, R.string.bags_headline);

        clickTab(R.id.navigation_help);
        assertTabIsShown(R.string.title_help, R.string.help_headline);

        clickTab(R.id.navigation_profile);
        assertToolbarTitle(R.string.title_profile);
        onView(withId(R.id.profile_name)).check(matches(allOf(isDisplayed(), withText("Aditi"))));

        clickTab(R.id.navigation_home);
        assertHomeIsShown();
    }

    /** Taps a bottom-bar item. Each item view carries its menu item id (R.id.navigation_...). */
    private static void clickTab(int tabId) {
        onView(withId(tabId)).perform(click());
    }

    /**
     * Checks the Home tab: its title in the top bar and the greeting for the sample traveller. What else it shows
     * depends on whether a backend is running (HomeFragmentTest checks the flight card).
     */
    private static void assertHomeIsShown() {
        assertToolbarTitle(R.string.title_home);
        onView(withId(R.id.home_greeting)).check(matches(allOf(isDisplayed(), withText(containsString("Aditi")))));
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
