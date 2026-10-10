package com.safarsaathi.app.ui.navigate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.airport.AirportMapRepository;
import com.safarsaathi.app.data.airport.MapJson;
import com.safarsaathi.app.data.local.AirportMapEntity;
import com.safarsaathi.routing.map.MapNode;
import com.safarsaathi.routing.route.WalkProfile;

/**
 * Data for the Navigate tab: the saved map of the pilot terminal (Room, so it works offline), how the last check with
 * the server went, and the navigation itself ({@link NavSession}: route, progress, turn-by-turn). It survives screen
 * rotation, so a walk keeps going, and keeps the navigation notification ({@link NavNotification}) up to date, also
 * while the screen is off. The screen never talks to the network itself.
 */
public class MapViewModel extends ViewModel {

    /** Something to tell the traveller once (snackbar + voice), e.g. "New route from Central hall". */
    public static final class Notice {
        public enum Kind { NO_ROUTE, POSITION_CONFIRMED, REROUTED, ROUTE_UPDATED, TURN_CONFIRMED, ARRIVED, QR_UNKNOWN }

        public final Kind kind;
        private boolean handled;

        Notice(Kind kind) {
            this.kind = kind;
        }

        /** True the first time only, so a notice is not repeated after the screen is rebuilt. */
        public boolean take() {
            if (handled) {
                return false;
            }
            handled = true;
            return true;
        }
    }

    /** Demo walk speeds: 1x = real walking pace, 4x and 8x to show a whole route in a short demo. */
    public static final int[] DEMO_SPEEDS = {1, 4, 8};
    private static final long TICK_MS = 100;

    private final AirportMapRepository repository;
    private final NavNotification notification;
    private final LiveData<AirportMapEntity> savedMap;
    private final LiveData<MapJson.MapData> mapData;
    private final NavSession session = new NavSession();
    private final MutableLiveData<NavSession> nav = new MutableLiveData<>(session);
    private final MutableLiveData<Notice> notice = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Observer<MapJson.MapData> mapObserver;

    private boolean demoWalking;
    private int demoSpeedIndex = 1;
    private long lastTickAt;
    private final Runnable demoTick = new Runnable() {
        @Override
        public void run() {
            if (!demoWalking) {
                return;
            }
            // Real time since the last tick (ticks can come late on a busy phone), so 1x is really walking pace.
            long now = SystemClock.elapsedRealtime();
            double seconds = Math.min(1.0, (now - lastTickAt) / 1000.0);
            lastTickAt = now;
            report(session.walkFor(seconds * DEMO_SPEEDS[demoSpeedIndex]));
            if (session.mode() != NavSession.Mode.NAVIGATING) {
                demoWalking = false;
            }
            publish();
            if (demoWalking) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    public MapViewModel(AirportMapRepository repository, NavNotification notification) {
        this.repository = repository;
        this.notification = notification;
        savedMap = repository.observe(AppConfig.PILOT_AIRPORT, AppConfig.PILOT_TERMINAL);
        // Parsed once per saved copy (about 30 KB of JSON: a few milliseconds).
        mapData = Transformations.map(savedMap, MapViewModel::parseOrNull);
        mapObserver = data -> {
            if (data != null) {
                session.setMap(data.map);
                publish();
            }
        };
        mapData.observeForever(mapObserver);
    }

    // ---- Map ----

    /** The saved copy: version, origin (inside the app / from the server), when it was checked. */
    public LiveData<AirportMapEntity> getSavedMap() {
        return savedMap;
    }

    /** The parsed map, or null while none is saved. */
    public LiveData<MapJson.MapData> getMapData() {
        return mapData;
    }

    public LiveData<SyncState> getSyncState() {
        return repository.getSyncState();
    }

    /** The tab opened: make sure a copy is saved (the one inside the app), then ask the server for a newer one. */
    public void loadAndRefresh() {
        repository.ensureSaved(AppConfig.PILOT_AIRPORT, AppConfig.PILOT_TERMINAL);
        repository.refresh(AppConfig.PILOT_AIRPORT, AppConfig.PILOT_TERMINAL);
    }

    // ---- Navigation ----

    /** The navigation state; a new value is published after every change (10 times a second during a demo walk). */
    public LiveData<NavSession> getNav() {
        return nav;
    }

    public LiveData<Notice> getNotice() {
        return notice;
    }

    /** Walking pace and step-free choice (from the traveller's settings). */
    public void setRouteOptions(boolean stepFree, boolean slowPace) {
        WalkProfile profile = new WalkProfile(slowPace ? WalkProfile.SLOW_SPEED : WalkProfile.NORMAL_SPEED, stepFree);
        report(session.setProfile(profile));
        publish();
    }

    /** "I'm here" on a place, or a place picked in "Where are you?". */
    public void setHere(String nodeId) {
        report(session.setHere(nodeId, NavSession.HereSource.CHOSEN));
        publish();
    }

    /** A scanned QR code: a "You are here" code of this map puts the traveller exactly there. */
    public void onQrScanned(String text) {
        MapNode node = session.map() == null || text == null ? null : session.map().anchorNode(text.trim());
        if (node == null) {
            notice.setValue(new Notice(Notice.Kind.QR_UNKNOWN));
            return;
        }
        report(session.setHere(node.id, NavSession.HereSource.QR));
        publish();
    }

    /** "Directions" to a place: shows the route preview. */
    public void setDestination(String nodeId) {
        stopDemoWalk();
        report(session.setDestination(nodeId));
        publish();
    }

    /** A stop on the way (from "Help nearby"); afterwards the screen offers to continue. */
    public void detourTo(String nodeId) {
        report(session.detourTo(nodeId));
        publish();
    }

    public void continueToResume() {
        report(session.continueToResume());
        publish();
    }

    /** "Start" in the preview. */
    public void start() {
        session.start();
        publish();
    }

    /** Close the preview, end navigation, or "Done" after arriving. */
    public void stop() {
        stopDemoWalk();
        session.stop();
        publish();
    }

    /** One step felt by the phone: move one step length along the route (an estimate). */
    public void onStep() {
        if (demoWalking) {
            return;
        }
        report(session.walk(session.profile().stepLengthM()));
        publish();
    }

    /** "I did turn": closes the "Missed a turn?" hint (see {@link NavSession#missedTurn}). */
    public void dismissMissedTurn() {
        session.dismissMissedTurn();
        publish();
    }

    /** The traveller answered the notification / step-counting question: show the notification again. */
    public void onPermissionsChanged() {
        notification.refresh();
        publish();
    }

    /** The phone's direction changed (see {@link NavSession#onHeading}). */
    public void onHeading(double degrees) {
        if (demoWalking) {
            return;
        }
        NavSession.Event event = session.onHeading(degrees);
        if (event != NavSession.Event.NONE) {
            report(event);
            publish();
        }
    }

    // ---- Demo walk ----

    public boolean isDemoWalking() {
        return demoWalking;
    }

    public int demoSpeed() {
        return DEMO_SPEEDS[demoSpeedIndex];
    }

    /** Play / pause the demo walk (demo builds only; the screen labels it). */
    public void toggleDemoWalk() {
        if (demoWalking) {
            stopDemoWalk();
        } else if (session.mode() == NavSession.Mode.NAVIGATING) {
            demoWalking = true;
            lastTickAt = SystemClock.elapsedRealtime();
            handler.postDelayed(demoTick, TICK_MS);
        }
        publish();
    }

    /** 1x → 4x → 8x → 1x. */
    public void nextDemoSpeed() {
        demoSpeedIndex = (demoSpeedIndex + 1) % DEMO_SPEEDS.length;
        publish();
    }

    private void stopDemoWalk() {
        demoWalking = false;
        handler.removeCallbacks(demoTick);
    }

    // ---- Inside ----

    /** Tells the screen and the notification (which also works with the screen off). */
    private void publish() {
        nav.setValue(session);
        notification.update(session, demoWalking);
    }

    private void report(NavSession.Event event) {
        switch (event) {
            case NO_ROUTE:
                notice.setValue(new Notice(Notice.Kind.NO_ROUTE));
                break;
            case POSITION_CONFIRMED:
                notice.setValue(new Notice(Notice.Kind.POSITION_CONFIRMED));
                break;
            case REROUTED:
                notice.setValue(new Notice(Notice.Kind.REROUTED));
                break;
            case ROUTE_UPDATED:
                notice.setValue(new Notice(Notice.Kind.ROUTE_UPDATED));
                break;
            case TURN_CONFIRMED:
                notice.setValue(new Notice(Notice.Kind.TURN_CONFIRMED));
                break;
            case ARRIVED:
                stopDemoWalk();
                notice.setValue(new Notice(Notice.Kind.ARRIVED));
                break;
            case NONE:
            default:
                break;
        }
    }

    @Override
    protected void onCleared() {
        stopDemoWalk();
        notification.cancel();
        mapData.removeObserver(mapObserver);
        super.onCleared();
    }

    @Nullable
    private static MapJson.MapData parseOrNull(@Nullable AirportMapEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return MapJson.parse(entity.json);
        } catch (RuntimeException e) {
            Log.w("SafarSaathi", "The saved map could not be read", e);
            return null;
        }
    }

    public static ViewModelProvider.Factory factory(Context context) {
        Context app = context.getApplicationContext();
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (modelClass.isAssignableFrom(MapViewModel.class)) {
                    return (T) new MapViewModel(ServiceLocator.get().getAirportMapRepository(),
                            new NavNotification(app));
                }
                throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
            }
        };
    }
}
