package com.safarsaathi.app.data.vault.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.safarsaathi.app.data.local.TimeConverters;
import com.safarsaathi.app.data.vault.crypto.DatabaseKey;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import java.io.File;

/**
 * The Document Vault's own database, SEPARATE from the app's main database (safar_saathi.db). The whole file is
 * encrypted by SQLCipher (AES-256): without the key, the file on the phone is random-looking bytes, not tables.
 * Room still writes all the SQL for us; SQLCipher only replaces the storage engine underneath.
 *
 * <p>The key comes from {@link DatabaseKey} (32 random bytes, kept wrapped by the Keystore master key). The file
 * lives in {@code no_backup/vault/vault.db}, which Android never copies into cloud backups.
 *
 * <p>Version 1 = V1 tables. Everything in here exists ONLY on the phone, so every future version needs a
 * migration ({@code @AutoMigration}, schemas in app/schemas/); there is deliberately no destructive fallback.
 */
@Database(
        entities = {TravellerEntity.class, TripVaultEntity.class, TripTravellerEntity.class, TripLegEntity.class,
                TripCountryEntity.class, TravelDocumentEntity.class, VaultFileEntity.class,
                TripDocumentLinkEntity.class},
        version = 1,
        exportSchema = true)
@TypeConverters({TimeConverters.class, VaultConverters.class})
public abstract class VaultDatabase extends RoomDatabase {

    private static boolean sqlcipherLoaded;

    public abstract VaultDao dao();

    /**
     * Opens (or, the first time, creates) the encrypted database file. Cheap: Room only really opens the file on
     * the first query, which runs on a background thread.
     *
     * @param key the 32-byte database key from {@link DatabaseKey#getOrCreate}
     */
    public static VaultDatabase open(Context context, File file, byte[] key) {
        loadSqlcipher();
        return Room.databaseBuilder(context.getApplicationContext(), VaultDatabase.class, file.getAbsolutePath())
                .openHelperFactory(new SupportOpenHelperFactory(DatabaseKey.sqlcipherRawKey(key)))
                .build();
    }

    /** SQLCipher is native (C) code; it must be loaded into the app once before the first database opens. */
    private static synchronized void loadSqlcipher() {
        if (!sqlcipherLoaded) {
            System.loadLibrary("sqlcipher");
            sqlcipherLoaded = true;
        }
    }
}
