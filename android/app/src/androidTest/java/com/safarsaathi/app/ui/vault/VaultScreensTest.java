package com.safarsaathi.app.ui.vault;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.vault.Vault;
import com.safarsaathi.app.data.vault.VaultSettings;
import com.safarsaathi.app.data.vault.crypto.AndroidKeystoreMasterKey;
import com.safarsaathi.app.testing.LocalNetworkAccess;
import com.safarsaathi.app.testing.Screens;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UI tests of the Document Vault screens (Espresso). Each test gets its own vault (temporary folder, its own
 * Keystore alias and settings file), so the app's real vault is never touched. The lock is switched off except in
 * the lock test, because an emulator cannot put a finger on the sensor.
 */
@RunWith(AndroidJUnit4.class)
public class VaultScreensTest {

    private static final String SAMPLE_TRIP = "Delhi → Kigali → Kinshasa";

    private Context context;
    private File folder;
    private AndroidKeystoreMasterKey masterKey;
    private VaultSettings settings;
    private ExecutorService vaultExecutor;
    private Vault vault;

    @BeforeClass
    public static void allowLocalNetwork() {
        LocalNetworkAccess.allowForTests();
    }

    @Before
    public void useATestVault() {
        context = ApplicationProvider.getApplicationContext();
        String run = UUID.randomUUID().toString();
        folder = new File(context.getCacheDir(), "vault-ui-test-" + run);
        masterKey = new AndroidKeystoreMasterKey("onboard_vault_ui_test_" + run);
        settings = new VaultSettings(context, "onboard_vault_settings_ui_test");
        settings.reset();
        settings.setLockEnabled(false);
        vaultExecutor = Executors.newSingleThreadExecutor();
        vault = new Vault(context, folder, masterKey, settings, vaultExecutor, ContextCompat.getMainExecutor(context),
                Clock.systemUTC());
        ServiceLocator.get().setVaultForTests(vault);
    }

    @After
    public void restoreTheRealVault() throws Exception {
        ServiceLocator.get().setVaultForTests(null);
        vaultExecutor.shutdownNow();
        masterKey.destroy();
        deleteRecursively(folder);
        settings.reset();
    }

    @Test
    public void home_saysCopiesDoNotReplaceOriginals_andLoadsTheSampleTrip() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.vault_graph); // opened from Profile > More
            onView(withId(R.id.vault_originals_note)).check(matches(withText(R.string.vault_originals_note)));
            eventually(() -> onView(withId(R.id.vault_trips_empty)).check(matches(isDisplayed())));

            onView(withId(R.id.vault_load_sample)).perform(scrollIntoView(), click());

            eventually(() -> onView(withId(R.id.vault_trips_list)).check(matches(allOf(
                    hasDescendant(withText(SAMPLE_TRIP)),
                    hasDescendant(withText("8 documents")),
                    hasDescendant(withText(R.string.vault_sample_chip))))));
            onView(withId(R.id.vault_load_sample)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void tripFolder_groupsByLegAndType_andTheDocumentShowsOnlyAMaskedNumber() throws Exception {
        vault.repository().loadSampleNow(new SamplePages());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.vault_graph);
            eventually(() -> onView(withText(SAMPLE_TRIP)).perform(click()));

            // By leg: whole trip, then the three legs; the onward flight sits under the Kigali transit.
            eventually(() -> onView(withId(R.id.folder_sections)).check(matches(allOf(
                    hasDescendant(withText(R.string.vault_whole_trip)),
                    hasDescendant(withText("Delhi → Kigali")),
                    hasDescendant(withText("Kigali transit")),
                    hasDescendant(withText("Kinshasa")),
                    hasDescendant(withText("E-ticket Kigali → Kinshasa"))))));

            // By type: one heading per kind of document.
            onView(withId(R.id.folder_by_type)).perform(click());
            onView(withId(R.id.folder_sections)).check(matches(allOf(
                    hasDescendant(withText(R.string.vault_type_passport)),
                    hasDescendant(withText(R.string.vault_type_ticket)),
                    hasDescendant(withText(R.string.vault_type_hotel)),
                    not(hasDescendant(withText(R.string.vault_whole_trip))))));

            onView(withText("Passport (India)")).perform(scrollIntoView(), click());
            eventually(() -> onView(withId(R.id.doc_fields)).check(matches(hasDescendant(withText("••••••01")))));
            onView(withId(R.id.doc_fields)).check(matches(not(hasDescendant(withText("S0000001")))));
            onView(withId(R.id.doc_sample_note)).check(matches(isDisplayed()));
            // The page was decrypted and its SHA-256 fingerprint matched.
            eventually(() -> onView(withId(R.id.doc_pages))
                    .check(matches(hasDescendant(withText(R.string.vault_doc_file_ok)))));
        }
    }

    @Test
    public void vaultScreens_blockScreenshots_otherScreensDoNot() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertEquals(0, secureFlag(scenario));
            Screens.open(scenario, R.id.vault_graph);
            eventually(() -> assertEquals(WindowManager.LayoutParams.FLAG_SECURE, secureFlag(scenario)));

            pressBack(); // back to Home
            eventually(() -> assertEquals(0, secureFlag(scenario)));

            // The traveller can switch it off in the vault settings.
            settings.setBlockScreenshots(false);
            Screens.open(scenario, R.id.vault_graph);
            onView(withId(R.id.vault_headline)).check(matches(isDisplayed()));
            assertEquals(0, secureFlag(scenario));
        }
    }

    @Test
    public void lockedVault_hidesTheDocuments_untilUnlocked() {
        // On an emulator without a screen lock the cover offers "open without a lock". (With a PIN set, Android's own
        // prompt would appear, which a test cannot answer, so the test is skipped there.)
        assumeFalse(VaultScreen.phoneHasLock(context));
        settings.setLockEnabled(true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.vault_graph);
            onView(withId(R.id.vault_cover)).check(matches(isDisplayed()));
            onView(withText(R.string.vault_no_lock_title)).check(matches(isDisplayed()));
            onView(withId(R.id.vault_headline)).check(matches(not(isDisplayed())));

            onView(withId(R.id.vault_cover_skip)).perform(click());
            onView(withId(R.id.vault_headline)).check(matches(isDisplayed()));
            onView(withId(R.id.vault_cover)).check(matches(not(isDisplayed())));

            // "Lock vault" in the toolbar covers everything again.
            onView(withId(R.id.vault_menu_lock)).perform(click());
            onView(withId(R.id.vault_cover)).check(matches(isDisplayed()));
            onView(withId(R.id.vault_headline)).check(matches(not(isDisplayed())));
        }
    }

    /** FLAG_SECURE on the activity's window, or 0. */
    private static int secureFlag(ActivityScenario<MainActivity> scenario) {
        AtomicInteger flags = new AtomicInteger();
        scenario.onActivity(activity -> flags.set(activity.getWindow().getAttributes().flags));
        return flags.get() & WindowManager.LayoutParams.FLAG_SECURE;
    }

    /** Scrolls a view of a NestedScrollView screen into sight (Espresso's scrollTo() only knows ScrollView). */
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

    /**
     * The vault decrypts and draws on its own background thread, which Espresso does not wait for: retry a check
     * for up to 10 s (drawing and encrypting the 8 sample pages takes a moment on an emulator).
     */
    private static void eventually(Runnable check) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
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

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
