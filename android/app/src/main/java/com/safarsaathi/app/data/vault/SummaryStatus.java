package com.safarsaathi.app.data.vault;

/**
 * One word about a saved document, for other parts of the app (VaultSummaryReader). Worst first: a document gets the
 * first of these that applies.
 */
public enum SummaryStatus {
    /** A page of it is missing from the phone (deleted or damaged storage): it cannot be shown. */
    MISSING,
    /** Its expiry date (or, for a visa, insurance or similar, its last valid day) is already past. */
    EXPIRED,
    /** The name on it does not match its traveller's name (case, spacing, order and middle names are ignored). */
    MISMATCH,
    /**
     * Its printed expiry date (passport, ID card) is within {@link VaultSummaries#EXPIRES_SOON_DAYS} days. Not used for
     * visas or insurance, which are meant to end around the trip (the pre-departure check, V4, judges those).
     */
    EXPIRES_SOON,
    /** Saved, but not yet included in a passed "make offline-ready" check of its trip (V6). */
    NOT_OFFLINE_YET,
    /** None of the above. */
    READY
}
