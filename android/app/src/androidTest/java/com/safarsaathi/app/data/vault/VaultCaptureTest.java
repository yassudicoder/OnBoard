package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.data.vault.capture.DocumentDraft;
import com.safarsaathi.app.data.vault.capture.DraftReader;
import com.safarsaathi.app.data.vault.capture.ReadNote;
import com.safarsaathi.app.data.vault.crypto.AndroidKeystoreMasterKey;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.VaultDao;
import com.safarsaathi.app.data.vault.db.VaultFileEntity;
import com.safarsaathi.app.ui.vault.PageAnalyzer;
import com.safarsaathi.app.ui.vault.SamplePages;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adding documents (V2) on a real phone or emulator: the phone's own text recognition reading a passport MRZ, and
 * saving / linking documents. Each test has its own vault folder and Keystore alias; background work runs on the
 * test thread.
 */
@RunWith(AndroidJUnit4.class)
public class VaultCaptureTest {

    private Context context;
    private File folder;
    private AndroidKeystoreMasterKey masterKey;
    private VaultSettings settings;
    private VaultRepository repository;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        String run = UUID.randomUUID().toString();
        folder = new File(context.getCacheDir(), "vault-capture-test-" + run);
        masterKey = new AndroidKeystoreMasterKey("onboard_vault_capture_test_" + run);
        settings = new VaultSettings(context, "onboard_vault_settings_capture_test");
        repository = new Vault(context, folder, masterKey, settings, Runnable::run, Runnable::run, Clock.systemUTC())
                .repository();
    }

    @After
    public void tearDown() throws Exception {
        masterKey.destroy();
        deleteRecursively(folder);
        settings.reset();
    }

    @Test
    public void thePhonesOwnTextRecognition_readsThePassportMrz_andNeverHidesAMisreadNumber() throws Exception {
        // The SAMPLE passport page, drawn exactly as the app saves it, then read back like a photo of it would be.
        SampleVault.SampleDocument passport = SampleVault.paramvir(Instant.now()).documents.get(0);
        byte[] jpeg = new SamplePages().render(passport);
        Bitmap page = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);

        PageAnalyzer.Result found = PageAnalyzer.analyse(Collections.singletonList(page));
        DocumentDraft draft = DraftReader.read(found.text, found.barcodes, DocSource.SCAN, LocalDate.of(2026, 10, 11));

        assertEquals(DocType.PASSPORT, draft.type);
        assertEquals("PARAMVIR SINGH", draft.holderName);
        assertEquals("IN", draft.issuingCountry);
        assertEquals(LocalDate.of(2029, 3, 14), draft.expiryDate);
        assertEquals(LocalDate.of(1990, 5, 14), draft.birthDate);
        // The number: read right, OR flagged. Never a wrong number without a warning. (On this emulator the text
        // recognition has read "SO000001", the letter O for a zero, which is exactly what check digits catch.)
        boolean flagged = false;
        for (ReadNote note : draft.notes) {
            flagged |= note.kind == ReadNote.Kind.MRZ_CHECK_FAILED && note.field == ReadNote.Field.NUMBER;
        }
        if (!"S0000001".equals(draft.number)) {
            assertTrue("A misread number must be flagged, it was read as " + draft.number, flagged);
        } else {
            assertFalse(flagged);
        }
        // Either way, the real number agrees with the check digit printed on the page.
        assertEquals(Boolean.TRUE, draft.numberMatchesCheckDigit("S0000001"));
    }

    @Test
    public void aConfirmedDocument_isSavedEncrypted_withItsTraveller() throws Exception {
        String tripId = newTrip("Business trip");
        DocumentDraft draft = new DocumentDraft();
        draft.type = DocType.PASSPORT;
        draft.title = "Passport (India)";
        draft.holderName = "PARAMVIR SINGH";
        draft.number = "s000 0001";
        draft.issuingCountry = "IN";
        draft.expiryDate = LocalDate.of(2029, 3, 14);
        byte[] page = new byte[] {1, 2, 3, 4, 5};

        String documentId = repository.addDocumentNow(tripId, null, draft,
                VaultRepository.TravellerChoice.newTraveller("PARAMVIR SINGH", "IN", LocalDate.of(1990, 5, 14)),
                Collections.singletonList(new VaultRepository.NewPage(page, VaultRepository.MIME_JPEG)));

        VaultDao dao = repository.daoForTests();
        TravelDocumentEntity saved = dao.document(documentId);
        assertTrue(saved.fieldsConfirmed);
        assertNotNull(saved.travellerId);
        assertFalse("The number is never stored readable", saved.numberEncrypted.contains("S0000001"));
        AtomicReference<String> number = new AtomicReference<>();
        repository.readNumber(saved, number::set);
        assertEquals("S0000001", number.get());

        List<VaultFileEntity> files = dao.filesOf(documentId);
        assertEquals(1, files.size());
        assertTrue(repository.checkAllFilesNow().allOk());
        assertEquals(1, dao.linkCount(tripId, documentId));
    }

    @Test
    public void aSavedDocument_canBeUsedByASecondTrip_withoutACopy() throws Exception {
        String first = newTrip("Trip A");
        String second = newTrip("Trip B");
        DocumentDraft draft = new DocumentDraft();
        draft.type = DocType.PASSPORT;
        draft.title = "Passport";
        String passport = repository.addDocumentNow(first, null, draft, VaultRepository.TravellerChoice.nobody(),
                Collections.singletonList(new VaultRepository.NewPage(new byte[] {9, 9}, VaultRepository.MIME_JPEG)));

        AtomicBoolean linked = new AtomicBoolean();
        repository.linkDocument(second, passport, null, nothing -> linked.set(true));
        repository.linkDocument(second, passport, null, nothing -> { }); // twice: still one link

        VaultDao dao = repository.daoForTests();
        assertTrue(linked.get());
        assertEquals(1, dao.linkCount(second, passport));
        assertEquals("Stored once", 1, repository.checkAllFilesNow().checked());

        // Deleting the first trip keeps the passport: the second trip still uses it.
        repository.deleteTrip(first, nothing -> { });
        assertNotNull(dao.document(passport));
        assertEquals(1, repository.checkAllFilesNow().checked());
    }

    private String newTrip(String title) {
        AtomicReference<String> id = new AtomicReference<>();
        repository.createTrip(title, null, null, id::set);
        return id.get();
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
