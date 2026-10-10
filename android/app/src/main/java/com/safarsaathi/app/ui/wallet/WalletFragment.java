package com.safarsaathi.app.ui.wallet;

import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.PlaceholderFragment;

/**
 * Wallet tab, the offline locker: boarding pass, tickets and ID that open without internet.
 * Placeholder until Phase 7.
 */
public class WalletFragment extends PlaceholderFragment {

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public WalletFragment() {
        super(R.drawable.ic_wallet_24, R.string.wallet_headline, R.string.wallet_description, 7);
    }
}
