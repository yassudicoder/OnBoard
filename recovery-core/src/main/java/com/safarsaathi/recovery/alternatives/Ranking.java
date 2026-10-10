package com.safarsaathi.recovery.alternatives;

import java.util.ArrayList;
import java.util.List;

import com.safarsaathi.recovery.connection.ConnectionCheck;

/** What {@link AlternativeRanker} found. */
public final class Ranking {

    /** The one option to show first; null when there is no usable option. */
    public RankedOption recommended;
    /** Up to N more options (RecoverySettings.otherOptionsShown), best first. */
    public List<RankedOption> others = new ArrayList<>();
    /** Every usable option, best first (the recommended one included). */
    public List<RankedOption> ranked = new ArrayList<>();
    /** Routes thrown out because a change of planes is shorter than the minimum connection time. */
    public List<Rejected> rejected = new ArrayList<>();

    /** A route that fails a minimum connection time, and why. */
    public static final class Rejected {
        public CandidateRoute route;
        public ConnectionCheck failedCheck;
        public String reason;
    }
}
