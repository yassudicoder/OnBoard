package com.safarsaathi.recovery.alternatives;

/**
 * How much a change of planes and extra cost count against an alternative, in "minutes later" (product choices, not
 * facts; set in mock-data/recovery/recovery-settings.json). Example for NORMAL: 180 = an extra change of planes counts
 * like arriving 3 hours later; 10 = every ₹1,000 of extra cost counts like 10 minutes later.
 */
public final class RankingWeights {

    public int stopPenaltyMinutes;
    public int minutesPerThousandInr;

    /** For the JSON readers. */
    public RankingWeights() {
    }

    public RankingWeights(int stopPenaltyMinutes, int minutesPerThousandInr) {
        this.stopPenaltyMinutes = stopPenaltyMinutes;
        this.minutesPerThousandInr = minutesPerThousandInr;
    }
}
