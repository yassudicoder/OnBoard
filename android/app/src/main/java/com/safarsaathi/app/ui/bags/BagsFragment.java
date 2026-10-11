package com.safarsaathi.app.ui.bags;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.safarsaathi.app.R;
import com.safarsaathi.app.databinding.ViewSampleBagBinding;
import com.safarsaathi.app.ui.help.HelpFragment;

/**
 * Bags tab, Baggage Shield. Until B1 (register the bag, scan its tag, photograph it before hand-over with a
 * server-signed receipt) it shows the bag-problem guide from the Help tab (B3), with a note on what B1 adds.
 * On top: "Your bags" with one hardcoded SAMPLE bag (layout/view_sample_bag.xml, Figma B01), so the tab is never
 * empty in the demo. It is labelled SAMPLE DATA; "Register a bag" only says that it comes with B1.
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The guide's column starts with the B1 note; the sample bag goes in right above it.
        View note = view.findViewById(R.id.help_b1_note);
        ViewGroup column = (ViewGroup) note.getParent();
        ViewSampleBagBinding sample = ViewSampleBagBinding.inflate(getLayoutInflater(), column, false);
        column.addView(sample.getRoot(), column.indexOfChild(note));
        sample.sampleRegister.setOnClickListener(v ->
                Snackbar.make(view, R.string.bags_register_coming, Snackbar.LENGTH_LONG).show());
    }
}
