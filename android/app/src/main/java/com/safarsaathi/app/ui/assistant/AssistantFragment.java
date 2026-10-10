package com.safarsaathi.app.ui.assistant;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.PlaceholderFragment;

/**
 * Assistant tab: chat and voice help in the traveller's own language (Hinglish too).
 * PARKED (CLAUDE.md "Parked"): not in the bottom bar any more. Placeholder of the old plan's Phase 6.
 */
public class AssistantFragment extends PlaceholderFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public AssistantFragment() {
        super(R.drawable.ic_assistant_24, R.string.assistant_headline, R.string.assistant_description, "6");
    }
}
