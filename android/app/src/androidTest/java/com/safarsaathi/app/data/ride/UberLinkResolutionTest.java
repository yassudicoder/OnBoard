package com.safarsaathi.app.data.ride;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * On a phone WITHOUT the Uber app (like our emulator), the Uber link must still open: in a browser, which shows
 * Uber's mobile website. Nothing is launched here; the test only asks Android which apps WOULD open the link.
 */
@RunWith(AndroidJUnit4.class)
public class UberLinkResolutionTest {

    private static final String LINK = UberDeepLink.build(null, RidePlace.myLocation(),
            RidePlace.point("Mumbai Airport Terminal 2, Departures", null, 19.0969906, 72.8739312, true));

    private final PackageManager packageManager =
            ApplicationProvider.getApplicationContext().getPackageManager();

    @Test
    public void uberLink_resolvesToABrowser_whenUberIsNotInstalled() {
        assumeFalse("this check needs a phone without the Uber app",
                UberDeepLinkProvider.isUberInstalled(packageManager));

        List<ResolveInfo> handlers = packageManager.queryIntentActivities(
                UberDeepLinkProvider.viewIntent(LINK), PackageManager.MATCH_DEFAULT_ONLY);
        Set<String> browsers = packages(packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
                        .addCategory(Intent.CATEGORY_BROWSABLE),
                PackageManager.MATCH_DEFAULT_ONLY));

        assertFalse("no app at all can open the Uber link", handlers.isEmpty());
        for (ResolveInfo handler : handlers) {
            String app = handler.activityInfo.packageName;
            assertNotEquals(UberDeepLinkProvider.UBER_PACKAGE, app);
            assertTrue(app + " opens the Uber link but is not a browser", browsers.contains(app));
        }
    }

    @Test
    public void browserFallback_resolvesToABrowserApp() {
        ResolveInfo browser = packageManager.resolveActivity(
                UberDeepLinkProvider.browserIntent(LINK), PackageManager.MATCH_DEFAULT_ONLY);

        assertNotNull("the forced-browser fallback finds no browser", browser);
    }

    private static Set<String> packages(List<ResolveInfo> infos) {
        Set<String> names = new HashSet<>();
        for (ResolveInfo info : infos) {
            names.add(info.activityInfo.packageName);
        }
        return names;
    }
}
