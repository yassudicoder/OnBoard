package com.safarsaathi.app.ui.bags;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.PlaceholderFragment;

/**
 * Bags tab, Baggage Shield: register the bag, scan its tag, photograph it before hand-over (time-stamped,
 * tamper-evident) and follow it to the belt (CLAUDE.md, Baggage Shield). Placeholder until phase B1.
 */
public class BagsFragment extends PlaceholderFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public BagsFragment() {
        super(R.drawable.ic_trip_24, R.string.bags_headline, R.string.bags_description, "B1");
    }
}
