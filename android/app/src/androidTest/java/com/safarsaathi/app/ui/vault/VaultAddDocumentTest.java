package com.safarsaathi.app.ui.vault;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.DocType;
import com.safarsaathi.app.data.vault.SampleVault;
import com.safarsaathi.app.data.vault.Vault;
import com.safarsaathi.app.data.vault.VaultRepository;
import com.safarsaathi.app.data.vault.VaultSettings;
import com.safarsaathi.app.data.vault.capture.DocumentDraft;
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
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI tests of adding documents (V2): typing one in, an imported photo of the SAMPLE passport going through the crop
 * screen and the phone's own MRZ reading to the confirm screen, and reusing a saved document in a second trip.
 * Google's scanner screen belongs to Play services and cannot be driven from a test; it hands its pages to the same
 * code as the import does.
 */
@RunWith(AndroidJUnit4.class)
public class VaultAddDocumentTest {

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
        folder = new File(context.getCacheDir(), "vault-add-test-" + run);
        masterKey = new AndroidKeystoreMasterKey("onboard_vault_add_test_" + run);
        settings = new VaultSettings(context, "onboard_vault_settings_add_test");
        settings.reset();
        settings.setLockEnabled(false); // an emulator cannot put a finger on the sensor
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
    public void typeItIn_savesAHotelBookingIntoTheFolder() throws Exception {
        newTrip("Kinshasa extension");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.vault_graph);
            eventually(() -> onView(withText("Kinshasa extension")).perform(click()));
            onView(withId(R.id.folder_add)).perform(click());
            onView(withText(R.string.vault_add_manual)).inRoot(isDialog()).perform(click());

            onView(withId(R.id.edit_type)).perform(click());
            onView(withText(R.string.vault_type_hotel)).inRoot(isPlatformPopup()).perform(click());
            onView(withId(R.id.edit_holder)).perform(scrollIntoView(), replaceText("Paramvir Singh"));
            onView(withId(R.id.edit_from)).perform(scrollIntoView(), replaceText("14/11/2026"));
            onView(withId(R.id.edit_to)).perform(scrollIntoView(), replaceText("18/11/2026"));
            onView(withId(R.id.edit_booking)).perform(scrollIntoView(), replaceText("htl-0002"));
            closeSoftKeyboard();
            onView(withId(R.id.edit_save)).perform(scrollIntoView(), click());

            eventually(() -> onView(withId(R.id.folder_sections)).check(matches(allOf(
                    hasDescendant(withText("Hotel booking")),
                    hasDescendant(withText("Hotel booking · 14 Nov 2026 to 18 Nov 2026"))))));
        }
    }

    @Test
    public void importedPassportPhoto_isCroppedAndRead_andMustBeConfirmed() throws Exception {
        String tripId = newTrip("Delhi to Kinshasa");
        SampleVault.SampleDocument passport = SampleVault.paramvir(Instant.now()).documents.get(0);
        byte[] jpeg = new SamplePages().render(passport);
        Bitmap photo = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.vault_graph);
            eventually(() -> onView(withText("Delhi to Kinshasa")).perform(click()));
            // As if "Import a PDF or photo" had picked this photo: it goes to the crop screen.
            scenario.onActivity(activity -> {
                NavController navController = Navigation.findNavController(activity, R.id.nav_host_fragment);
                CaptureViewModel capture = new ViewModelProvider(navController.getBackStackEntry(R.id.vault_graph))
                        .get(CaptureViewModel.class);
                capture.begin(tripId, DocSource.IMPORT);
                capture.setPendingCrop(photo);
                navController.navigate(R.id.vault_crop);
            });
            onView(withId(R.id.crop_use)).perform(click());

            // Read on the phone: the MRZ fills in the passport. Up to 2 minutes: the FIRST text recognition after a fresh
            // install loads its models cold, which a busy emulator can stretch to ~40 s (a phone takes a second or two).
            eventually(() -> onView(withId(R.id.edit_holder)).check(matches(withText("PARAMVIR SINGH"))), 120_000);
            onView(withId(R.id.edit_notes)).check(matches(hasDescendant(withText(R.string.vault_note_mrz_read))));
            onView(withId(R.id.edit_title)).check(matches(withText("Passport (India)")));
            // If text recognition misread the number (e.g. O for 0), the field says so; the traveller types it from
            // the passport and the printed check digit confirms the correction.
            onView(withId(R.id.edit_number)).perform(scrollIntoView(), replaceText("S0000001"));
            closeSoftKeyboard();
            onView(withText(R.string.vault_edit_number_check_ok)).check(matches(isDisplayed()));

            // Not saved until the traveller confirms having compared the details.
            onView(withId(R.id.edit_save)).perform(scrollIntoView(), click());
            onView(withId(R.id.edit_confirm_error)).check(matches(isDisplayed()));
            onView(withId(R.id.edit_confirm)).perform(scrollIntoView(), click());
            onView(withId(R.id.edit_save)).perform(scrollIntoView(), click());

            eventually(() -> onView(withText("Passport (India)")).perform(click()));
            eventually(() -> onView(withId(R.id.doc_fields)).check(matches(hasDescendant(withText("••••••01")))));
            eventually(() -> onView(withId(R.id.doc_pages))
                    .check(matches(hasDescendant(withText(R.string.vault_doc_file_ok)))));
        }
    }

    @Test
    public void fromYourOtherTrips_usesTheSamePassportInASecondTrip() throws Exception {
        String first = newTrip("Trip A");
        newTrip("Trip B");
        DocumentDraft draft = new DocumentDraft();
        draft.type = DocType.PASSPORT;
        draft.title = "Passport (India)";
        CountDownLatch saved = new CountDownLatch(1);
        vault.repository().addDocument(first, null, draft, VaultRepository.TravellerChoice.nobody(),
                Collections.emptyList(), id -> saved.countDown());
        saved.await(10, TimeUnit.SECONDS);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Screens.open(scenario, R.id.vault_graph);
            eventually(() -> onView(withText("Trip B")).perform(click()));
            onView(withId(R.id.folder_add)).perform(click());
            onView(withText(R.string.vault_add_existing)).inRoot(isDialog()).perform(click());
            eventually(() -> onView(withText("Passport (India) · Passport")).inRoot(isDialog()).perform(click()));

            eventually(() -> onView(withId(R.id.folder_sections))
                    .check(matches(hasDescendant(withText("Passport (India)")))));
        }
    }

    /** Creates a trip folder and waits for it. */
    private String newTrip(String title) throws Exception {
        AtomicReference<String> id = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        vault.repository().createTrip(title, null, null, newId -> {
            id.set(newId);
            done.countDown();
        });
        done.await(10, TimeUnit.SECONDS);
        return id.get();
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

    private static void eventually(Runnable check) {
        eventually(check, 10_000);
    }

    /** Work on background threads (encryption, text recognition) is not something Espresso waits for: retry. */
    private static void eventually(Runnable check, long timeoutMillis) {
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
        while (true) {
            try {
                check.run();
                return;
            } catch (RuntimeException | AssertionError e) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw e;
                }
                SystemClock.sleep(200);
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
