package com.safarsaathi.app.data.vault;

/**
 * The vault cannot be opened on this phone, almost always because its Keystore master key is gone (e.g. Android
 * reset its Keystore, or the app's files were copied from another phone). The encrypted data cannot be read without
 * that key by design, so the only way forward is to delete the vault and start again.
 */
public class VaultUnavailableException extends Exception {

    public VaultUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
