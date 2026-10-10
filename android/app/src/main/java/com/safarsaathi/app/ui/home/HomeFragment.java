package com.safarsaathi.app.ui.home;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.PlaceholderFragment;

/**
 * Home tab, "Do this now": one big card with the trip's very next step and one button.
 * Placeholder until Phase 3.
 */
public class HomeFragment extends PlaceholderFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public HomeFragment() {
        super(R.drawable.ic_home_24, R.string.home_headline, R.string.home_description, 3);
    }
}
