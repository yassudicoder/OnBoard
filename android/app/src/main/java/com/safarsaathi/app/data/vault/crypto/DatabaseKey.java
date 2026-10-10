package com.safarsaathi.app.data.vault.crypto;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;

/**
 * The vault database's password: 32 random bytes, made once on this phone the first time the vault opens.
 *
 * <p>It is only ever saved WRAPPED (encrypted with the Keystore {@link VaultMasterKey}) in a small file next to the
 * database. Each time the vault opens, the master key unwraps it in memory and hands it to SQLCipher. Copying the
 * database and this file to another phone is useless: the master key cannot leave this phone.
 */
public final class DatabaseKey {

    private static final String LABEL = "vault-database-key";

    private DatabaseKey() {
    }

    /**
     * Returns the database password, making and saving a new one the first time.
     *
     * @throws GeneralSecurityException when the saved password cannot be unwrapped, e.g. the master key is gone
     *                                  (Android's Keystore was reset). The vault then cannot be opened any more.
     */
    public static byte[] getOrCreate(File wrappedKeyFile, VaultMasterKey masterKey)
            throws IOException, GeneralSecurityException {
        if (wrappedKeyFile.isFile()) {
            byte[] wrapped = Files.readAllBytes(wrappedKeyFile.toPath());
            return AesGcm.decrypt(masterKey.get(), wrapped, LABEL);
        }
        byte[] key = AesGcm.randomBytes(AesGcm.KEY_BYTES);
        byte[] wrapped = AesGcm.encrypt(masterKey.get(), key, LABEL);
        File folder = wrappedKeyFile.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Could not create the vault folder");
        }
        try (FileOutputStream out = new FileOutputStream(wrappedKeyFile)) {
            out.write(wrapped);
            out.getFD().sync();
        }
        return key;
    }

    /**
     * The password in SQLCipher's "raw key" form: the text {@code x'<64 hex digits>'}.
     *
     * <p>Why: for a normal password SQLCipher first runs 256,000 rounds of PBKDF2 to make guessing slow, which takes
     * up to a second on a phone. Our password is already 32 truly random bytes, so guessing is hopeless anyway; the
     * raw form tells SQLCipher to use the bytes as the key directly (documented at zetetic.net/sqlcipher).
     */
    public static byte[] sqlcipherRawKey(byte[] key) {
        if (key.length != AesGcm.KEY_BYTES) {
            throw new IllegalArgumentException("The database key is exactly " + AesGcm.KEY_BYTES + " bytes");
        }
        StringBuilder text = new StringBuilder("x'");
        for (byte b : key) {
            text.append(String.format("%02X", b & 0xff));
        }
        text.append('\'');
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
