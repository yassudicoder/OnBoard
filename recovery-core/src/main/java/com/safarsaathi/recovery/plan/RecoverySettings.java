package com.safarsaathi.recovery.plan;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.safarsaathi.recovery.alternatives.RankingWeights;
import com.safarsaathi.recovery.alternatives.Urgency;

/**
 * Product choices for recovery plans: mock-data/recovery/recovery-settings.json. These are decisions, not facts about
 * airports or airlines (those live in airport-rules.json and airline-policies.json with their sources).
 */
public final class RecoverySettings {

    /** For humans reading the file. */
    public List<String> about = new ArrayList<>();
    /** yyyy-MM-dd */
    public String lastChecked;
    /** Ranking weights when the traveller chose NORMAL. */
    public RankingWeights normal;
    /** Ranking weights when the traveller chose URGENT. */
    public RankingWeights urgent;
    /** Alternatives may leave up to this many hours after the earliest possible departure. */
    public int searchWindowHours;
    /** One change of planes may take at most this many hours (no silly overnight stops in a third city). */
    public int maxLayoverHours;
    /** The Overnight section shows when the next option leaves more than this many hours after you can (or the next day). */
    public int overnightThresholdHours;
    /** The plan says "may be outdated" when the flight data was last confirmed longer ago than this. */
    public int staleAfterMinutes;
    /** How many options besides the recommended one the plan shows. */
    public int otherOptionsShown;
    /** How many ranked options the server keeps per plan (the alternatives endpoint lists them). */
    public int alternativesKept;

    public RankingWeights weights(Urgency urgency) {
        return urgency == Urgency.URGENT ? urgent : normal;
    }

    /** Everything wrong with the file, in plain words; empty when it is fine. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        boolean dateValid = lastChecked != null;
        if (dateValid) {
            try {
                LocalDate.parse(lastChecked);
            } catch (DateTimeParseException e) {
                dateValid = false;
            }
        }
        if (!dateValid) {
            problems.add("lastChecked must be a date like 2026-10-11, but is " + lastChecked);
        }
        checkWeights("normal", normal, problems);
        checkWeights("urgent", urgent, problems);
        if (normal != null && urgent != null && urgent.stopPenaltyMinutes > normal.stopPenaltyMinutes) {
            problems.add("urgent.stopPenaltyMinutes should not be higher than normal's: URGENT is about arriving first");
        }
        positive("searchWindowHours", searchWindowHours, problems);
        positive("maxLayoverHours", maxLayoverHours, problems);
        positive("overnightThresholdHours", overnightThresholdHours, problems);
        positive("staleAfterMinutes", staleAfterMinutes, problems);
        positive("alternativesKept", alternativesKept, problems);
        if (otherOptionsShown < 0) {
            problems.add("otherOptionsShown must be 0 or more");
        }
        return problems;
    }

    private static void checkWeights(String name, RankingWeights weights, List<String> problems) {
        if (weights == null) {
            problems.add(name + " ranking weights are missing");
        } else if (weights.stopPenaltyMinutes < 0 || weights.minutesPerThousandInr < 0) {
            problems.add(name + " ranking weights cannot be negative");
        }
    }

    private static void positive(String name, int value, List<String> problems) {
        if (value <= 0) {
            problems.add(name + " must be more than 0");
        }
    }
}
