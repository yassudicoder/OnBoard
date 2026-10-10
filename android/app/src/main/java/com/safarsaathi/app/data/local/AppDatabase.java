package com.safarsaathi.app.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/**
 * The app's offline database (file "safar_saathi.db" on the phone). Room generates the code behind it.
 * Created once, in ServiceLocator.
 *
 * <p>Version 1 = Phase 1 tables (trips, blocks, links). When a later phase adds or changes a table, raise
 * the version number. Until we write real migrations, ServiceLocator lets Room simply rebuild the database
 * on a version change: it only holds a copy of server data, which the next refresh downloads again.
 * The schema of each version is exported to app/schemas/ (see app/build.gradle.kts).
 */
@Database(
        entities = {TripEntity.class, BlockEntity.class, LinkEntity.class},
        version = 1,
        exportSchema = true)
@TypeConverters(TimeConverters.class)
public abstract class AppDatabase extends RoomDatabase {

    /** File name of the database on the phone. */
    public static final String NAME = "safar_saathi.db";

    public abstract TripDao tripDao();
}
