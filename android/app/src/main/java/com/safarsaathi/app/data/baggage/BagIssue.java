package com.safarsaathi.app.data.baggage;

/**
 * What went wrong with a checked bag (Help tab, "What happened?"). All three are the airline's responsibility, so they
 * share one claim flow: Property Irregularity Report (PIR) at the airline's baggage services counter, photos, details,
 * the written-complaint deadline and a claim pack PDF (CLAUDE.md, Baggage Shield). Something lost INSIDE the terminal
 * is not a bag claim: that goes to the airport's Lost &amp; Found (LostFoundFragment).
 */
public enum BagIssue {
    /** The bag arrived broken: wheel, handle, zip, a hole, a crushed corner. */
    DAMAGE,
    /** The bag did not come out on the belt (or came days later). */
    DELAY,
    /** The bag arrived, but something is missing from inside it ("pilferage"). */
    PILFERAGE;

    /** Reads a saved or passed-on name back; unknown text = DAMAGE (the most common case). */
    public static BagIssue fromName(String name) {
        if (name != null) {
            for (BagIssue issue : values()) {
                if (issue.name().equals(name)) {
                    return issue;
                }
            }
        }
        return DAMAGE;
    }
}
