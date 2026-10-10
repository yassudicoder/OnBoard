package com.safarsaathi.backend.planb;

import java.time.Instant;
import java.util.List;

import com.safarsaathi.recovery.alternatives.CandidateRoute;

/**
 * Somewhere to ask "which flights could get the traveller from A to B?". It only offers routes: checking them against
 * minimum connection times and ranking them is recovery-core's AlternativeRanker, and nothing is ever booked here.
 *
 * <p>Today one implementation, {@link SampleScheduleProvider} (SAMPLE DATA, no key). Future: a Duffel test-mode
 * adapter (fake airline only), selected when its test key is set, behind this same interface.
 */
public interface AlternativeFlightProvider {

    /**
     * Direct flights and one-stop routes from {@code from} to {@code to} whose first flight leaves between
     * {@code earliestDeparture} and {@code latestDeparture}. Must never throw: no answer = an empty list.
     */
    List<CandidateRoute> search(String from, String to, Instant earliestDeparture, Instant latestDeparture);

    /** A short name for the logs and the plan, e.g. "SampleScheduleProvider (SAMPLE DATA)". */
    String name();
}
