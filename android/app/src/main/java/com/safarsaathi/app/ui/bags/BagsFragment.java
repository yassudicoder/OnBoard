package com.safarsaathi.app.ui.bags;

import com.safarsaathi.app.ui.help.HelpFragment;

/**
 * Bags tab, Baggage Shield. Until B1 (register the bag, scan its tag, photograph it before hand-over with a
 * server-signed receipt) it shows the bag-problem guide from the Help tab (B3), with a note on what B1 adds.
 */
public class BagsFragment extends HelpFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public BagsFragment() {
        super();
    }

    @Override
    protected boolean showBagNote() {
        return true;
    }
}
