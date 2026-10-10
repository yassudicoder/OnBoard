package com.safarsaathi.app.data.vault;

import android.content.Context;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.vault.crypto.AndroidKeystoreMasterKey;
import com.safarsaathi.app.data.vault.crypto.DatabaseKey;
import com.safarsaathi.app.data.vault.crypto.EncryptedFileStore;
import com.safarsaathi.app.data.vault.crypto.VaultMasterKey;
import com.safarsaathi.app.data.vault.db.VaultDatabase;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.concurrent.Executor;

/**
 * The Document Vault's front door, created once in ServiceLocator. It owns the vault's lock state and settings, and
 * opens the encrypted database the first time a vault screen needs it.
 *
 * <p>Everything the vault keeps on the phone is in ONE private folder, {@code no_backup/vault/}:
 * <pre>
 *   vault.db (+ -wal, -shm)   the SQLCipher-encrypted database
 *   db.key                    the database key, wrapped by the Keystore master key
 *   files/&lt;id&gt;.bin           every page and PDF, each encrypted with its own key
 * </pre>
 * Android never copies "no_backup" into cloud backups or to a new phone. Together with the Keystore master key,
 * that folder is the whole vault, which is what makes {@link #deleteEverything} a complete delete.
 */
public final class Vault {

    private final Context appContext;
    private final File folder;
    private final VaultMasterKey masterKey;
    private final VaultSettings settings;
    private final VaultSession session;
    private final Executor vaultExecutor;
    private final Executor mainExecutor;
    private final Clock clock;

    @Nullable
    private VaultRepository repository;

    public Vault(Context context, File folder, VaultMasterKey masterKey, VaultSettings settings, Executor vaultExecutor,
                 Executor mainExecutor, Clock clock) {
        this.appContext = context.getApplicationContext();
        this.folder = folder;
        this.masterKey = masterKey;
        this.settings = settings;
        this.session = new VaultSession(clock, settings);
        this.vaultExecutor = vaultExecutor;
        this.mainExecutor = mainExecutor;
        this.clock = clock;
    }

    /** The app's real vault: folder no_backup/vault, the real Keystore key and the real settings file. */
    public static Vault create(Context context, Executor vaultExecutor, Executor mainExecutor, Clock clock) {
        return new Vault(context, new File(context.getNoBackupFilesDir(), "vault"),
                new AndroidKeystoreMasterKey(AndroidKeystoreMasterKey.DEFAULT_ALIAS),
                new VaultSettings(context, VaultSettings.FILE), vaultExecutor, mainExecutor, clock);
    }

    public VaultSettings settings() {
        return settings;
    }

    public VaultSession session() {
        return session;
    }

    /**
     * The ONE way for other parts of the app (Ask OnBoard, Ride hand-off) to know about the vault's documents:
     * read-only, never numbers / MRZ / images / file paths / full names / birth dates, and only hotel name + address
     * while locked. See {@link VaultSummaryReader}.
     */
    public VaultSummaryReader summaryReader() {
        return new VaultSummaryReader(this, vaultExecutor, mainExecutor, clock);
    }

    /**
     * The vault's data, opening it the first time. Opening unwraps the database key with the Keystore (a few
     * milliseconds; the very first time Android also creates the master key). The database file itself is only
     * read later, on a background thread, by the first query.
     *
     * @throws VaultUnavailableException when the vault exists but its key is gone; see that class
     */
    public synchronized VaultRepository repository() throws VaultUnavailableException {
        if (repository == null) {
            try {
                byte[] databaseKey = DatabaseKey.getOrCreate(new File(folder, "db.key"), masterKey);
                VaultDatabase database = VaultDatabase.open(appContext, new File(folder, "vault.db"), databaseKey);
                EncryptedFileStore files = new EncryptedFileStore(new File(folder, "files"), masterKey);
                repository = new VaultRepository(database, files, masterKey, vaultExecutor, mainExecutor, clock);
            } catch (IOException | GeneralSecurityException e) {
                throw new VaultUnavailableException("The vault's key could not be read on this phone", e);
            }
        }
        return repository;
    }

    /**
     * Full delete (India's DPDP Act: the traveller can erase their data): closes the database, deletes the whole
     * vault folder, deletes the Keystore master key (so even a leftover copy could never be decrypted), and resets
     * the vault settings and lock. The next vault screen starts a brand-new, empty vault.
     *
     * <p>Leave the vault screens (or make sure none observes the old data) before calling this.
     */
    public void deleteEverything(VaultRepository.Callback<Void> callback) {
        vaultExecutor.execute(() -> {
            try {
                synchronized (this) {
                    if (repository != null) {
                        repository.close();
                        repository = null;
                    }
                    deleteRecursively(folder);
                    masterKey.destroy();
                }
                settings.reset();
                session.lockNow();
                mainExecutor.execute(() -> callback.onDone(null));
            } catch (GeneralSecurityException | RuntimeException e) {
                mainExecutor.execute(() -> callback.onError(e));
            }
        });
    }

    /** The vault's folder (tests check that a full delete leaves nothing behind). */
    public File folder() {
        return folder;
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
