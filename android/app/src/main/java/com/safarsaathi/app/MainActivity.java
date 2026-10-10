package com.safarsaathi.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.safarsaathi.app.databinding.ActivityMainBinding;

/**
 * The app's only activity: a top bar, the current tab's screen, and a bottom bar with five tabs
 * (Home, Navigate, Bags, Help, Profile). Jetpack Navigation swaps the tab screens (fragments) for us.
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    /**
     * Shows Android's "allow access to devices on your local network?" question (see askForLocalNetworkAccess).
     * Registered here, as a field, because Android only allows it before the screen starts.
     */
    private final ActivityResultLauncher<String> localNetworkQuestion =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), allowed -> {
                if (allowed) {
                    // Load the plan now, instead of waiting for the Trip tab's next 10-second refresh.
                    ServiceLocator.get().getTripRepository().refresh(AppConfig.DEMO_TRIP_ID, false);
                }
            });

    /**
     * Large text (Profile tab, {@link AppSettings}): runs before any view exists, so every screen, dialog and sheet
     * of this activity draws its text 30% bigger when the traveller switched it on.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.applyTo(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Draw behind the system bars on every Android version, with status-bar icons that stay readable
        // in light and dark mode. (Android 15+ already forces this for apps targeting API 35+.)
        EdgeToEdge.enable(this);

        // View binding: ActivityMainBinding is generated from activity_main.xml, one field per view id.
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySideInsets(binding);

        // Our MaterialToolbar becomes the top "action bar" (the theme has none of its own: NoActionBar).
        setSupportActionBar(binding.toolbar);

        // The NavController switches between the tab screens. We get it from the NavHostFragment
        // declared in activity_main.xml. (Navigation.findNavController(...) does not work this early
        // in onCreate, because the fragment's view does not exist yet.)
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        // The five tabs are "top-level" screens, so the toolbar shows no back arrow on them. Screens opened from a tab
        // (the Wallet, parked screens) get a back arrow.
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home,
                R.id.navigation_navigate,
                R.id.navigation_bags,
                R.id.navigation_help,
                R.id.navigation_profile)
                .build();

        // The toolbar title follows the current screen's android:label from nav_graph.xml.
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        // Tapping a tab opens the nav-graph destination with the SAME id as the menu item, and each tab
        // keeps its own back stack. (Never use a Java switch on R.id values: with AGP 9 they are not
        // compile-time constants. NavigationUI matches the ids for us; elsewhere use if/else.)
        NavigationUI.setupWithNavController(binding.bottomNav, navController);

        // Navigate tab: a full-screen map like Google Maps, with its own search bar, so no toolbar there. Leaving the
        // tab always brings the bottom tabs back (they are hidden while navigating, see setTabsVisible).
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean navigate = destination.getId() == R.id.navigation_navigate;
            binding.appBar.setVisibility(navigate ? View.GONE : View.VISIBLE);
            if (!navigate) {
                setTabsVisible(true);
            }
        });

        // Only on a fresh start: after a screen rotation the question (or its answer) is already there.
        if (savedInstanceState == null) {
            askForLocalNetworkAccess();
        }
    }

    /**
     * Android 17 (API 37) blocks an app's connections to the LOCAL network (for the demo: the laptop running
     * our backend, which the emulator reaches at 10.0.2.2) until the user allows it. Without this the Trip tab
     * keeps saying "could not be reached" although the backend is running. Only debug builds ask: only they
     * declare the permission (src/debug/AndroidManifest.xml), because only they talk to a laptop.
     * Tapped "Don't allow" by mistake? Settings, Apps, Safar Saathi, Permissions, Nearby devices.
     */
    private void askForLocalNetworkAccess() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
            return; // release build, or Android 16 and older: nothing to ask
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK)
                != PackageManager.PERMISSION_GRANTED) {
            localNetworkQuestion.launch(Manifest.permission.ACCESS_LOCAL_NETWORK);
        }
    }

    /**
     * Navigate tab, turn-by-turn: hides the bottom tabs so the map and the instructions get the whole screen (like
     * Google Maps), and shows them again afterwards.
     */
    public void setTabsVisible(boolean visible) {
        View tabs = findViewById(R.id.bottom_nav);
        if (tabs != null) {
            tabs.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** The toolbar's back arrow: go up one screen. (Never visible on the five tabs themselves.) */
    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    /**
     * Edge-to-edge, part 2 (part 1 is in activity_main.xml): keeps the toolbar and the tab screens clear
     * of system bars and camera cutouts on the LEFT and RIGHT, e.g. a phone held sideways.
     * The top (status bar) is handled by the AppBarLayout. The BottomNavigationView makes room for the
     * navigation bar below AND beside its tabs by itself, so it needs nothing from us.
     * We pad only the toolbar and the tab area, not the whole screen: padding moves a view's content but
     * not its background, so both bars stay coloured right up to the screen edges.
     */
    private static void applySideInsets(ActivityMainBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, insets) -> {
            Insets sides = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.toolbar.setPadding(sides.left, 0, sides.right, 0);
            binding.navHostFragment.setPadding(sides.left, 0, sides.right, 0);
            // Pass the insets on unchanged: the AppBarLayout and the BottomNavigationView still need them.
            return insets;
        });
    }
}
