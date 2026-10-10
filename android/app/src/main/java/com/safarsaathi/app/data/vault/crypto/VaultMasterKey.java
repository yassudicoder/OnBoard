package com.safarsaathi.app.data.vault.crypto;

import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

/**
 * The vault's master key. It never encrypts documents itself: it only locks ("wraps") the small keys that do (one
 * per file, plus the database password) and the document numbers.
 *
 * <p>On the phone this is {@link AndroidKeystoreMasterKey}: the key lives inside the Android Keystore (secure
 * hardware on most phones) and cannot be copied out. Laptop unit tests use a plain in-memory key instead, so the
 * rest of the vault code can be tested without a phone.
 */
public interface VaultMasterKey {

    /** The key, created on first use. */
    SecretKey get() throws GeneralSecurityException;

    /**
     * Deletes the key for good. Everything it protected becomes unreadable, which is how "delete my vault" makes
     * sure nothing can be recovered from the phone afterwards.
     */
    void destroy() throws GeneralSecurityException;
}
