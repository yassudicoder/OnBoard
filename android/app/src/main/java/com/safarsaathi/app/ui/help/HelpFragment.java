package com.safarsaathi.app.ui.help;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.baggage.BagIssue;
import com.safarsaathi.app.databinding.FragmentHelpBinding;
import com.safarsaathi.app.databinding.ItemHelpOptionBinding;

/**
 * Help tab (Baggage Shield B3): "What happened?" with four choices. The three bag problems (damaged, didn't arrive,
 * item missing) open the claim screen ({@link BagClaimFragment}); "I lost something at the airport" opens
 * {@link LostFoundFragment}. The Bags tab shows the same screen with a note about B1 (BagsFragment).
 */
public class HelpFragment extends Fragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public HelpFragment() {
        super(R.layout.fragment_help);
    }

    /** The Bags tab says what B1 will add; the Help tab doesn't. */
    protected boolean showBagNote() {
        return false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentHelpBinding binding = FragmentHelpBinding.bind(view);
        binding.helpB1Note.setVisibility(showBagNote() ? View.VISIBLE : View.GONE);
        option(binding.helpDamage, R.drawable.ic_warning_24, R.string.help_issue_damage, R.string.help_issue_damage_hint,
                () -> openClaim(view, BagIssue.DAMAGE));
        option(binding.helpDelay, R.drawable.ic_poi_luggage, R.string.help_issue_delay, R.string.help_issue_delay_hint,
                () -> openClaim(view, BagIssue.DELAY));
        option(binding.helpPilferage, R.drawable.ic_search_24, R.string.help_issue_pilferage,
                R.string.help_issue_pilferage_hint, () -> openClaim(view, BagIssue.PILFERAGE));
        option(binding.helpLost, R.drawable.ic_place_24, R.string.help_issue_lost, R.string.help_issue_lost_hint,
                () -> Navigation.findNavController(view).navigate(R.id.navigation_lost_found));
    }

    private static void option(ItemHelpOptionBinding row, @DrawableRes int icon, @StringRes int title,
                               @StringRes int hint, Runnable onClick) {
        row.optionIcon.setImageResource(icon);
        row.optionTitle.setText(title);
        row.optionHint.setText(hint);
        row.getRoot().setOnClickListener(card -> onClick.run());
    }

    private static void openClaim(View view, BagIssue issue) {
        Bundle arguments = new Bundle();
        arguments.putString(BagClaimFragment.ARG_ISSUE, issue.name());
        Navigation.findNavController(view).navigate(R.id.navigation_bag_claim, arguments);
    }
}
