package com.safarsaathi.app.data.ride;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safarsaathi.app.R;

/**
 * Demo provider for emulators without Uber (android/local.properties: RIDE_PROVIDER=mock). It opens nothing: a
 * dialog shows the pickup, the drop-off and the exact link the real provider would open, so judges can see what
 * Uber would receive. "Open link anyway" hands the same link to {@link UberDeepLinkProvider}.
 */
public final class MockRideProvider implements RideProvider {

    @Nullable
    private final String clientId;

    /** @param clientId same as the real provider, so the shown link is exactly the real one */
    public MockRideProvider(@Nullable String clientId) {
        this.clientId = clientId;
    }

    @Override
    public String name() {
        return "Uber (demo)";
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public String link(RideRequest request) {
        return UberDeepLink.build(clientId, request.pickup, request.dropoff);
    }

    @Override
    public Opened open(Context context, RideRequest request) {
        String message = context.getString(R.string.ride_demo_message,
                describe(context, request.pickup), describe(context, request.dropoff), link(request));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.ride_demo_title)
                .setMessage(message)
                .setPositiveButton(R.string.ride_demo_close, null)
                .setNeutralButton(R.string.ride_demo_open_anyway,
                        (dialog, which) -> new UberDeepLinkProvider(clientId).open(context, request))
                .show();
        return Opened.DEMO;
    }

    private static String describe(Context context, @Nullable RidePlace place) {
        if (place == null || place.myLocation || place.isEmpty()) {
            return context.getString(R.string.ride_place_my_location);
        }
        String label = place.label();
        return place.approximate ? context.getString(R.string.ride_place_approximate, label) : label;
    }
}
