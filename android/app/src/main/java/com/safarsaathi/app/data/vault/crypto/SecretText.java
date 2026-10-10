package com.safarsaathi.app.data.vault.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Extra encryption for the most sensitive short texts, such as a passport or visa number. They are stored inside
 * the encrypted database AND encrypted again on their own with the Keystore master key, so they stay hidden even in
 * a database export or a crash report, and screens only decrypt them when they really show the number.
 * The result is Base64 text (letters, digits, +, /) so it fits in a normal text column.
 */
public final class SecretText {

    private SecretText() {
    }

    /**
     * @param label what the text is, e.g. "doc-number:" + document id. The same label is needed to decrypt, so a
     *              number copied onto another document fails to decrypt.
     */
    public static String encrypt(VaultMasterKey masterKey, String text, String label) throws GeneralSecurityException {
        byte[] box = AesGcm.encrypt(masterKey.get(), text.getBytes(StandardCharsets.UTF_8), label);
        return Base64.getEncoder().encodeToString(box);
    }

    public static String decrypt(VaultMasterKey masterKey, String encrypted, String label)
            throws GeneralSecurityException {
        byte[] box;
        try {
            box = Base64.getDecoder().decode(encrypted);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Not an encrypted vault text", e);
        }
        return new String(AesGcm.decrypt(masterKey.get(), box, label), StandardCharsets.UTF_8);
    }
}
