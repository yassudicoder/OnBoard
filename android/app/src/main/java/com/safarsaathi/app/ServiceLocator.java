package com.safarsaathi.app;

import android.app.Application;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.room.Room;

import com.safarsaathi.app.data.TripRepository;
import com.safarsaathi.app.data.local.AppDatabase;
import com.safarsaathi.app.data.remote.SafarSaathiApi;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Our hand-made "dependency container": the ONE place where the app's shared objects are created.
 *
 * <p>Why: screens should not build their own database or network client. They ask the ServiceLocator
 * ({@code ServiceLocator.get().getTripRepository()}), so every screen shares the same objects, and tests can
 * swap them. This is the simple, no-magic alternative to libraries such as Hilt or Dagger (CLAUDE.md: no Hilt).
 *
 * <p>It is a singleton: {@link SafarSaathiApp} calls {@link #init} once when the app starts.
 */
public final class ServiceLocator {

    /** Give up connecting to the backend after this long (the laptop is either there or not). */
    private static final long CONNECT_TIMEOUT_SECONDS = 5;
    /** Give up waiting for an answer after this long. */
    private static final long READ_TIMEOUT_SECONDS = 10;

    private static ServiceLocator instance;

    private final AppConfig appConfig;
    private final AppDatabase database;
    private final OkHttpClient okHttpClient;
    private final SafarSaathiApi api;
    private final ExecutorService ioExecutor;
    private final TripRepository tripRepository;

    /** Set only by instrumented tests, see {@link #setTripRepositoryForTests}. */
    @Nullable
    private volatile TripRepository tripRepositoryForTests;

    private ServiceLocator(Application app) {
        // Settings from local.properties, baked into BuildConfig at build time (see app/build.gradle.kts).
        appConfig = new AppConfig(BuildConfig.BACKEND_BASE_URL, BuildConfig.HAS_MAPS_KEY);

        // Each object below may use the ones above it. "app" is the Application context, which lives as
        // long as the app, so it is safe to keep.

        // Room: the offline copy of the trip, the screens' source of truth.
        // fallbackToDestructiveMigration(true): when a later phase raises the database version without a
        // migration, Room deletes and recreates the tables instead of crashing. That is fine here: the
        // database only holds a copy of server data, and the next refresh downloads it again.
        database = Room.databaseBuilder(app, AppDatabase.class, AppDatabase.NAME)
                .fallbackToDestructiveMigration(true)
                .build();

        okHttpClient = createOkHttpClient(BuildConfig.DEBUG);

        // Retrofit turns the SafarSaathiApi interface into real HTTP calls; Gson reads the JSON answers.
        api = new Retrofit.Builder()
                .baseUrl(appConfig.getBackendBaseUrl())
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SafarSaathiApi.class);

        // ONE background thread for all database writes: they happen one after another, never at the
        // same time, and never on the main thread (which would freeze the screen).
        ioExecutor = Executors.newSingleThreadExecutor(task -> new Thread(task, "safar-saathi-io"));

        tripRepository = new TripRepository(database.tripDao(), api, ioExecutor, Clock.systemUTC());
    }

    /**
     * Short timeouts: on stage, "offline" should show within seconds, not after a minute.
     * In debug builds every request is also logged in one line (method, URL, status, time) in Logcat
     * under the tag "okhttp.OkHttpClient". BASIC level never logs headers or bodies.
     */
    private static OkHttpClient createOkHttpClient(boolean debug) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (debug) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(logging);
        }
        return builder.build();
    }

    /** Creates the container. Called once from {@link SafarSaathiApp#onCreate()}; later calls do nothing. */
    public static synchronized void init(Application app) {
        if (instance == null) {
            instance = new ServiceLocator(app);
        }
    }

    /** Returns the container. Only valid after {@link #init} (which always runs before any screen opens). */
    public static synchronized ServiceLocator get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ServiceLocator.init() has not run yet. It is called in SafarSaathiApp.onCreate().");
        }
        return instance;
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    public SafarSaathiApi getApi() {
        return api;
    }

    /** The single background thread for database writes. */
    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    /** Trip data for the screens (the test replacement, if a test set one). */
    public TripRepository getTripRepository() {
        TripRepository replacement = tripRepositoryForTests;
        return replacement != null ? replacement : tripRepository;
    }

    // ============================================================================================
    // TEST HOOK - for instrumented tests (src/androidTest) only. Never call this from app code.
    // A UI test builds a TripRepository with an in-memory database and a fake SafarSaathiApi, puts a
    // trip into it, and sets it here BEFORE opening a screen. Screens then show exactly that trip, with
    // or without a backend running. Pass null (in the test's @After) to go back to the real one.
    // ============================================================================================
    @VisibleForTesting
    public void setTripRepositoryForTests(@Nullable TripRepository replacement) {
        tripRepositoryForTests = replacement;
    }
}
