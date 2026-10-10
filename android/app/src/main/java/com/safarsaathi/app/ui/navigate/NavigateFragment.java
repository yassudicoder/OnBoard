package com.safarsaathi.app.ui.navigate;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.PlaceholderFragment;

/**
 * Navigate tab, Gate Navigation: Google-Maps-style walking directions inside the terminal to the boarding gate, which
 * re-route when the gate changes (CLAUDE.md, Gate Navigation). Placeholder until phases G1-G3.
 */
public class NavigateFragment extends PlaceholderFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public NavigateFragment() {
        super(R.drawable.ic_navigate_24, R.string.navigate_headline, R.string.navigate_description, "G1–G3");
    }
}
