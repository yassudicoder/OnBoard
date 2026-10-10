package com.safarsaathi.recovery.alternatives;

/**
 * How the traveller wants alternatives ranked (they choose; saved per connection by name, e.g. "URGENT").
 * The weights behind each are product choices in mock-data/recovery/recovery-settings.json, see
 * {@link AlternativeRanker}.
 */
public enum Urgency {
    /** Earliest arrival first, but an extra change of planes or a higher cost counts against an option. */
    NORMAL,
    /** Earliest arrival dominates; changes of planes and cost hardly count (cost is still shown). */
    URGENT
}
