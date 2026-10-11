package com.safarsaathi.app.ui.help;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.baggage.BagIssue;
import com.safarsaathi.app.data.baggage.BaggageFacts;
import com.safarsaathi.app.databinding.FragmentLostFoundBinding;
import com.safarsaathi.app.ui.navigate.NavigateFragment;

/**
 * "I lost something at the airport" (Baggage Shield B3). Where it was lost decides who to ask:
 * <ul>
 *   <li>in the terminal: the airport's Lost &amp; Found, with its official facts (baggage-facts.json, source and date),
 *       the way there on the T2 map, a report the traveller copies into the official form, and the official portal
 *       (opened in the browser; OnBoard never submits anything);</li>
 *   <li>on the plane: the airline;</li>
 *   <li>inside a checked bag: the bag claim flow ("Item missing from bag").</li>
 * </ul>
 */
public class LostFoundFragment extends Fragment {

    /** The Lost &amp; Found place on the Mumbai T2 map (mock-data/airports/BOM-T2/map.json). */
    static final String LOST_FOUND_NODE = "lost_found";

    private FragmentLostFoundBinding binding;
    private BaggageFacts facts;

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public LostFoundFragment() {
        super(R.layout.fragment_lost_found);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentLostFoundBinding.bind(view);
        facts = BagClaimFragment.loadFacts(requireContext());
        BaggageFacts.LostFound lf = facts.lostFound;

        binding.lostName.setText(lf.name);
        binding.lostT2Location.setText(lf.t2Location);
        binding.lostT1Location.setText(lf.t1Location);
        binding.lostHours.setText(lf.hours);
        binding.lostKept.setText(lf.keptFor);
        StringBuilder bring = new StringBuilder();
        for (String item : lf.toCollect) {
            bring.append("• ").append(item).append('\n');
        }
        binding.lostToCollect.setText(bring.toString().trim());
        binding.lostSource.setText(getString(R.string.lost_source, lf.sourceUrl, lf.lastChecked));
        binding.lostPortalNote.setText(lf.portalNote);

        binding.lostShowWay.setOnClickListener(button -> {
            NavigateFragment.requestDirections(LOST_FOUND_NODE);
            BottomNavigationView tabs = requireActivity().findViewById(R.id.bottom_nav);
            if (tabs != null) {
                tabs.setSelectedItemId(R.id.navigation_navigate);
            }
        });
        binding.lostCopy.setOnClickListener(button -> copyReport());
        binding.lostOpenPortal.setOnClickListener(button -> openLink(lf.portalUrl));
        binding.lostWhereGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showWhere(checkedId);
            }
        });
        showWhere(binding.lostWhereGroup.getCheckedButtonId());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // R.id values are not compile-time constants with AGP 9, so if/else instead of switch.
    private void showWhere(int checkedId) {
        if (checkedId == R.id.lost_where_bag) {
            // Something missing from a checked bag is the airline's: the bag claim flow.
            binding.lostWhereGroup.check(R.id.lost_where_terminal);
            Bundle arguments = new Bundle();
            arguments.putString(BagClaimFragment.ARG_ISSUE, BagIssue.PILFERAGE.name());
            Navigation.findNavController(binding.getRoot()).navigate(R.id.navigation_bag_claim, arguments);
            return;
        }
        boolean aircraft = checkedId == R.id.lost_where_aircraft;
        binding.lostAircraftText.setVisibility(aircraft ? View.VISIBLE : View.GONE);
        binding.lostTerminalSection.setVisibility(aircraft ? View.GONE : View.VISIBLE);
    }

    /** The report as plain text, for pasting into the official form (we never send it anywhere ourselves). */
    private void copyReport() {
        String report = getString(R.string.lost_report_heading) + "\n"
                + line(R.string.lost_what, binding.lostWhat.getText())
                + line(R.string.lost_where_exactly, binding.lostPlace.getText())
                + line(R.string.lost_when, binding.lostWhen.getText())
                + line(R.string.lost_flight, binding.lostFlight.getText())
                + line(R.string.lost_details, binding.lostDetails.getText());
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.lost_report_heading), report.trim()));
        }
        Snackbar.make(binding.getRoot(), R.string.lost_copied, Snackbar.LENGTH_LONG).show();
    }

    private String line(int label, @Nullable CharSequence value) {
        String text = value == null ? "" : value.toString().trim();
        return text.isEmpty() ? "" : getString(label) + ": " + text + "\n";
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.claim_open_link_failed, Snackbar.LENGTH_LONG).show();
        }
    }
}
