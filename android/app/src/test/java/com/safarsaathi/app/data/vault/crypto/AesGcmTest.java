package com.safarsaathi.app.data.vault.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;

/** Unit tests for the vault's one encryption recipe, AES-256-GCM ({@link AesGcm}). */
public class AesGcmTest {

    private static final byte[] PASSPORT_SCAN = "pretend these are the bytes of a passport scan"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    public void encryptThenDecrypt_givesBackTheSameBytes() throws GeneralSecurityException {
        SecretKey key = AesGcm.newKey();
        byte[] box = AesGcm.encrypt(key, PASSPORT_SCAN, "file:1");

        assertEquals(PASSPORT_SCAN.length + AesGcm.OVERHEAD_BYTES, box.length);
        assertArrayEquals(PASSPORT_SCAN, AesGcm.decrypt(key, box, "file:1"));
    }

    @Test
    public void theEncryptedBytesDoNotContainThePlainText() throws GeneralSecurityException {
        byte[] box = AesGcm.encrypt(AesGcm.newKey(), PASSPORT_SCAN, "file:1");
        String asText = new String(box, StandardCharsets.ISO_8859_1);

        assertFalse(asText.contains("passport"));
    }

    @Test
    public void sameDataTwice_encryptsDifferently() throws GeneralSecurityException {
        // A fresh random IV each time: nobody can even tell that two files are identical.
        SecretKey key = AesGcm.newKey();
        byte[] first = AesGcm.encrypt(key, PASSPORT_SCAN, "file:1");
        byte[] second = AesGcm.encrypt(key, PASSPORT_SCAN, "file:1");

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void wrongKey_fails() throws GeneralSecurityException {
        byte[] box = AesGcm.encrypt(AesGcm.newKey(), PASSPORT_SCAN, "file:1");

        assertThrows(AEADBadTagException.class, () -> AesGcm.decrypt(AesGcm.newKey(), box, "file:1"));
    }

    @Test
    public void wrongLabel_fails() throws GeneralSecurityException {
        // A box copied over another file's name: its label no longer matches.
        SecretKey key = AesGcm.newKey();
        byte[] box = AesGcm.encrypt(key, PASSPORT_SCAN, "file:1");

        assertThrows(AEADBadTagException.class, () -> AesGcm.decrypt(key, box, "file:2"));
    }

    @Test
    public void oneChangedBit_fails() throws GeneralSecurityException {
        SecretKey key = AesGcm.newKey();
        byte[] box = AesGcm.encrypt(key, PASSPORT_SCAN, "file:1");
        box[box.length / 2] ^= 0x01;

        assertThrows(AEADBadTagException.class, () -> AesGcm.decrypt(key, box, "file:1"));
    }

    @Test
    public void notASealedBox_isRefused() {
        SecretKey key = AesGcm.newKey();

        assertThrows(GeneralSecurityException.class, () -> AesGcm.decrypt(key, PASSPORT_SCAN, "file:1"));
        assertThrows(GeneralSecurityException.class, () -> AesGcm.decrypt(key, new byte[3], "file:1"));
    }

    @Test
    public void keys_areAes256() {
        assertEquals(32, AesGcm.newKey().getEncoded().length);
        assertThrows(IllegalArgumentException.class, () -> AesGcm.keyFromBytes(new byte[16]));
    }
}
