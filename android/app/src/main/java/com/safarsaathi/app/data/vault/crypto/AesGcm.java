package com.safarsaathi.app.data.vault.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM: the one encryption recipe the whole Document Vault uses (files, keys, document numbers).
 * Plain Java (javax.crypto), so it runs in the laptop unit tests as well as on the phone.
 *
 * <p>Every encrypted value ("sealed box") is laid out like this:
 * <pre>
 *   [ "OBV1" (4 bytes) ][ IV (12 bytes) ][ encrypted data ... ][ tag (16 bytes) ]
 * </pre>
 * <ul>
 *   <li>"OBV1" = OnBoard Vault, format 1. Lets us recognise our own data and change the format later.</li>
 *   <li>IV ("nonce"): 12 random bytes, new for every encryption. GCM is only safe if one key never sees the same IV
 *       twice. We never choose the IV ourselves: the cipher picks a random one (the Android Keystore even insists on
 *       that) and we store it in front of the data.</li>
 *   <li>Tag: GCM's built-in tamper seal. If a single bit of the box was changed, or the wrong key is used,
 *       {@link #decrypt} throws instead of returning garbage.</li>
 *   <li>Label ("associated data"): a short text such as the file's id. It is checked but not stored. A box copied
 *       over another one (say, someone swaps two files) fails to open, because its label no longer matches.</li>
 * </ul>
 */
public final class AesGcm {

    /** AES-256 = a 32-byte key. */
    public static final int KEY_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte[] MAGIC = {'O', 'B', 'V', '1'};
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    /** Bytes a sealed box adds on top of the data itself: magic + IV + tag. */
    public static final int OVERHEAD_BYTES = MAGIC.length + IV_BYTES + TAG_BITS / 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcm() {
    }

    /** A brand-new random AES-256 key (used for one file, or as the database password). */
    public static SecretKey newKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(KEY_BYTES * 8, RANDOM);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            // Every Java and Android runtime has AES; this cannot happen on a working phone.
            throw new IllegalStateException("AES is not available", e);
        }
    }

    /** Turns 32 stored bytes back into a key (after unwrapping them). */
    public static SecretKey keyFromBytes(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("An AES-256 key is exactly " + KEY_BYTES + " bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** Random bytes from the phone's secure random generator. */
    public static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Encrypts {@code plaintext} into a sealed box.
     *
     * @param label checked again by {@link #decrypt}; say what the data is, e.g. "file:3f2a..."
     */
    public static byte[] encrypt(SecretKey key, byte[] plaintext, String label) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        // No IV given: the cipher makes a random one. (Android Keystore keys refuse an IV chosen by the app.)
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_BYTES) {
            throw new GeneralSecurityException("Unexpected GCM IV length");
        }
        cipher.updateAAD(labelBytes(label));
        byte[] encrypted = cipher.doFinal(plaintext);

        byte[] box = new byte[MAGIC.length + IV_BYTES + encrypted.length];
        System.arraycopy(MAGIC, 0, box, 0, MAGIC.length);
        System.arraycopy(iv, 0, box, MAGIC.length, IV_BYTES);
        System.arraycopy(encrypted, 0, box, MAGIC.length + IV_BYTES, encrypted.length);
        return box;
    }

    /**
     * Opens a sealed box made by {@link #encrypt}.
     *
     * @throws AEADBadTagException when the key or label is wrong, or the box was changed in any way
     * @throws GeneralSecurityException when the bytes are not a sealed box at all
     */
    public static byte[] decrypt(SecretKey key, byte[] box, String label) throws GeneralSecurityException {
        if (box == null || box.length < OVERHEAD_BYTES
                || !Arrays.equals(Arrays.copyOfRange(box, 0, MAGIC.length), MAGIC)) {
            throw new GeneralSecurityException("Not an OnBoard vault sealed box");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec iv = new GCMParameterSpec(TAG_BITS, box, MAGIC.length, IV_BYTES);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        cipher.updateAAD(labelBytes(label));
        int start = MAGIC.length + IV_BYTES;
        return cipher.doFinal(box, start, box.length - start);
    }

    private static byte[] labelBytes(String label) {
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("Every sealed box needs a label");
        }
        return label.getBytes(StandardCharsets.UTF_8);
    }
}
