package com.safarsaathi.app.data.vault.crypto;

import javax.crypto.SecretKey;

/**
 * A master key for laptop tests: a plain AES-256 key in memory, standing in for the Android Keystore key (which only
 * exists on a phone; the instrumented tests use the real one).
 */
public final class TestMasterKey implements VaultMasterKey {

    private SecretKey key = AesGcm.newKey();

    @Override
    public SecretKey get() {
        if (key == null) {
            key = AesGcm.newKey(); // like the Keystore: after destroy(), a NEW key is made on next use
        }
        return key;
    }

    @Override
    public void destroy() {
        key = null;
    }
}
