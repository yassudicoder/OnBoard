package com.safarsaathi.app.ui.profile;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.PlaceholderFragment;

/**
 * Profile tab: language, persona (who is travelling), large-text mode and saved cards.
 * Placeholder until Phase 6.
 */
public class ProfileFragment extends PlaceholderFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public ProfileFragment() {
        super(R.drawable.ic_profile_24, R.string.profile_headline, R.string.profile_description, 6);
    }
}
