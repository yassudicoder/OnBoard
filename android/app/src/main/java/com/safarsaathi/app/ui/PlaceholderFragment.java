package com.safarsaathi.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.safarsaathi.app.R;
import com.safarsaathi.app.databinding.FragmentPlaceholderBinding;

/**
 * Phase 0 "coming soon" screen, shared by all five tabs: an icon, a headline, one line about what the
 * tab will do, and the phase that builds it (layout: res/layout/fragment_placeholder.xml).
 *
 * <p>Each tab is a tiny subclass that only chooses its icon and texts (see HomeFragment). When a tab
 * gets its real screen (Trip in Phase 1, Home in Phase 3, ...), that fragment stops extending this class
 * and gets its own layout and ViewModel.
 */
public abstract class PlaceholderFragment extends Fragment {

    private final int iconRes;
    private final int headlineRes;
    private final int descriptionRes;
    private final int phase;

    protected PlaceholderFragment(@DrawableRes int iconRes, @StringRes int headlineRes,
                                  @StringRes int descriptionRes, int phase) {
        // Passing the layout to Fragment's constructor makes the fragment inflate it for us,
        // so we do not need to override onCreateView().
        super(R.layout.fragment_placeholder);
        this.iconRes = iconRes;
        this.headlineRes = headlineRes;
        this.descriptionRes = descriptionRes;
        this.phase = phase;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // View binding: FragmentPlaceholderBinding is generated from fragment_placeholder.xml.
        // bind(view) connects it to the views that were just inflated. It is a local variable on purpose:
        // the fragment can outlive its views (e.g. while another tab is shown), so we do not keep it.
        FragmentPlaceholderBinding binding = FragmentPlaceholderBinding.bind(view);
        binding.placeholderIcon.setImageResource(iconRes);
        binding.placeholderHeadline.setText(headlineRes);
        binding.placeholderDescription.setText(descriptionRes);
        binding.placeholderPhase.setText(getString(R.string.placeholder_coming_in_phase, phase));
    }
}
