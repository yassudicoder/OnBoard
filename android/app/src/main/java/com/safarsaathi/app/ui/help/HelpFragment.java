package com.safarsaathi.app.ui.help;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.PlaceholderFragment;

/**
 * Help tab: "What happened?" (damaged, delayed or missing bag; something lost at the airport) with step-by-step
 * guidance and the official contacts (CLAUDE.md, Baggage Shield 3.3). Placeholder until phase B3.
 */
public class HelpFragment extends PlaceholderFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public HelpFragment() {
        super(R.drawable.ic_help_24, R.string.help_headline, R.string.help_description, "B3");
    }
}
