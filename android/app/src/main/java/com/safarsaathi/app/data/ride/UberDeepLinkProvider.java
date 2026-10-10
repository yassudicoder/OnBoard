package com.safarsaathi.app.data.ride;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * The real provider: opens an Uber universal link ({@link UberDeepLink}) with an ordinary "view this link" Intent.
 * No API, no key, no partner approval: Uber's app (or Uber's website, when the app is missing) takes over.
 *
 * <p>Order of tries:
 * <ol>
 *   <li>ACTION_VIEW on the https link. Android gives it to the Uber app when it is installed (Uber claims its
 *       m.uber.com links), otherwise to the browser, which shows Uber's mobile website.</li>
 *   <li>If nothing took it (ActivityNotFoundException), the same link again, forced into a browser app.</li>
 *   <li>If that also fails: {@link Opened#NOTHING}, and the screen shows a friendly message.</li>
 * </ol>
 * AndroidManifest.xml declares a {@code <queries>} entry for the Uber app and for browsers: since Android 11 an app
 * cannot see other apps unless it says which ones it looks for.
 */
public final class UberDeepLinkProvider implements RideProvider {

    /** The Uber rider app's package name (its Google Play id). */
    public static final String UBER_PACKAGE = "com.ubercab";

    private static final String TAG = "RideProvider";

    @Nullable
    private final String clientId;

    /** @param clientId UBER_CLIENT_ID from local.properties; blank = links without client_id (logged once). */
    public UberDeepLinkProvider(@Nullable String clientId) {
        boolean missing = clientId == null || clientId.trim().isEmpty();
        this.clientId = missing ? null : clientId.trim();
        if (missing) {
            Log.w(TAG, "UBER_CLIENT_ID is not set in android/local.properties: Uber links are built without "
                    + "client_id (they still open Uber; Uber just cannot credit the sign-up to our app)");
        }
    }

    @Override
    public String name() {
        return "Uber";
    }

    @Override
    public boolean isMock() {
        return false;
    }

    public boolean hasClientId() {
        return clientId != null;
    }

    @Override
    public String link(RideRequest request) {
        return UberDeepLink.build(clientId, request.pickup, request.dropoff);
    }

    @Override
    public Opened open(Context context, RideRequest request) {
        String link = link(request);
        PackageManager packageManager = context.getPackageManager();
        try {
            context.startActivity(viewIntent(link));
            return isUberInstalled(packageManager) ? Opened.APP : Opened.BROWSER;
        } catch (ActivityNotFoundException noHandler) {
            Log.w(TAG, "No app took the Uber link; trying a browser");
        }
        try {
            context.startActivity(browserIntent(link));
            return Opened.BROWSER;
        } catch (ActivityNotFoundException noBrowser) {
            Log.w(TAG, "No browser on this phone either");
            return Opened.NOTHING;
        }
    }

    /** "Open this link": Android picks the Uber app if it is installed, else the browser. */
    public static Intent viewIntent(String link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        return intent;
    }

    /**
     * The same link, but only a browser may open it. The "selector" says: choose among apps that are browsers
     * (CATEGORY_APP_BROWSER), then hand that app this link.
     */
    public static Intent browserIntent(String link) {
        Intent intent = viewIntent(link);
        intent.setSelector(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
        return intent;
    }

    public static boolean isUberInstalled(PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(UBER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
