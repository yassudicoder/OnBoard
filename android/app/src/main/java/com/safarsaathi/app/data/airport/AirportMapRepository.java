package com.safarsaathi.app.data.airport;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.local.AirportMapDao;
import com.safarsaathi.app.data.local.AirportMapEntity;
import com.safarsaathi.app.data.remote.SafarSaathiApi;
import com.safarsaathi.routing.map.AirportMapValidator;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The terminal maps for Gate Navigation. Like TripRepository: screens read the saved copy (Room) as LiveData, so the
 * map works offline; {@link #refresh} asks the server for a newer one.
 *
 * <p>Where the saved copy comes from:
 * <ol>
 *   <li>{@link #ensureSaved}: the copy that ships inside the app (assets, made from mock-data/airports at build time)
 *       is saved when the phone has no copy yet, or when it is newer than the saved one (an app update with a new
 *       map), so the map opens even before the app ever reached the server;</li>
 *   <li>{@link #refresh}: GET /api/airports/{code}/{terminal}/map. A server copy replaces the saved one when its
 *       version is higher, or at the same version when the saved copy is only the bundled one.</li>
 * </ol>
 * Every copy is checked with routing-core's AirportMapValidator before it is saved; a broken one is never saved.
 * Writes run on the shared single background thread.
 */
public class AirportMapRepository {

    /** Reads a file that ships inside the app, e.g. "BOM-T2/map.json" (assets). Swappable for tests. */
    public interface BundledFiles {
        String read(String path) throws IOException;
    }

    private static final String TAG = "SafarSaathi";

    private final AirportMapDao dao;
    private final SafarSaathiApi api;
    private final BundledFiles bundled;
    private final Executor ioExecutor;
    private final Clock clock;

    private final MutableLiveData<SyncState> syncState = new MutableLiveData<>(SyncState.idle());
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    public AirportMapRepository(AirportMapDao dao, SafarSaathiApi api, BundledFiles bundled, Executor ioExecutor,
                                Clock clock) {
        this.dao = dao;
        this.api = api;
        this.bundled = bundled;
        this.ioExecutor = ioExecutor;
        this.clock = clock;
    }

    /** The saved copy of one terminal's map; null until one is saved. */
    public LiveData<AirportMapEntity> observe(String airport, String terminal) {
        return dao.observe(airport, terminal);
    }

    /** How the last refresh went (for "Map v1 · updated 01:40" / "Offline · map from this phone"). */
    public LiveData<SyncState> getSyncState() {
        return syncState;
    }

    /**
     * Saves the bundled copy when the phone has no copy of this terminal yet, or when the bundled one has a higher
     * version than the saved one (the app was updated with a newer map). Returns at once.
     */
    public void ensureSaved(String airport, String terminal) {
        ioExecutor.execute(() -> {
            String folder = airport + "-" + terminal + "/";
            try {
                String json = MapJson.combine(bundled.read(folder + "map.json"), bundled.read(folder + "areas.geojson"));
                AirportMapEntity saved = dao.get(airport, terminal);
                if (saved == null || MapJson.parse(json).map.version > saved.version) {
                    save(airport, terminal, json, AirportMapEntity.ORIGIN_BUNDLED);
                }
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "No usable bundled map for " + airport + " " + terminal, e);
            }
        });
    }

    /** Asks the server for the newest map. Returns at once; a refresh already running is not started twice. */
    public void refresh(String airport, String terminal) {
        if (!refreshRunning.compareAndSet(false, true)) {
            return;
        }
        syncState.postValue(SyncState.refreshing());
        try {
            api.getAirportMap(airport, terminal).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    ioExecutor.execute(() -> saveAnswer(airport, terminal, response));
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable error) {
                    SyncState.Problem problem = error instanceof IOException
                            ? SyncState.Problem.NO_CONNECTION : SyncState.Problem.BAD_DATA;
                    ioExecutor.execute(() -> finishWithProblem(airport, terminal, problem, error.toString()));
                }
            });
        } catch (RuntimeException e) {
            ioExecutor.execute(() -> finishWithProblem(airport, terminal, SyncState.Problem.NO_CONNECTION, e.toString()));
        }
    }

    /** Runs on the background thread. */
    private void saveAnswer(String airport, String terminal, Response<ResponseBody> response) {
        if (!response.isSuccessful() || response.body() == null) {
            SyncState.Problem problem = response.code() == 404
                    ? SyncState.Problem.NOT_FOUND : SyncState.Problem.SERVER_ERROR;
            finishWithProblem(airport, terminal, problem, "HTTP " + response.code());
            return;
        }
        try (ResponseBody body = response.body()) {
            String json = body.string();
            MapJson.MapData fresh = MapJson.parse(json);
            AirportMapEntity saved = dao.get(airport, terminal);
            boolean newer = saved == null
                    || fresh.map.version > saved.version
                    || (fresh.map.version == saved.version && AirportMapEntity.ORIGIN_BUNDLED.equals(saved.origin));
            if (newer) {
                save(airport, terminal, json, AirportMapEntity.ORIGIN_SERVER);
            }
            dao.markChecked(airport, terminal, clock.instant());
            finish(SyncState.upToDate(clock.instant()));
        } catch (IOException | RuntimeException e) {
            finishWithProblem(airport, terminal, SyncState.Problem.BAD_DATA, e.toString());
        }
    }

    /** Checks the map and saves it. Runs on the background thread. */
    private void save(String airport, String terminal, String json, String origin) {
        MapJson.MapData data = MapJson.parse(json);
        List<String> problems = AirportMapValidator.problems(data.map);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("The map has mistakes: " + problems);
        }
        AirportMapEntity entity = new AirportMapEntity();
        entity.airport = airport;
        entity.terminal = terminal;
        entity.version = data.map.version;
        entity.json = json;
        entity.origin = origin;
        entity.savedAt = clock.instant();
        entity.checkedAt = AirportMapEntity.ORIGIN_SERVER.equals(origin) ? clock.instant() : null;
        dao.save(entity);
        Log.i(TAG, "Saved map " + airport + " " + terminal + " v" + entity.version + " (" + origin + ")");
    }

    private void finishWithProblem(String airport, String terminal, SyncState.Problem problem, String detail) {
        Log.w(TAG, "Could not refresh the map of " + airport + " " + terminal + " (" + problem + "): " + detail);
        AirportMapEntity saved = null;
        try {
            saved = dao.get(airport, terminal);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not read the saved map", e);
        }
        finish(SyncState.offline(problem, detail, saved == null ? null : saved.checkedAt));
    }

    private void finish(SyncState result) {
        refreshRunning.set(false);
        syncState.postValue(result);
    }
}
