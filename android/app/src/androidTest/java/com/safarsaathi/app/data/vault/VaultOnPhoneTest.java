package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.data.vault.crypto.AesGcm;
import com.safarsaathi.app.data.vault.crypto.AndroidKeystoreMasterKey;
import com.safarsaathi.app.data.vault.crypto.EncryptedFileStore;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.TripDocumentLinkEntity;
import com.safarsaathi.app.data.vault.db.TripVaultEntity;
import com.safarsaathi.app.data.vault.db.VaultDatabase;
import com.safarsaathi.app.data.vault.db.VaultFileEntity;
import com.safarsaathi.app.ui.vault.SamplePages;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

/**
 * The Document Vault's security core on a real phone or emulator: the Android Keystore master key, the
 * SQLCipher-encrypted database file, the sample trip end to end, and the full delete.
 *
 * <p>Each test uses its own folder and its own Keystore alias, so the app's real vault is never touched. The vault's
 * background work runs on the test thread here ({@code Runnable::run}), so every answer is there at once.
 */
@RunWith(AndroidJUnit4.class)
public class VaultOnPhoneTest {

    private Context context;
    private File folder;
    private String alias;
    private AndroidKeystoreMasterKey masterKey;
    private VaultSettings settings;
    private Vault vault;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        String run = UUID.randomUUID().toString();
        folder = new File(context.getCacheDir(), "vault-test-" + run);
        alias = "onboard_vault_test_" + run;
        masterKey = new AndroidKeystoreMasterKey(alias);
        settings = new VaultSettings(context, "onboard_vault_settings_test");
        settings.reset();
        vault = new Vault(context, folder, masterKey, settings, Runnable::run, Runnable::run, Clock.systemUTC());
    }

    @After
    public void tearDown() throws Exception {
        masterKey.destroy();
        deleteRecursively(folder);
        settings.reset();
    }

    @Test
    public void keystoreKey_neverLeavesTheKeystore_andOnlyItOpensTheFiles() throws Exception {
        SecretKey key = masterKey.get();
        assertNull("A Keystore key never hands out its bytes", key.getEncoded());

        byte[] scan = "photo page of the passport".getBytes(StandardCharsets.UTF_8);
        String fileId = UUID.randomUUID().toString();
        File files = new File(folder, "files");
        EncryptedFileStore.StoredFile stored = new EncryptedFileStore(files, masterKey).write(fileId, scan);

        // A new handle to the same Keystore entry (like after an app restart) opens it.
        EncryptedFileStore afterRestart = new EncryptedFileStore(files, new AndroidKeystoreMasterKey(alias));
        assertArrayEquals(scan, afterRestart.read(fileId, stored.wrappedKey, stored.sha256));

        // Any other Keystore key cannot.
        AndroidKeystoreMasterKey other = new AndroidKeystoreMasterKey(alias + "_other");
        try {
            assertEquals(EncryptedFileStore.Check.UNREADABLE,
                    new EncryptedFileStore(files, other).check(fileId, stored.wrappedKey, stored.sha256));
        } finally {
            other.destroy();
        }
    }

    @Test
    public void databaseFile_isEncrypted_andOnlyOpensWithItsKey() {
        File dbFolder = new File(folder, "db");
        File dbFile = new File(dbFolder, "vault.db");
        byte[] key = AesGcm.randomBytes(32);

        VaultDatabase database = VaultDatabase.open(context, dbFile, key);
        TripVaultEntity trip = trip("Secret trip to Kinshasa");
        database.dao().insertTrip(trip);
        database.close();

        // No readable SQLite header and no readable text anywhere in the database files (main file, -wal, -shm).
        for (File file : dbFolder.listFiles()) {
            String content = new String(readAll(file), StandardCharsets.ISO_8859_1);
            assertFalse(file.getName(), content.startsWith("SQLite format 3"));
            assertFalse(file.getName(), content.contains("Secret trip to Kinshasa"));
        }

        VaultDatabase wrongKey = VaultDatabase.open(context, dbFile, AesGcm.randomBytes(32));
        assertThrows(RuntimeException.class, () -> wrongKey.dao().sampleTripCount());
        wrongKey.close();

        VaultDatabase rightKey = VaultDatabase.open(context, dbFile, key);
        assertEquals(1, rightKey.dao().sampleTripCount());
        rightKey.close();
    }

    @Test
    public void deletingADocument_alsoDeletesItsPagesAndLinks() {
        VaultDatabase database = VaultDatabase.open(context, new File(folder, "vault.db"), AesGcm.randomBytes(32));
        TripVaultEntity trip = trip("Trip");
        database.dao().insertTrip(trip);
        TravelDocumentEntity document = new TravelDocumentEntity();
        document.id = UUID.randomUUID().toString();
        document.title = "Passport";
        database.dao().insertDocument(document);
        VaultFileEntity page = new VaultFileEntity();
        page.id = UUID.randomUUID().toString();
        page.documentId = document.id;
        page.mimeType = "image/jpeg";
        page.sha256 = "00";
        database.dao().insertFile(page);
        TripDocumentLinkEntity link = new TripDocumentLinkEntity();
        link.tripId = trip.id;
        link.documentId = document.id;
        database.dao().insertLinks(Collections.singletonList(link));

        database.dao().deleteDocument(document.id);

        SupportSQLiteDatabase sql = database.getOpenHelper().getReadableDatabase();
        assertEquals(0, count(sql, "document_files"));
        assertEquals(0, count(sql, "trip_document_links"));
        assertEquals(1, count(sql, "trips"));
        database.close();
    }

    @Test
    public void sampleTrip_isEncryptedAndChecked_andATamperedFileIsCaught() throws Exception {
        VaultRepository repository = vault.repository();
        String tripId = repository.loadSampleNow(new SamplePages());
        assertNotNull(tripId);
        assertNull("The sample trip is only added once", repository.loadSampleNow(new SamplePages()));

        FileCheckReport report = repository.checkAllFilesNow();
        assertEquals(8, report.checked());
        assertTrue(report.allOk());
        assertTrue(report.totalBytes() > 0);

        // Every page on disk is encrypted: none starts like a JPEG (FF D8).
        File[] pages = new File(folder, "files").listFiles();
        assertEquals(8, pages.length);
        for (File page : pages) {
            byte[] bytes = readAll(page);
            assertFalse(page.getName(), bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8);
        }

        // Change one byte of one encrypted page: the check finds exactly that one.
        byte[] tampered = readAll(pages[0]);
        tampered[tampered.length / 2] ^= 0x01;
        Files.write(pages[0].toPath(), tampered);
        FileCheckReport afterTampering = repository.checkAllFilesNow();
        assertEquals(1, afterTampering.problems().size());
        assertEquals(7, afterTampering.passed());

        // Deleting the trip deletes its documents' encrypted pages from the phone.
        AtomicBoolean deleted = new AtomicBoolean();
        repository.deleteTrip(tripId, nothing -> deleted.set(true));
        assertTrue(deleted.get());
        assertEquals(0, new File(folder, "files").listFiles().length);
        assertEquals(0, repository.checkAllFilesNow().checked());
    }

    @Test
    public void deleteEverything_leavesNoFilesAndNoKey() throws Exception {
        vault.repository().loadSampleNow(new SamplePages());
        settings.setLockEnabled(false);

        AtomicBoolean done = new AtomicBoolean();
        vault.deleteEverything(nothing -> done.set(true));

        assertTrue(done.get());
        assertFalse("The vault folder is gone", folder.exists());
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        assertFalse("The Keystore master key is gone", keyStore.containsAlias(alias));
        assertTrue("Settings are back to the safe defaults", settings.lockEnabled());
        // A brand-new, empty vault opens afterwards.
        assertEquals(0, vault.repository().checkAllFilesNow().checked());
    }

    @Test
    public void lostMasterKey_makesTheVaultUnavailable_insteadOfShowingGarbage() throws Exception {
        vault.repository(); // creates the database key, wrapped by the master key
        masterKey.destroy(); // e.g. Android's Keystore was reset

        Vault afterReset = new Vault(context, folder, new AndroidKeystoreMasterKey(alias), settings, Runnable::run,
                Runnable::run, Clock.systemUTC());
        assertThrows(VaultUnavailableException.class, afterReset::repository);
    }

    private static TripVaultEntity trip(String title) {
        TripVaultEntity trip = new TripVaultEntity();
        trip.id = UUID.randomUUID().toString();
        trip.title = title;
        trip.sample = true;
        trip.createdAt = Instant.now();
        return trip;
    }

    private static int count(SupportSQLiteDatabase sql, String table) {
        try (android.database.Cursor cursor = sql.query("SELECT COUNT(*) FROM " + table)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static byte[] readAll(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
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
