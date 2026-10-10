package com.safarsaathi.app.data.vault.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.AEADBadTagException;

/**
 * Unit tests for the vault's encrypted files ({@link EncryptedFileStore}): round trip, wrong key, the SHA-256 check,
 * and what happens when a file on the phone is changed, swapped or deleted.
 */
public class EncryptedFileStoreTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private static final byte[] SCAN = "JPEG bytes of the photo page: PARAMVIR SINGH S0000001"
            .getBytes(StandardCharsets.UTF_8);

    private TestMasterKey masterKey;
    private EncryptedFileStore store;
    private File folder;

    @Before
    public void setUp() throws IOException {
        folder = temporary.newFolder("files");
        masterKey = new TestMasterKey();
        store = new EncryptedFileStore(folder, masterKey);
    }

    @Test
    public void roundTrip_andTheFileOnDiskIsEncrypted() throws Exception {
        String id = newId();
        EncryptedFileStore.StoredFile stored = store.write(id, SCAN);

        assertArrayEquals(SCAN, store.read(id, stored.wrappedKey, stored.sha256));
        assertEquals(SCAN.length, stored.sizeBytes);
        assertEquals(Sha256.hex(SCAN), stored.sha256);

        String onDisk = new String(Files.readAllBytes(store.fileFor(id).toPath()), StandardCharsets.ISO_8859_1);
        assertFalse(onDisk.contains("PARAMVIR"));
        assertFalse(onDisk.contains("S0000001"));
        // Only the finished file is left, no temporary one.
        assertEquals(1, folder.listFiles().length);
    }

    @Test
    public void everyFileHasItsOwnKey() throws Exception {
        EncryptedFileStore.StoredFile first = store.write(newId(), SCAN);
        EncryptedFileStore.StoredFile second = store.write(newId(), SCAN);

        byte[] firstKey = AesGcm.decrypt(masterKey.get(), first.wrappedKey, "file-key:" + first.fileId);
        byte[] secondKey = AesGcm.decrypt(masterKey.get(), second.wrappedKey, "file-key:" + second.fileId);
        assertFalse(Arrays.equals(firstKey, secondKey));
    }

    @Test
    public void wrongMasterKey_cannotRead() throws Exception {
        String id = newId();
        EncryptedFileStore.StoredFile stored = store.write(id, SCAN);
        EncryptedFileStore otherPhone = new EncryptedFileStore(folder, new TestMasterKey());

        assertThrows(AEADBadTagException.class, () -> otherPhone.read(id, stored.wrappedKey, stored.sha256));
        assertEquals(EncryptedFileStore.Check.UNREADABLE, otherPhone.check(id, stored.wrappedKey, stored.sha256));
    }

    @Test
    public void deletedMasterKey_makesFilesUnreadable() throws Exception {
        String id = newId();
        EncryptedFileStore.StoredFile stored = store.write(id, SCAN);
        masterKey.destroy();

        assertEquals(EncryptedFileStore.Check.UNREADABLE, store.check(id, stored.wrappedKey, stored.sha256));
    }

    @Test
    public void hashCheck_passesForAnUntouchedFile() throws Exception {
        String id = newId();
        EncryptedFileStore.StoredFile stored = store.write(id, SCAN);

        assertEquals(EncryptedFileStore.Check.OK, store.check(id, stored.wrappedKey, stored.sha256));
    }

    @Test
    public void hashCheck_failsWhenTheSavedFingerprintDoesNotMatch() throws Exception {
        // E.g. the database row says this should be a different file than the one that decrypts.
        String id = newId();
        EncryptedFileStore.StoredFile stored = store.write(id, SCAN);
        String otherFingerprint = Sha256.hex("another document".getBytes(StandardCharsets.UTF_8));

        assertEquals(EncryptedFileStore.Check.CHANGED, store.check(id, stored.wrappedKey, otherFingerprint));
        assertThrows(EncryptedFileStore.IntegrityException.class,
                () -> store.read(id, stored.wrappedKey, otherFingerprint));
    }

    @Test
    public void aChangedByteOnDisk_isNoticed() throws Exception {
        String id = newId();
        EncryptedFileStore.StoredFile stored = store.write(id, SCAN);
        File file = store.fileFor(id);
        byte[] bytes = Files.readAllBytes(file.toPath());
        bytes[bytes.length - 20] ^= 0x40;
        Files.write(file.toPath(), bytes);

        assertEquals(EncryptedFileStore.Check.UNREADABLE, store.check(id, stored.wrappedKey, stored.sha256));
    }

    @Test
    public void twoFilesSwappedOnDisk_areNoticed() throws Exception {
        String first = newId();
        String second = newId();
        EncryptedFileStore.StoredFile firstStored = store.write(first, SCAN);
        store.write(second, "the hotel booking".getBytes(StandardCharsets.UTF_8));
        Files.copy(store.fileFor(second).toPath(), store.fileFor(first).toPath(), StandardCopyOption.REPLACE_EXISTING);

        assertEquals(EncryptedFileStore.Check.UNREADABLE,
                store.check(first, firstStored.wrappedKey, firstStored.sha256));
    }

    @Test
    public void missingFile_isReportedAsMissing() throws Exception {
        String id = newId();
        EncryptedFileStore.StoredFile stored = store.write(id, SCAN);
        store.delete(id);

        assertFalse(store.fileFor(id).exists());
        assertEquals(EncryptedFileStore.Check.MISSING, store.check(id, stored.wrappedKey, stored.sha256));
    }

    @Test
    public void idsThatCouldLeaveTheFolder_areRefused() {
        assertThrows(IllegalArgumentException.class, () -> store.write("../../shared_prefs/x", SCAN));
        assertThrows(IllegalArgumentException.class, () -> store.fileFor("not-a-uuid"));
    }

    @Test
    public void tooLargeFiles_areRefused() {
        byte[] huge = new byte[EncryptedFileStore.MAX_FILE_BYTES + 1];
        assertThrows(IOException.class, () -> store.write(newId(), huge));
        assertEquals(0, folder.listFiles().length);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
