package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.safarsaathi.app.data.vault.crypto.AesGcm;
import com.safarsaathi.app.data.vault.crypto.DatabaseKey;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.VaultDatabase;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The vault database is phone-only, so a new version must keep every saved document. This builds a real version-1
 * database exactly as version 1 of the app created it (the SQL in app/schemas/.../1.json, encrypted with SQLCipher),
 * then opens it with the app's own {@link VaultDatabase#open}, which runs the version 1 to 2 migration and Room's
 * schema check. (Room's MigrationTestHelper needs a newer kotlinx-serialization than the app uses, so it is not
 * used here.)
 */
@RunWith(AndroidJUnit4.class)
public class VaultMigrationTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final File file = new File(context.getCacheDir(), "vault-migration-" + UUID.randomUUID() + ".db");

    @After
    public void deleteTheTestDatabase() {
        for (String suffix : new String[] {"", "-wal", "-shm", "-journal"}) {
            //noinspection ResultOfMethodCallIgnored
            new File(file.getPath() + suffix).delete();
        }
    }

    @Test
    public void version1To2_keepsEveryDocument_andAddsEmptyHotelFields() throws Exception {
        System.loadLibrary("sqlcipher");
        byte[] key = AesGcm.randomBytes(32);
        JSONObject version1 = new JSONObject(asset("com.safarsaathi.app.data.vault.db.VaultDatabase/1.json"))
                .getJSONObject("database");

        SupportSQLiteOpenHelper oldApp = new SupportOpenHelperFactory(DatabaseKey.sqlcipherRawKey(key)).create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(file.getAbsolutePath())
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                createVersion1(db, version1);
                            }

                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                            }
                        })
                        .build());
        SupportSQLiteDatabase v1 = oldApp.getWritableDatabase();
        v1.execSQL("INSERT INTO documents (id, type, title, source, fieldsConfirmed, sample, createdAt)"
                + " VALUES ('d1', 'HOTEL', 'Hotel booking', 'MANUAL', 1, 0, 0)");
        oldApp.close();

        // The app of today opens the old file: migration 1 -> 2, then Room checks the result against version 2.
        VaultDatabase today = VaultDatabase.open(context, file, key);
        TravelDocumentEntity document = today.dao().document("d1");
        assertEquals("Hotel booking", document.title);
        assertNull(document.placeName);
        assertNull(document.address);
        today.close();
    }

    /** Runs the CREATE statements and Room's own setup (its identity hash) of version 1. */
    private static void createVersion1(SupportSQLiteDatabase db, JSONObject version1) {
        try {
            JSONArray entities = version1.getJSONArray("entities");
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.getJSONObject(i);
                String table = entity.getString("tableName");
                db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
                JSONArray indices = entity.optJSONArray("indices");
                for (int j = 0; indices != null && j < indices.length(); j++) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
                }
            }
            JSONArray setup = version1.getJSONArray("setupQueries");
            for (int i = 0; i < setup.length(); i++) {
                db.execSQL(setup.getString(i));
            }
        } catch (org.json.JSONException e) {
            throw new AssertionError(e);
        }
    }

    /** A file of the test's assets (app/schemas is added as androidTest assets in app/build.gradle.kts). */
    private static String asset(String path) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
