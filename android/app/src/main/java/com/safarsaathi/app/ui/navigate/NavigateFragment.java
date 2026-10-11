package com.safarsaathi.app.ui.navigate;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.safarsaathi.app.AppSettings;
import com.safarsaathi.app.BuildConfig;
import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.airport.MapJson;
import com.safarsaathi.app.data.local.AirportMapEntity;
import com.safarsaathi.app.databinding.FragmentNavigateBinding;
import com.safarsaathi.app.databinding.ItemNavHelpBinding;
import com.safarsaathi.app.databinding.ItemNavStepBinding;
import com.safarsaathi.app.databinding.SheetNavListBinding;
import com.safarsaathi.app.ui.trip.TripTime;
import com.safarsaathi.routing.map.MapAnchor;
import com.safarsaathi.routing.map.MapFloor;
import com.safarsaathi.routing.map.MapNode;
import com.safarsaathi.routing.map.NodeType;
import com.safarsaathi.routing.map.Zone;
import com.safarsaathi.routing.route.InstructionText;
import com.safarsaathi.routing.route.Maneuver;
import com.safarsaathi.routing.route.Route;
import com.safarsaathi.routing.route.RoutePoint;
import com.safarsaathi.routing.route.RouteStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Navigate tab: Google-Maps-style walking directions inside Mumbai T2 (layout: res/layout/fragment_navigate.xml).
 *
 * <ol>
 *   <li><b>Explore:</b> the map with a search bar, floors, and a card for a tapped place ("Directions", "I'm here").</li>
 *   <li><b>Preview:</b> "Your place → To", the route in blue, time and distance (estimates), step-free / slower pace,
 *       "Start". When the app doesn't know where the traveller is, it asks: scan a QR "You are here" code or choose
 *       the place (a phone can't locate itself inside a terminal).</li>
 *   <li><b>Navigating:</b> the green banner with the next turn ("Turn left at Central hall", "Then ..."), the map
 *       following a blue arrow with the walking direction up, time / distance / arrival at the bottom, voice guidance,
 *       steps list, help nearby, "Missed a turn?", and a notification that keeps going with the screen off. The arrow
 *       moves with counted steps (estimate), QR codes, "I'm here", or the demo walk in demo builds.</li>
 *   <li><b>Arrived:</b> "You have arrived at Gate 32".</li>
 * </ol>
 * The map's own note ("Demo schematic, not an official airport map") is always on screen (CLAUDE.md honesty rule). The
 * route, its words and the times all come from routing-core, so they work offline.
 */
public class NavigateFragment extends Fragment {

    private FragmentNavigateBinding binding;
    private MapViewModel viewModel;

    @Nullable
    private MapJson.MapData data;
    @Nullable
    private AirportMapEntity savedMap;
    private SyncState syncState = SyncState.idle();
    @Nullable
    private String floorId;
    private final List<String> floorButtonIds = new ArrayList<>();
    private final List<Integer> floorButtonViewIds = new ArrayList<>();
    private boolean settingFloorButtons;
    @Nullable
    private MapNode placeOnCard;

    // What is on screen, to only redo work when it changes.
    @Nullable
    private NavSession.Mode shownMode;
    private int shownRouteVersion = -1;
    private boolean fitRouteOnLayout;
    @Nullable
    private String bannerKey;
    @Nullable
    private String spokenKey;
    private boolean spokeNear;
    @Nullable
    private String missedTurnKey;
    private int topInset;
    private int bottomInset;

    @Nullable
    private MotionSensors sensors;
    @Nullable
    private VoiceGuide voice;
    private boolean voiceMissingShown;

    /** Step counting ("physical activity") and, on Android 13+, the navigation notification. */
    private final ActivityResultLauncher<String[]> navPermissions =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), answers -> {
                updateSensors();
                viewModel.onPermissionsChanged();
                if (Boolean.FALSE.equals(answers.get(Manifest.permission.ACTIVITY_RECOGNITION))) {
                    showSnack(getString(R.string.nav_position_no_steps));
                }
            });

    private final OnBackPressedCallback back = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            // Back closes the place card, the preview, or ends navigation, before leaving the tab.
            if (viewModel.getNav().getValue() != null && viewModel.getNav().getValue().mode() != NavSession.Mode.EXPLORE) {
                viewModel.stop();
            } else {
                showPlaceCard(null);
            }
        }
    };

    /**
     * A place another tab asked for ("Show the way" on the Help tab), used once when this tab's map is loaded: the
     * route preview to it opens (which asks "Where are you now?" first, as always). Main thread only.
     */
    @Nullable
    private static String pendingDestination;

    /** Call before switching to the Navigate tab: opens directions to this map place (node id). */
    public static void requestDirections(String nodeId) {
        pendingDestination = nodeId;
    }

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public NavigateFragment() {
        super(R.layout.fragment_navigate);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentNavigateBinding.bind(view);
        viewModel = new ViewModelProvider(this, MapViewModel.factory(requireContext())).get(MapViewModel.class);
        floorButtonIds.clear();
        floorButtonViewIds.clear();
        shownMode = null;
        shownRouteVersion = -1;
        bannerKey = null;
        sensors = new MotionSensors(requireContext());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);

        applyInsets();
        wireButtons();
        binding.navigateMap.setListener(new TerminalMapView.Listener() {
            @Override
            public void onPlaceTapped(@Nullable MapNode node) {
                onMapPlaceTapped(node);
            }

            @Override
            public void onFollowStopped() {
                binding.navigateFollow.setVisibility(View.VISIBLE);
            }
        });
        // The cards over the map change size: tell the map, so "show the whole route" uses the free part.
        View.OnLayoutChangeListener overlays = (v, l, t, r, b, ol, ot, or, ob) -> updateOverlayInsets();
        binding.navigateTop.addOnLayoutChangeListener(overlays);
        binding.navigateBottom.addOnLayoutChangeListener(overlays);

        binding.navigateStepFree.setChecked(AppSettings.isStepFree(requireContext()));
        binding.navigateSlowPace.setChecked(AppSettings.isSlowPace(requireContext()));
        viewModel.setRouteOptions(binding.navigateStepFree.isChecked(), binding.navigateSlowPace.isChecked());

        viewModel.getSavedMap().observe(getViewLifecycleOwner(), entity -> {
            savedMap = entity;
            renderStatus();
        });
        viewModel.getMapData().observe(getViewLifecycleOwner(), newData -> {
            data = newData;
            renderMap();
            if (newData != null && pendingDestination != null && newData.map.node(pendingDestination) != null) {
                String destination = pendingDestination;
                pendingDestination = null;
                viewModel.setDestination(destination);
            }
        });
        viewModel.getSyncState().observe(getViewLifecycleOwner(), state -> {
            syncState = state;
            renderStatus();
        });
        viewModel.getNav().observe(getViewLifecycleOwner(), this::render);
        viewModel.getNotice().observe(getViewLifecycleOwner(), this::showNotice);
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.loadAndRefresh();
        updateSensors();
    }

    @Override
    public void onStop() {
        // While navigating, step counting goes on with the screen off or in another app (NavigationService keeps
        // Android sending the steps; the notification shows the walk). Otherwise the sensors are off already.
        NavSession session = viewModel.getNav().getValue();
        boolean navigating = session != null && session.mode() == NavSession.Mode.NAVIGATING;
        if (sensors != null && !navigating) {
            sensors.stop();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (voice != null) {
            voice.shutdown();
            voice = null;
        }
        if (sensors != null) {
            sensors.stop();
        }
        setTabsVisible(true);
        binding = null;
        super.onDestroyView();
    }

    // ---- Buttons ----

    private void wireButtons() {
        binding.navigateSearch.setOnClickListener(button -> search(R.string.nav_search_title, node -> {
            switchFloor(node.floor);
            binding.navigateMap.select(node.id, true);
            showPlaceCard(node);
        }));
        binding.navigateScan.setOnClickListener(button -> scanQr());
        binding.navigateRecenter.setOnClickListener(button -> showWhereIAm());
        binding.navigateDirections.setOnClickListener(button -> {
            if (placeOnCard != null) {
                viewModel.setDestination(placeOnCard.id);
            } else {
                search(R.string.nav_where_to, node -> viewModel.setDestination(node.id));
            }
        });
        binding.navigateFloors.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            int index = floorButtonViewIds.indexOf(checkedId);
            if (isChecked && index >= 0 && !settingFloorButtons) {
                showFloor(floorButtonIds.get(index), true);
            }
        });

        // Place card
        binding.navigatePlaceClose.setOnClickListener(button -> showPlaceCard(null));
        binding.navigatePlaceDirections.setOnClickListener(button -> {
            if (placeOnCard != null) {
                viewModel.setDestination(placeOnCard.id);
            }
        });
        binding.navigatePlaceHere.setOnClickListener(button -> {
            if (placeOnCard != null) {
                viewModel.setHere(placeOnCard.id);
                showPlaceCard(null);
            }
        });

        // Preview
        binding.navigatePreviewBack.setOnClickListener(button -> viewModel.stop());
        binding.navigateFrom.setOnClickListener(button -> askWhereAmI());
        binding.navigateTo.setOnClickListener(button -> search(R.string.nav_where_to,
                node -> viewModel.setDestination(node.id)));
        binding.navigateNeedScan.setOnClickListener(button -> scanQr());
        binding.navigateNeedChoose.setOnClickListener(button -> askWhereAmI());
        binding.navigateStepFree.setOnCheckedChangeListener((chip, checked) -> routeOptionsChanged());
        binding.navigateSlowPace.setOnCheckedChangeListener((chip, checked) -> routeOptionsChanged());
        binding.navigateStart.setOnClickListener(button -> viewModel.start()); // render() asks for permissions
        binding.navigatePreviewSteps.setOnClickListener(button -> showSteps());

        // Navigating
        binding.navigateExit.setOnClickListener(button -> viewModel.stop());
        binding.navigateSteps.setOnClickListener(button -> showSteps());
        binding.navigateHelp.setOnClickListener(button -> showHelpNearby());
        binding.navigateFollow.setOnClickListener(button -> {
            binding.navigateFollow.setVisibility(View.GONE);
            binding.navigateMap.follow();
        });
        binding.navigateVoice.setOnClickListener(button -> {
            boolean on = !AppSettings.isVoiceGuidance(requireContext());
            AppSettings.setVoiceGuidance(requireContext(), on);
            if (!on && voice != null) {
                voice.silence();
            }
            renderVoiceButton();
        });
        binding.navigateDemoWalk.setOnClickListener(button -> viewModel.toggleDemoWalk());
        binding.navigateDemoSpeed.setOnClickListener(button -> viewModel.nextDemoSpeed());
        binding.navigateMissedTurnScan.setOnClickListener(button -> scanQr());
        binding.navigateMissedTurnHere.setOnClickListener(button -> askWhereAmI());
        binding.navigateMissedTurnClose.setOnClickListener(button -> viewModel.dismissMissedTurn());

        // Arrived
        binding.navigateArrivedDone.setOnClickListener(button -> viewModel.stop());
        binding.navigateArrivedContinue.setOnClickListener(button -> viewModel.continueToResume());
    }

    private void routeOptionsChanged() {
        boolean stepFree = binding.navigateStepFree.isChecked();
        boolean slow = binding.navigateSlowPace.isChecked();
        AppSettings.setStepFree(requireContext(), stepFree);
        AppSettings.setSlowPace(requireContext(), slow);
        viewModel.setRouteOptions(stepFree, slow);
    }

    // ---- Rendering ----

    private void render(NavSession session) {
        if (binding == null || data == null || session.map() == null) {
            return;
        }
        NavSession.Mode mode = session.mode();
        boolean modeChanged = mode != shownMode;
        boolean routeChanged = session.routeVersion() != shownRouteVersion;
        boolean explore = mode == NavSession.Mode.EXPLORE;
        boolean preview = mode == NavSession.Mode.PREVIEW;
        boolean navigating = mode == NavSession.Mode.NAVIGATING;
        boolean arrived = mode == NavSession.Mode.ARRIVED;

        if (modeChanged) {
            binding.navigateSearchBar.setVisibility(explore ? View.VISIBLE : View.GONE);
            binding.navigatePreviewTop.setVisibility(preview ? View.VISIBLE : View.GONE);
            binding.navigateBanner.setVisibility(navigating ? View.VISIBLE : View.GONE);
            if (!navigating) {
                binding.navigateMissedTurn.setVisibility(View.GONE);
            }
            binding.navigateFloorCard.setVisibility(navigating ? View.GONE : View.VISIBLE);
            binding.navigateVoice.setVisibility(navigating ? View.VISIBLE : View.GONE);
            binding.navigateRecenter.setVisibility(explore ? View.VISIBLE : View.GONE);
            binding.navigateDirections.setVisibility(explore ? View.VISIBLE : View.GONE);
            binding.navigateDemoControls.setVisibility(navigating && BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
            binding.navigatePreviewSheet.setVisibility(preview ? View.VISIBLE : View.GONE);
            binding.navigateNavSheet.setVisibility(navigating ? View.VISIBLE : View.GONE);
            binding.navigateArrivedCard.setVisibility(arrived ? View.VISIBLE : View.GONE);
            binding.navigateFollow.setVisibility(View.GONE);
            renderStatus(); // hidden while navigating: only the next instruction and the honesty label up there
            if (!explore) {
                showPlaceCard(null);
            }
            setTabsVisible(!(navigating || arrived));
            applyBottomInset();
            renderVoiceButton();
            back.setEnabled(!explore || placeOnCard != null);
            if (navigating) {
                askForNavPermissions();
            }
        }

        TerminalMapView map = binding.navigateMap;
        map.setRoute(explore ? null : session.route(), session.progress());
        map.setNavigating(navigating);
        map.setDestination(explore ? null : session.destination());
        map.setHere(navigating ? null : session.here(), session.positionIsEstimate());

        if (navigating) {
            RoutePoint point = session.position();
            if (point != null && !point.floor.equals(floorId)) {
                showFloor(point.floor, false);
            }
            renderBanner(session);
            renderMissedTurn(session);
            renderNavSheet(session);
            announce(session);
        } else if (preview) {
            renderPreview(session);
            if (routeChanged || modeChanged) {
                Route route = session.route();
                MapNode start = route != null ? route.start() : session.here() != null ? session.here()
                        : session.destination();
                if (start != null) {
                    showFloor(start.floor, false);
                    checkFloorButton(start.floor);
                }
                fitRouteOnLayout = true;
                binding.navigateMap.post(this::fitRouteIfPreviewing);
            }
        } else if (arrived && modeChanged) {
            renderArrived(session);
            MapNode destination = session.destination();
            if (destination != null) {
                showFloor(destination.floor, false);
                checkFloorButton(destination.floor);
                map.showPlace(destination);
            }
        } else if (explore && modeChanged && shownMode != null) {
            map.fitFloor();
        }
        if (modeChanged || routeChanged) {
            updateSensors();
        }
        shownMode = mode;
        shownRouteVersion = session.routeVersion();
    }

    private void renderPreview(NavSession session) {
        MapNode here = session.here();
        MapNode destination = session.destination();
        binding.navigateFrom.setText(here == null ? getString(R.string.nav_from_unknown)
                : getString(R.string.nav_from, here.name(language())));
        binding.navigateTo.setText(destination == null ? "" : getString(R.string.nav_to, destination.name(language())));
        Route route = session.route();
        boolean needStart = here == null;
        binding.navigateNeedStart.setVisibility(needStart ? View.VISIBLE : View.GONE);
        binding.navigatePreviewRoute.setVisibility(needStart ? View.GONE : View.VISIBLE);
        binding.navigatePreviewButtons.setVisibility(needStart ? View.GONE : View.VISIBLE);
        binding.navigateStart.setEnabled(route != null);
        binding.navigatePreviewSteps.setEnabled(route != null);
        if (needStart) {
            return;
        }
        if (route == null) {
            binding.navigatePreviewTime.setText("—");
            binding.navigatePreviewDistance.setText("");
            binding.navigatePreviewDetail.setText(R.string.nav_notice_no_route);
            binding.navigatePreviewNote.setText("");
            return;
        }
        binding.navigatePreviewTime.setText(minutes(route.totalSeconds));
        binding.navigatePreviewDistance.setText(getString(R.string.nav_in_brackets, distance(route.totalMetres)));
        MapNode checkpoint = null;
        for (MapNode node : route.nodes) {
            if (node.type == NodeType.SECURITY || node.type == NodeType.IMMIGRATION) {
                checkpoint = node;
                break;
            }
        }
        binding.navigatePreviewDetail.setText(checkpoint != null && route.passesCheckpoint()
                ? getString(R.string.nav_route_via, checkpoint.name(language()))
                : getString(R.string.nav_route_plain));
        binding.navigatePreviewNote.setText(route.passesCheckpoint() ? R.string.nav_route_note_security
                : R.string.nav_route_note);
    }

    private void renderBanner(NavSession session) {
        RouteStep step = session.bannerStep();
        if (step == null) {
            return;
        }
        String key = session.routeVersion() + ":" + session.route().steps.indexOf(step);
        if (!key.equals(bannerKey)) {
            bannerKey = key;
            binding.navigateBannerIcon.setImageDrawable(new ManeuverDrawable(step.maneuver, Color.WHITE));
            binding.navigateBannerText.setText(instruction(step));
            RouteStep then = session.thenStep();
            binding.navigateThen.setVisibility(then == null ? View.GONE : View.VISIBLE);
            if (then != null) {
                binding.navigateThenIcon.setImageDrawable(new ManeuverDrawable(then.maneuver, Color.WHITE));
                binding.navigateThenIcon.setContentDescription(instruction(then));
            }
            binding.navigateBanner.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            binding.navigateMap.setContentDescription(getString(R.string.nav_map_description, instruction(step)));
        }
        boolean riding = step.maneuver.changesFloor();
        setIfChanged(binding.navigateBannerDistance, riding ? "" : distance(session.bannerDistance()));
        binding.navigateBannerDistance.setVisibility(riding ? View.GONE : View.VISIBLE);
    }

    /**
     * "Missed a turn? Scan a QR code or tap I'm here": the counted steps went more than 20 m past a turn that the
     * phone did not feel (see {@link NavSession#missedTurn}). Said out loud once per turn.
     */
    private void renderMissedTurn(NavSession session) {
        RouteStep missed = session.missedTurn();
        binding.navigateMissedTurn.setVisibility(missed == null ? View.GONE : View.VISIBLE);
        if (missed == null) {
            return;
        }
        String key = session.routeVersion() + ":" + session.route().steps.indexOf(missed);
        if (!key.equals(missedTurnKey)) {
            missedTurnKey = key;
            binding.navigateMissedTurnDetail.setText(getString(R.string.nav_missed_turn_detail, instruction(missed)));
            binding.navigateMissedTurn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            speak(getString(R.string.nav_missed_turn));
        }
    }

    private void renderNavSheet(NavSession session) {
        setIfChanged(binding.navigateEtaTime, minutes(session.remainingSeconds()));
        Instant arrival = Instant.now().plusSeconds(Math.round(session.remainingSeconds()));
        setIfChanged(binding.navigateEtaDetail, getString(R.string.nav_eta_detail, distance(session.remainingMetres()),
                TripTime.time(arrival)));
        String note;
        MapNode from = session.countedFrom();
        String fromName = from == null ? "" : from.name(language());
        if (viewModel.isDemoWalking() || (session.positionIsEstimate() && !canCountSteps() && from != null
                && session.progress() > 0.5)) {
            note = getString(viewModel.isDemoWalking() ? R.string.nav_position_demo : R.string.nav_position_no_steps);
        } else if (session.positionIsEstimate()) {
            note = getString(R.string.nav_position_counted, fromName);
        } else {
            note = getString(session.hereSource() == NavSession.HereSource.QR ? R.string.nav_position_scanned
                    : R.string.nav_position_chosen, fromName);
        }
        setIfChanged(binding.navigatePositionNote, note);
        // Demo walk controls (demo builds only).
        boolean walking = viewModel.isDemoWalking();
        binding.navigateDemoWalk.setText(walking ? R.string.nav_demo_pause : R.string.nav_demo_walk);
        binding.navigateDemoWalk.setChipIconResource(walking ? R.drawable.ic_pause_24 : R.drawable.ic_play_24);
        setIfChanged(binding.navigateDemoSpeed, getString(R.string.nav_demo_speed, viewModel.demoSpeed()));
    }

    private void renderArrived(NavSession session) {
        MapNode destination = session.destination();
        if (destination == null) {
            return;
        }
        binding.navigateArrivedTitle.setText(words().arrived(destination));
        binding.navigateArrivedDetail.setText(placeDetail(destination));
        MapNode resume = session.resumeDestination();
        binding.navigateArrivedContinue.setVisibility(resume == null ? View.GONE : View.VISIBLE);
        if (resume != null) {
            binding.navigateArrivedContinue.setText(getString(R.string.nav_continue_to, resume.name(language())));
        }
    }

    /** Speaks a new instruction once ("In 40 m, turn left at ..."), and once more just before the turn. */
    private void announce(NavSession session) {
        RouteStep step = session.bannerStep();
        if (step == null) {
            return;
        }
        String key = session.routeVersion() + ":" + session.route().steps.indexOf(step);
        double distance = session.bannerDistance();
        String text = instruction(step);
        if (!key.equals(spokenKey)) {
            spokenKey = key;
            boolean far = distance >= 15 && !step.maneuver.changesFloor() && step.maneuver != Maneuver.DEPART;
            speak(far ? getString(R.string.nav_voice_in, distance(distance), text) : text);
            spokeNear = !far;
        } else if (!spokeNear && distance <= 8) {
            spokeNear = true;
            speak(text);
        }
    }

    // ---- Map and floors ----

    private void renderMap() {
        if (binding == null) {
            return;
        }
        boolean hasMap = data != null;
        binding.navigateEmpty.setVisibility(hasMap ? View.GONE : View.VISIBLE);
        binding.navigateSearch.setEnabled(hasMap);
        binding.navigateDirections.setEnabled(hasMap);
        if (!hasMap) {
            binding.navigateDemoLabel.setText(R.string.map_loading);
            return;
        }
        // The honesty label comes from the map itself, e.g. "BOM T2 · Demo schematic, not an official airport map".
        binding.navigateDemoLabel.setText(getString(R.string.map_title, data.map.airport, data.map.terminal,
                data.map.note == null ? "" : data.map.note));
        buildFloorButtons();
        String floor = floorId != null && data.map.floor(floorId) != null ? floorId : topFloorId();
        checkFloorButton(floor);
        showFloor(floor, true);
        if (viewModel.getNav().getValue() != null) {
            render(viewModel.getNav().getValue());
        }
    }

    /** One button per floor, top floor first (e.g. Departures, Arrivals). Rebuilt only when the floors change. */
    private void buildFloorButtons() {
        List<MapFloor> floors = new ArrayList<>(data.map.floors);
        Collections.sort(floors, (a, b) -> Integer.compare(b.order, a.order));
        List<String> ids = new ArrayList<>();
        for (MapFloor floor : floors) {
            ids.add(floor.id);
        }
        if (ids.equals(floorButtonIds)) {
            return;
        }
        binding.navigateFloors.removeAllViews();
        floorButtonIds.clear();
        floorButtonViewIds.clear();
        for (MapFloor floor : floors) {
            MaterialButton button = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            int viewId = View.generateViewId();
            button.setId(viewId);
            button.setText(floor.name(language()));
            button.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
            button.setInsetTop(0);
            button.setInsetBottom(0);
            binding.navigateFloors.addView(button, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            floorButtonIds.add(floor.id);
            floorButtonViewIds.add(viewId);
        }
    }

    private void checkFloorButton(String floor) {
        int index = floorButtonIds.indexOf(floor);
        if (index >= 0 && binding.navigateFloors.getCheckedButtonId() != floorButtonViewIds.get(index)) {
            settingFloorButtons = true;
            binding.navigateFloors.check(floorButtonViewIds.get(index));
            settingFloorButtons = false;
        }
    }

    /** Shows a floor; {@code fit}: the whole floor (exploring), else the camera stays (route, following). */
    private void showFloor(String floor, boolean fit) {
        if (binding == null || data == null) {
            return;
        }
        boolean changed = !floor.equals(floorId);
        floorId = floor;
        binding.navigateMap.setFloor(data, floor, language(), fit && changed || fit && shownMode == null);
        MapFloor mapFloor = data.map.floor(floor);
        binding.navigateMap.setContentDescription(getString(R.string.map_description,
                mapFloor == null ? floor : mapFloor.name(language()), data.map.nodesOnFloor(floor).size()));
        if (changed && placeOnCard != null && !floor.equals(placeOnCard.floor)) {
            showPlaceCard(null);
        }
    }

    private void switchFloor(String floor) {
        checkFloorButton(floor);
        showFloor(floor, false);
    }

    private String topFloorId() {
        MapFloor top = null;
        for (MapFloor floor : data.map.floors) {
            if (top == null || floor.order > top.order) {
                top = floor;
            }
        }
        return top == null ? "" : top.id;
    }

    private void fitRouteIfPreviewing() {
        NavSession session = viewModel.getNav().getValue();
        if (binding != null && session != null && session.mode() == NavSession.Mode.PREVIEW) {
            updateOverlayInsets();
            if (session.route() != null) {
                binding.navigateMap.fitRoute();
            } else if (session.destination() != null) {
                binding.navigateMap.showPlace(session.destination());
            }
        }
    }

    private void updateOverlayInsets() {
        if (binding == null) {
            return;
        }
        float top = binding.navigateTop.getBottom();
        float bottom = binding.navigateBottom.getHeight() == 0 ? 0
                : binding.navigateMap.getHeight() - binding.navigateBottom.getTop();
        binding.navigateMap.setOverlayInsets(top, Math.max(0, bottom));
        if (fitRouteOnLayout) {
            fitRouteOnLayout = false;
            fitRouteIfPreviewing();
        }
    }

    // ---- Places ----

    private void onMapPlaceTapped(@Nullable MapNode node) {
        NavSession session = viewModel.getNav().getValue();
        NavSession.Mode mode = session == null ? NavSession.Mode.EXPLORE : session.mode();
        if (mode == NavSession.Mode.EXPLORE || mode == NavSession.Mode.ARRIVED) {
            if (mode == NavSession.Mode.ARRIVED && node != null) {
                viewModel.stop();
            }
            showPlaceCard(node);
            return;
        }
        if (node == null) {
            return;
        }
        // While planning or walking: "Are you at Security check now?" → the route continues from there.
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.nav_here_question, node.name(language())))
                .setMessage(R.string.nav_here_question_detail)
                .setPositiveButton(R.string.nav_im_here, (dialog, which) -> viewModel.setHere(node.id))
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialog -> binding.navigateMap.select(null, false))
                .show();
    }

    private void showPlaceCard(@Nullable MapNode node) {
        if (binding == null) {
            return;
        }
        placeOnCard = node;
        NavSession session = viewModel.getNav().getValue();
        boolean explore = session == null || session.mode() == NavSession.Mode.EXPLORE;
        back.setEnabled(!explore || node != null);
        binding.navigatePlaceCard.setVisibility(node == null ? View.GONE : View.VISIBLE);
        if (node == null) {
            binding.navigateMap.select(null, false);
            return;
        }
        binding.navigatePlaceName.setText(node.name(language()));
        binding.navigatePlaceDetail.setText(placeDetail(node));
        String hint = node.hint(language());
        binding.navigatePlaceHint.setVisibility(hint == null ? View.GONE : View.VISIBLE);
        binding.navigatePlaceHint.setText(hint == null ? "" : hint);
        binding.navigatePlaceIcon.setImageResource(MapStyle.icon(node.type));
        binding.navigatePlaceIcon.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), MapStyle.color(node.type))));
    }

    /** "Gate · Departures · after security". */
    private String placeDetail(MapNode node) {
        MapFloor floor = data == null ? null : data.map.floor(node.floor);
        return getString(R.string.map_place_detail, getString(typeLabel(node)),
                floor == null ? node.floor : floor.name(language()),
                getString(node.zone == Zone.AIRSIDE ? R.string.map_after_security : R.string.map_before_security));
    }

    private void search(@StringRes int title, PlaceSearch.OnPicked onPicked) {
        if (data != null) {
            PlaceSearch.show(this, data.map, language(), floorId == null ? "" : floorId, title, onPicked);
        }
    }

    /** "Show where I am": the blue dot when the app knows the place, else a hint how to tell it. */
    private void showWhereIAm() {
        NavSession session = viewModel.getNav().getValue();
        MapNode here = session == null ? null : session.here();
        if (here == null) {
            binding.navigateMap.fitFloor();
            showSnack(getString(R.string.nav_notice_here_unknown));
            return;
        }
        switchFloor(here.floor);
        binding.navigateMap.showPlace(here);
    }

    /** "Where are you now?": scan a QR code, a QR-code place, another place, or Google Maps to get to T2. */
    private void askWhereAmI() {
        if (data == null) {
            return;
        }
        List<MapNode> spots = new ArrayList<>();
        for (MapAnchor anchor : data.map.anchors) {
            MapNode node = data.map.node(anchor.node);
            if (node != null && !spots.contains(node)) {
                spots.add(node);
            }
        }
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.nav_scan_qr));
        for (MapNode node : spots) {
            items.add(node.name(language()));
        }
        items.add(getString(R.string.nav_another_place));
        items.add(getString(R.string.nav_not_at_airport));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.nav_where_are_you)
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        scanQr();
                    } else if (which <= spots.size()) {
                        viewModel.setHere(spots.get(which - 1).id);
                    } else if (which == spots.size() + 1) {
                        search(R.string.nav_where_are_you, node -> viewModel.setHere(node.id));
                    } else if (!GoogleMapsLink.openDirectionsToTerminal(requireContext())) {
                        showSnack(getString(R.string.nav_notice_maps_missing));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Google's QR scanner screen (Google Play services; the app needs no camera permission). */
    private void scanQr() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(requireContext(), options);
        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    if (isAdded()) {
                        viewModel.onQrScanned(barcode.getRawValue());
                    }
                })
                .addOnFailureListener(error -> {
                    if (isAdded()) {
                        showSnack(getString(R.string.nav_notice_scanner_failed));
                    }
                });
    }

    // ---- Sheets ----

    /** All instructions of the route, the current one highlighted. */
    private void showSteps() {
        NavSession session = viewModel.getNav().getValue();
        Route route = session == null ? null : session.route();
        if (route == null) {
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetNavListBinding list = SheetNavListBinding.inflate(getLayoutInflater());
        list.sheetTitle.setText(getString(R.string.nav_steps_title, minutes(route.totalSeconds),
                getString(R.string.nav_in_brackets, distance(route.totalMetres))));
        int current = session.mode() == NavSession.Mode.NAVIGATING ? route.stepIndexAt(session.progress()) : -1;
        int textColor = ContextCompat.getColor(requireContext(), R.color.md_theme_onSurface);
        for (int i = 0; i < route.steps.size(); i++) {
            RouteStep step = route.steps.get(i);
            ItemNavStepBinding row = ItemNavStepBinding.inflate(getLayoutInflater(), list.sheetRows, false);
            row.stepIcon.setImageDrawable(new ManeuverDrawable(step.maneuver,
                    i == current ? ContextCompat.getColor(requireContext(), R.color.nav_eta) : textColor));
            row.stepText.setText(instruction(step));
            row.stepDistance.setText(step.distanceM > 0.5 ? distance(step.distanceM) : "");
            if (i < current) {
                row.getRoot().setAlpha(0.5f); // done
            }
            list.sheetRows.addView(row.getRoot());
        }
        list.sheetFooter.setVisibility(View.VISIBLE);
        list.sheetFooter.setText(route.passesCheckpoint() ? R.string.nav_route_note_security : R.string.nav_route_note);
        sheet.setContentView(list.getRoot());
        sheet.show();
    }

    /** The nearest help desk, washrooms, lift and buggy stop from where the traveller is, each with "Go". */
    private void showHelpNearby() {
        NavSession session = viewModel.getNav().getValue();
        if (session == null || session.currentPlace() == null) {
            showSnack(getString(R.string.nav_notice_here_unknown));
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetNavListBinding list = SheetNavListBinding.inflate(getLayoutInflater());
        list.sheetTitle.setText(R.string.nav_help_nearby);
        NodeType[] kinds = {NodeType.HELP_DESK, NodeType.WASHROOM, NodeType.LIFT, NodeType.BUGGY_STOP};
        for (NodeType kind : kinds) {
            MapNode nearest = session.nearest(kind);
            Route route = nearest == null ? null : session.routeFromHere(nearest);
            ItemNavHelpBinding row = ItemNavHelpBinding.inflate(getLayoutInflater(), list.sheetRows, false);
            row.helpIcon.setImageResource(MapStyle.icon(kind));
            row.helpIcon.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), MapStyle.color(kind))));
            if (nearest == null || route == null) {
                row.helpName.setText(typeLabelFor(kind));
                row.helpDetail.setText(R.string.nav_help_none);
                row.helpGo.setEnabled(false);
            } else {
                row.helpName.setText(nearest.name(language()));
                row.helpDetail.setText(getString(R.string.nav_time_distance, minutes(route.totalSeconds),
                        distance(route.totalMetres)));
                row.helpGo.setOnClickListener(button -> {
                    sheet.dismiss();
                    viewModel.detourTo(nearest.id);
                });
            }
            list.sheetRows.addView(row.getRoot());
        }
        list.sheetFooter.setVisibility(View.VISIBLE);
        list.sheetFooter.setText(R.string.nav_help_footer);
        sheet.setContentView(list.getRoot());
        sheet.show();
    }

    // ---- Notices, voice, sensors ----

    private void showNotice(@Nullable MapViewModel.Notice notice) {
        if (notice == null || binding == null || !notice.take()) {
            return;
        }
        NavSession session = viewModel.getNav().getValue();
        MapNode here = session == null ? null : session.here();
        String place = here == null ? "" : here.name(language());
        String text;
        switch (notice.kind) {
            case NO_ROUTE:
                text = getString(R.string.nav_notice_no_route);
                break;
            case POSITION_CONFIRMED:
                text = getString(R.string.nav_notice_position, place);
                break;
            case REROUTED:
                text = getString(R.string.nav_notice_rerouted, place);
                speak(text);
                break;
            case ROUTE_UPDATED:
                text = getString(R.string.nav_notice_updated);
                break;
            case TURN_CONFIRMED:
                MapNode at = session == null || session.route() == null ? null
                        : session.route().steps.get(session.route().stepIndexAt(session.progress())).at;
                text = getString(R.string.nav_notice_turn, at == null ? place : at.name(language()));
                break;
            case ARRIVED:
                MapNode destination = session == null ? null : session.destination();
                if (destination != null) {
                    speak(words().arrived(destination));
                }
                return; // the arrival card says it
            case QR_UNKNOWN:
            default:
                text = getString(R.string.nav_notice_qr_unknown);
                break;
        }
        showSnack(text);
    }

    private void speak(String text) {
        if (!AppSettings.isVoiceGuidance(requireContext())) {
            return;
        }
        if (voice == null) {
            voice = new VoiceGuide(requireContext(), language(), locale -> {
                if (!voiceMissingShown && isAdded()) {
                    voiceMissingShown = true;
                    requireActivity().runOnUiThread(() -> showSnack(getString(R.string.nav_notice_voice_missing)));
                }
            });
        }
        voice.speak(text);
    }

    private void renderVoiceButton() {
        boolean on = AppSettings.isVoiceGuidance(requireContext());
        binding.navigateVoice.setImageResource(on ? R.drawable.ic_volume_up_24 : R.drawable.ic_volume_off_24);
        binding.navigateVoice.setContentDescription(getString(on ? R.string.nav_voice_on : R.string.nav_voice_off));
    }

    /** While navigating: count steps (if allowed) and watch for turns. Otherwise the sensors are off. */
    private void updateSensors() {
        if (sensors == null || binding == null) {
            return;
        }
        NavSession session = viewModel.getNav().getValue();
        boolean navigating = session != null && session.mode() == NavSession.Mode.NAVIGATING && isStarted();
        if (!navigating) {
            sensors.stop();
            return;
        }
        sensors.start(new MotionSensors.Listener() {
            @Override
            public void onStep() {
                viewModel.onStep();
            }

            @Override
            public void onHeading(double degrees) {
                viewModel.onHeading(degrees);
            }
        }, canCountSteps());
    }

    private boolean isStarted() {
        return getView() != null && getViewLifecycleOwner().getLifecycle().getCurrentState()
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);
    }

    private boolean canCountSteps() {
        return sensors != null && sensors.canCountSteps() && hasActivityPermission();
    }

    private boolean hasActivityPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Asks once per walk for what is still missing: the "physical activity" permission (only when the phone can count
     * steps at all) and, on Android 13+, notifications (the walk's notification).
     */
    private void askForNavPermissions() {
        List<String> missing = new ArrayList<>();
        if (sensors != null && sensors.canCountSteps() && !hasActivityPermission()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            navPermissions.launch(missing.toArray(new String[0]));
        }
    }

    // ---- Insets and tabs ----

    /** The toolbar is hidden on this tab: keep the cards clear of the status bar (and the gesture bar). */
    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.navigateRoot, (root, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topInset = bars.top;
            bottomInset = bars.bottom;
            int eight = Math.round(8 * getResources().getDisplayMetrics().density);
            binding.navigateTop.setPadding(binding.navigateTop.getPaddingLeft(), topInset + eight,
                    binding.navigateTop.getPaddingRight(), 0);
            applyBottomInset();
            return insets;
        });
    }

    /** With the tabs hidden (navigating), the bottom cards reach the screen edge: pad them above the gesture bar. */
    private void applyBottomInset() {
        NavSession session = viewModel.getNav().getValue();
        boolean tabsHidden = session != null && (session.mode() == NavSession.Mode.NAVIGATING
                || session.mode() == NavSession.Mode.ARRIVED);
        int extra = tabsHidden ? bottomInset : 0;
        binding.navigateNavSheet.setContentPadding(0, 0, 0, extra);
        binding.navigateArrivedCard.setContentPadding(0, 0, 0, extra);
    }

    private void setTabsVisible(boolean visible) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTabsVisible(visible);
        }
    }

    // ---- Status ----

    /** "Map v1 · inside the app" / "Map v1 · from the server, checked 01:40" / "Offline · map v1 saved on this phone". */
    private void renderStatus() {
        if (binding == null) {
            return;
        }
        NavSession session = viewModel.getNav().getValue();
        boolean navigating = session != null && session.mode() == NavSession.Mode.NAVIGATING;
        if (savedMap == null || navigating) {
            binding.navigateStatus.setVisibility(View.GONE);
            return;
        }
        binding.navigateStatus.setVisibility(View.VISIBLE);
        boolean offline = syncState.kind == SyncState.Kind.OFFLINE
                && syncState.problem == SyncState.Problem.NO_CONNECTION;
        String text;
        if (syncState.kind == SyncState.Kind.REFRESHING) {
            text = getString(R.string.map_status_checking, savedMap.version);
        } else if (offline) {
            text = getString(R.string.map_status_offline, savedMap.version);
        } else if (AirportMapEntity.ORIGIN_SERVER.equals(savedMap.origin)) {
            text = getString(R.string.map_status_server, savedMap.version,
                    TripTime.time(savedMap.checkedAt != null ? savedMap.checkedAt : savedMap.savedAt));
        } else {
            text = getString(R.string.map_status_bundled, savedMap.version);
        }
        binding.navigateStatus.setText(text);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(binding.navigateStatus,
                offline ? R.drawable.ic_offline_24 : 0, 0, 0, 0);
    }

    // ---- Helpers ----

    private void showSnack(String text) {
        if (binding == null) {
            return;
        }
        Snackbar snackbar = Snackbar.make(binding.navigateRoot, text, Snackbar.LENGTH_LONG);
        snackbar.setAnchorView(binding.navigateBottom.getHeight() > 0 ? binding.navigateBottom : null);
        snackbar.show();
    }

    private InstructionText words() {
        return InstructionText.forLanguage(language());
    }

    private String instruction(RouteStep step) {
        return words().text(step, data.map);
    }

    /** "40 m" (rounded like Google Maps). */
    private String distance(double metres) {
        return NavText.distance(getResources(), metres);
    }

    /** "4 min" (at least 1). */
    private String minutes(double seconds) {
        return NavText.minutes(getResources(), seconds);
    }

    private static void setIfChanged(android.widget.TextView view, CharSequence text) {
        if (!text.toString().contentEquals(view.getText())) {
            view.setText(text);
        }
    }

    /** The app's language code ("en", "hi", "mr"); place names and instructions fall back to English. */
    private String language() {
        return NavText.language(getResources());
    }

    @StringRes
    private static int typeLabelFor(NodeType type) {
        MapNode probe = new MapNode();
        probe.type = type;
        return typeLabel(probe);
    }

    @StringRes
    static int typeLabel(MapNode node) {
        switch (node.type) {
            case ENTRY: return R.string.map_type_entry;
            case CHECKIN_ROW: return R.string.map_type_checkin;
            case BAG_DROP: return R.string.map_type_bag_drop;
            case SECURITY: return R.string.map_type_security;
            case IMMIGRATION: return R.string.map_type_immigration;
            case PIER_JUNCTION: return R.string.map_type_pier;
            case GATE: return R.string.map_type_gate;
            case LIFT: return R.string.map_type_lift;
            case ESCALATOR: return R.string.map_type_escalator;
            case STAIRS: return R.string.map_type_stairs;
            case TRAVELATOR: return R.string.map_type_travelator;
            case WASHROOM: return R.string.map_type_washroom;
            case HELP_DESK: return R.string.map_type_help_desk;
            case BUGGY_STOP: return R.string.map_type_buggy;
            case BAGGAGE_BELT: return R.string.map_type_belt;
            case BAGGAGE_SERVICES: return R.string.map_type_baggage_services;
            case LOST_FOUND: return R.string.map_type_lost_found;
            case LANDMARK:
            default:
                return R.string.map_type_landmark;
        }
    }
}
