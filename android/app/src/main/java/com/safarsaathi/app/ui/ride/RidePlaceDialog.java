package com.safarsaathi.app.ui.ride;

import android.content.Context;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.ride.RecentRidePlaces;

import java.util.List;
import java.util.function.Consumer;

/**
 * "Where to?" for a ride when the trip has no hotel address (or the traveller wants somewhere else): type an
 * address, or tap a saved one. The address stays on the phone ({@link RecentRidePlaces}) and only leaves it inside
 * the Uber link, when the traveller taps the ride button.
 */
public final class RidePlaceDialog {

    private RidePlaceDialog() {
    }

    /** @param onChosen gets the chosen address (already trimmed and saved) */
    public static void show(Context context, Consumer<String> onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_ride_place, null);
        TextInputLayout inputLayout = view.findViewById(R.id.ride_place_input_layout);
        TextInputEditText input = view.findViewById(R.id.ride_place_input);
        TextView savedLabel = view.findViewById(R.id.ride_place_saved_label);
        ChipGroup saved = view.findViewById(R.id.ride_place_saved);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.ride_place_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ride_place_use, null) // click handled below, so a blank field can stay open
                .create();

        Consumer<String> choose = address -> {
            String trimmed = address.trim();
            RecentRidePlaces.remember(context, trimmed);
            dialog.dismiss();
            onChosen.accept(trimmed);
        };

        List<String> places = RecentRidePlaces.all(context);
        savedLabel.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        for (String place : places) {
            Chip chip = new Chip(context);
            chip.setText(place);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setChipIconResource(R.drawable.ic_place_24);
            chip.setOnClickListener(tapped -> choose.accept(place));
            saved.addView(chip);
        }

        Runnable useTyped = () -> {
            Editable text = input.getText();
            if (text == null || text.toString().trim().isEmpty()) {
                inputLayout.setError(context.getString(R.string.ride_place_empty));
            } else {
                choose.accept(text.toString());
            }
        };
        input.setOnEditorActionListener((field, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                useTyped.run();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(shown ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> useTyped.run()));
        dialog.show();
    }
}
