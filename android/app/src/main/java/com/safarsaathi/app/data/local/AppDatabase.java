package com.safarsaathi.app.data.local;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/**
 * The app's offline database (file "safar_saathi.db" on the phone). Room generates the code behind it.
 * Created once, in ServiceLocator.
 *
 * <p>Version 1 = Phase 1 tables (trips, blocks, links).
 * Version 2 = blocks also remember their originally planned times (plannedStartTime, plannedEndTime).
 * Version 3 = the Wallet and Profile tables: documents (boarding passes) and saved_cards.
 * Version 4 = id_documents: Aadhaar (last 4 digits only), PAN and visa details for the Wallet.
 *
 * <p>Going from one version to the next: an {@code @AutoMigration} lets Room write the upgrade itself from the
 * exported schemas in app/schemas/ (1.json to 4.json; see app/build.gradle.kts). Version 1 -> 2 only adds
 * two empty (null) columns, the later versions only add new tables, so the saved plan survives the app update and
 * still opens offline. A version change WITHOUT a migration makes ServiceLocator rebuild the database from scratch.
 * Since version 3 that would also delete the traveller's boarding passes, IDs and cards, which exist only on the
 * phone, so every future version must come with a migration.
 */
@Database(
        entities = {TripEntity.class, BlockEntity.class, LinkEntity.class, DocumentEntity.class,
                SavedCardEntity.class, IdDocumentEntity.class},
        version = 4,
        exportSchema = true,
        autoMigrations = {@AutoMigration(from = 1, to = 2), @AutoMigration(from = 2, to = 3),
                @AutoMigration(from = 3, to = 4)})
@TypeConverters(TimeConverters.class)
public abstract class AppDatabase extends RoomDatabase {

    /** File name of the database on the phone. */
    public static final String NAME = "safar_saathi.db";

    public abstract TripDao tripDao();

    public abstract WalletDao walletDao();
}
