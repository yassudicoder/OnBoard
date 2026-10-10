package com.safarsaathi.app.data.vault.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;

/** Unit tests for the vault database's key ({@link DatabaseKey}) and the document-number encryption. */
public class DatabaseKeyTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void createdOnce_thenTheSameKeyEveryTime() throws Exception {
        File keyFile = new File(temporary.getRoot(), "vault/db.key");
        TestMasterKey masterKey = new TestMasterKey();

        byte[] first = DatabaseKey.getOrCreate(keyFile, masterKey);
        byte[] second = DatabaseKey.getOrCreate(keyFile, masterKey);

        assertEquals(32, first.length);
        assertArrayEquals(first, second);
        // Only the WRAPPED key is on disk: a sealed box that only the master key opens.
        byte[] onDisk = Files.readAllBytes(keyFile.toPath());
        assertEquals(32 + AesGcm.OVERHEAD_BYTES, onDisk.length);
        assertArrayEquals(first, AesGcm.decrypt(masterKey.get(), onDisk, "vault-database-key"));
    }

    @Test
    public void anotherMasterKey_cannotUnwrapIt() throws Exception {
        File keyFile = new File(temporary.getRoot(), "db.key");
        DatabaseKey.getOrCreate(keyFile, new TestMasterKey());

        assertThrows(GeneralSecurityException.class, () -> DatabaseKey.getOrCreate(keyFile, new TestMasterKey()));
    }

    @Test
    public void sqlcipherRawKey_isTheDocumentedHexForm() {
        byte[] key = new byte[32];
        key[0] = (byte) 0xAB;
        key[31] = 0x01;
        String raw = new String(DatabaseKey.sqlcipherRawKey(key), StandardCharsets.US_ASCII);

        assertEquals(67, raw.length()); // x' + 64 hex digits + '
        assertTrue(raw.startsWith("x'AB00"));
        assertTrue(raw.endsWith("0001'"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseKey.sqlcipherRawKey(new byte[16]));
    }

    @Test
    public void secretText_roundTrip_andTheLabelMustMatch() throws Exception {
        TestMasterKey masterKey = new TestMasterKey();
        String encrypted = SecretText.encrypt(masterKey, "S0000001", "doc-number:a");

        assertFalse(encrypted.contains("S0000001"));
        assertEquals("S0000001", SecretText.decrypt(masterKey, encrypted, "doc-number:a"));
        // A number copied onto another document does not decrypt.
        assertThrows(GeneralSecurityException.class, () -> SecretText.decrypt(masterKey, encrypted, "doc-number:b"));
        assertThrows(GeneralSecurityException.class, () -> SecretText.decrypt(masterKey, "not base64!", "x"));
    }
}
