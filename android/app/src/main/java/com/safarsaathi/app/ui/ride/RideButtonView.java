package com.safarsaathi.app.ui.ride;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.ride.RideCheck;
import com.safarsaathi.app.data.ride.RidePlace;
import com.safarsaathi.app.data.ride.RideProvider;
import com.safarsaathi.app.data.ride.RideRequest;
import com.safarsaathi.app.data.ride.RideTapCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The one shared "Get a ride" button (layout: res/layout/view_ride_button.xml). Put it in a layout as
 * {@code <com.safarsaathi.app.ui.ride.RideButtonView>} and call {@link #bind} with the ride.
 *
 * <p>It shows what {@link RideCheck} says (disabled with a reason, approximate points, offline), counts each tap
 * locally ({@link RideTapCounter}), hands the ride to the {@link RideProvider}, and then says only what we really
 * know: "Opened in Uber". Booking, price, driver and arrival time happen inside Uber and never reach us.
 */
public class RideButtonView extends LinearLayout {

    private final MaterialButton button;
    private final TextView note;
    private final TextView offline;
    private final TextView status;

    @Nullable
    private RideProvider provider;
    @Nullable
    private RideRequest request;

    public RideButtonView(@NonNull Context context) {
        this(context, null);
    }

    public RideButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        inflate(context, R.layout.view_ride_button, this);
        button = findViewById(R.id.ride_button);
        note = findViewById(R.id.ride_note);
        offline = findViewById(R.id.ride_offline);
        status = findViewById(R.id.ride_status);
        button.setOnClickListener(view -> onTap());
    }

    /** For cards with a coloured background (the flight card): the notes use that card's text colour. */
    public void setTextColor(@ColorInt int color) {
        ColorStateList colors = ColorStateList.valueOf(color);
        for (TextView text : new TextView[] {note, offline, status}) {
            text.setTextColor(colors);
            TextViewCompat.setCompoundDrawableTintList(text, colors);
        }
    }

    /**
     * Shows the button for this ride. Safe to call again on every screen refresh: the "Opened in Uber" line stays
     * unless the drop-off changed.
     *
     * @param buttonText e.g. "Get a ride to Mumbai T2" (estimates in it must say "estimate")
     */
    public void bind(RideProvider provider, RideRequest request, CharSequence buttonText) {
        if (this.request != null && !Objects.equals(dropoffLabel(this.request), dropoffLabel(request))) {
            status.setVisibility(View.GONE);
        }
        this.provider = provider;
        this.request = request;
        button.setText(buttonText);

        RideCheck check = RideCheck.of(request, NetworkStatus.isOnline(getContext()));
        button.setEnabled(check.enabled());
        List<String> notes = new ArrayList<>();
        if (check.problem == RideCheck.Problem.NO_DROPOFF) {
            notes.add(getContext().getString(R.string.ride_disabled_no_dropoff));
        }
        if (check.approximatePickup) {
            notes.add(getContext().getString(R.string.ride_approximate_pickup));
        }
        if (check.approximateDropoff) {
            notes.add(getContext().getString(R.string.ride_approximate_dropoff));
        }
        if (check.checkDropoffInRideApp) {
            notes.add(getContext().getString(R.string.ride_check_dropoff, provider.name()));
        }
        note.setText(String.join("\n", notes));
        note.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
        // Offline: still tappable (the link is built on the phone), but booking needs internet.
        offline.setVisibility(check.enabled() && check.offline ? View.VISIBLE : View.GONE);
    }

    private void onTap() {
        RideProvider currentProvider = provider;
        RideRequest currentRequest = request;
        if (currentProvider == null || currentRequest == null || !currentRequest.hasDropoff()) {
            return;
        }
        Context context = getContext();
        RideTapCounter.count(context, currentRequest.placement);
        offline.setVisibility(NetworkStatus.isOnline(context) ? View.GONE : View.VISIBLE);
        showStatus(currentProvider.open(context, currentRequest));
    }

    private void showStatus(RideProvider.Opened opened) {
        int text;
        int icon;
        switch (opened) {
            case APP:
                text = R.string.ride_opened_app;
                icon = R.drawable.ic_check_circle_24;
                break;
            case BROWSER:
                text = R.string.ride_opened_browser;
                icon = R.drawable.ic_check_circle_24;
                break;
            case DEMO:
                text = R.string.ride_opened_demo;
                icon = 0;
                break;
            case NOTHING:
            default:
                text = R.string.ride_opened_nothing;
                icon = R.drawable.ic_warning_24;
                break;
        }
        status.setText(text);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(status, icon, 0, 0, 0);
        status.setVisibility(View.VISIBLE);
    }

    @Nullable
    private static String dropoffLabel(RideRequest request) {
        RidePlace dropoff = request.dropoff;
        return dropoff == null ? null : dropoff.label();
    }
}
