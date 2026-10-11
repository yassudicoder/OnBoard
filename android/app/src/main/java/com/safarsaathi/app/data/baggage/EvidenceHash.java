package com.safarsaathi.app.data.baggage;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 fingerprint of a photo file: 64 hex characters that change completely if even one pixel of the file changes.
 * Printed on the claim pack next to each photo, so anyone can later check the photo was not edited after it was added
 * (re-compute it and compare). Plain Java ({@code java.security.MessageDigest}), unit tested.
 */
public final class EvidenceHash {

    private EvidenceHash() {
    }

    public static String sha256(InputStream in) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java and Android runtime has SHA-256", e);
        }
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            text.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return text.toString();
    }
}
