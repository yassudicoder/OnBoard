package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.data.vault.crypto.AndroidKeystoreMasterKey;
import com.safarsaathi.app.data.vault.db.VaultFileEntity;
import com.safarsaathi.app.ui.vault.SamplePages;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The read-only window for other features (VaultSummaryReader) against a real encrypted vault on the phone:
 * locked = hotel name and address only; unlocked = all documents, statuses from the real files.
 */
@RunWith(AndroidJUnit4.class)
public class VaultSummaryReaderTest {

    private File folder;
    private AndroidKeystoreMasterKey masterKey;
    private VaultSettings settings;
    private Vault vault;
    private String tripId;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String run = UUID.randomUUID().toString();
        folder = new File(context.getCacheDir(), "vault-reader-test-" + run);
        masterKey = new AndroidKeystoreMasterKey("onboard_vault_reader_test_" + run);
        settings = new VaultSettings(context, "onboard_vault_settings_reader_test");
        settings.reset(); // lock on (the default): the vault starts locked
        vault = new Vault(context, folder, masterKey, settings, Runnable::run, Runnable::run, Clock.systemUTC());
        tripId = vault.repository().loadSampleNow(new SamplePages());
    }

    @After
    public void tearDown() throws Exception {
        masterKey.destroy();
        deleteRecursively(folder);
        settings.reset();
    }

    @Test
    public void locked_givesOnlyTheHotelNameAndAddress() throws Exception {
        VaultSummaryReader reader = vault.summaryReader();
        assertTrue(!reader.isUnlocked());

        List<DocumentSummary> summaries = reader.getSummaries(tripId);

        assertEquals(1, summaries.size());
        assertEquals("SAMPLE Hotel Kinshasa", summaries.get(0).hotelName);
        assertEquals("1 Sample Avenue, Kinshasa (made-up address)", summaries.get(0).hotelAddress);
        assertNull(summaries.get(0).holderFirstName);
        assertNull(summaries.get(0).status);
        // The current trip is known, but not its title.
        TripRef trip = reader.currentTrip();
        assertEquals(tripId, trip.id);
        assertNull(trip.title);
    }

    @Test
    public void unlocked_givesEveryDocument_andNoticesAMissingPage() throws Exception {
        vault.session().unlock();
        VaultSummaryReader reader = vault.summaryReader();

        List<DocumentSummary> summaries = reader.getSummaries(tripId);
        assertEquals(8, summaries.size());
        for (DocumentSummary summary : summaries) {
            assertEquals("Paramvir", summary.holderFirstName);
            assertEquals("Not offline-ready yet (that check comes in V6)", SummaryStatus.NOT_OFFLINE_YET,
                    summary.status);
        }
        assertEquals("Delhi → Kigali → Kinshasa", reader.currentTrip().title);

        // Delete the passport's encrypted page from the phone: the passport becomes MISSING.
        DocumentSummary passport = null;
        for (DocumentSummary summary : summaries) {
            if (summary.type == DocType.PASSPORT) {
                passport = summary;
            }
        }
        List<VaultFileEntity> pages = vault.repository().daoForTests().filesOf(passport.documentId);
        assertTrue(new File(new File(folder, "files"), pages.get(0).id + ".bin").delete());
        for (DocumentSummary summary : reader.getSummaries(tripId)) {
            if (summary.type == DocType.PASSPORT) {
                assertEquals(SummaryStatus.MISSING, summary.status);
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
