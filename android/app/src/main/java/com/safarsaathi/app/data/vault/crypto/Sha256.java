package com.safarsaathi.app.data.vault.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 "fingerprint" of a file: 64 hex characters that change completely if even one byte of the file changes.
 * The vault stores it for every file when the file is added, and compares it again after decrypting (the
 * offline-ready test), so a damaged or swapped file is noticed.
 */
public final class Sha256 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha256() {
    }

    /** Lower-case hex, e.g. "9f86d081884c7d65...". */
    public static String hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                out[i * 2] = HEX[(digest[i] >> 4) & 0x0f];
                out[i * 2 + 1] = HEX[digest[i] & 0x0f];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            // Every Java and Android runtime has SHA-256.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Compares two fingerprints in constant time (MessageDigest.isEqual), so the time taken does not hint at how
     * many leading characters matched.
     */
    public static boolean same(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII),
                actualHex.getBytes(StandardCharsets.US_ASCII));
    }
}
