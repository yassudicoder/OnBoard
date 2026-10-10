package com.safarsaathi.app.data.vault.crypto;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * The vault's master key inside the Android Keystore: an AES-256-GCM key that Android makes and keeps itself.
 * Our code only ever gets a handle to it; the key bytes never enter the app's memory and cannot be exported or
 * copied to another phone (on most phones the Keystore runs in separate secure hardware, the "TEE").
 *
 * <p>Deliberately NOT tied to the fingerprint/PIN in V1 ({@code setUserAuthenticationRequired} is off):
 * <ul>
 *   <li>Android deletes such keys when the traveller removes their screen lock, which would wipe the vault;</li>
 *   <li>phones with no screen lock could not use the vault at all;</li>
 *   <li>background work (expiry reminders, V4) could not read the vault.</li>
 * </ul>
 * The fingerprint/PIN prompt (VaultSession) guards the screens instead. docs/privacy.md explains the trade-off.
 */
public final class AndroidKeystoreMasterKey implements VaultMasterKey {

    /** The Keystore entry the app uses. Tests pass their own alias so they never touch the real vault's key. */
    public static final String DEFAULT_ALIAS = "onboard_vault_master_v1";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private final String alias;
    private SecretKey cached;

    public AndroidKeystoreMasterKey(String alias) {
        this.alias = alias;
    }

    @Override
    public synchronized SecretKey get() throws GeneralSecurityException {
        if (cached != null) {
            return cached;
        }
        Key existing = keyStore().getKey(alias, null);
        if (existing instanceof SecretKey) {
            cached = (SecretKey) existing;
            return cached;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AesGcm.KEY_BYTES * 8)
                // The Keystore picks a fresh random IV for every encryption (this is also the default).
                .setRandomizedEncryptionRequired(true)
                .build());
        cached = generator.generateKey();
        return cached;
    }

    @Override
    public synchronized void destroy() throws GeneralSecurityException {
        cached = null;
        KeyStore store = keyStore();
        if (store.containsAlias(alias)) {
            store.deleteEntry(alias);
        }
    }

    private static KeyStore keyStore() throws GeneralSecurityException {
        KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
        try {
            store.load(null);
        } catch (IOException e) {
            throw new KeyStoreException("Could not open the Android Keystore", e);
        }
        return store;
    }
}
