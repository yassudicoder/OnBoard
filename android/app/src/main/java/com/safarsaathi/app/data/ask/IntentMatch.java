package com.safarsaathi.app.data.ask;

import androidx.annotation.Nullable;

/**
 * What {@link IntentMatcher} understood from one question: the intent, how sure it is, and two details some answers
 * need (which kind of place, which gate number was mentioned).
 */
public final class IntentMatch {

    /**
     * How sure the matcher is. The router (phase A4) only trusts a Quick answer on its own at HIGH; otherwise it may
     * prefer the on-device or cloud AI.
     */
    public enum Confidence {
        /** Nothing matched. */
        NONE,
        /** Something matched, but weakly or close to another intent. */
        MEDIUM,
        /** A strong rule matched, clearly ahead of every other intent. */
        HIGH
    }

    /** For {@link AskIntent#WHERE_IS}: which kind of place. */
    public enum Place { WASHROOM, HELP_DESK, LIFT }

    public final AskIntent intent;
    public final Confidence confidence;
    /** The winning rule's score (0 when nothing matched); for tests and the routing log. */
    public final int score;
    /** WHERE_IS only: the kind of place asked for, else null. */
    @Nullable
    public final Place place;
    /** A gate number in the question ("washroom near gate 32" -> "32"), else null. */
    @Nullable
    public final String gateNumber;

    IntentMatch(AskIntent intent, Confidence confidence, int score, @Nullable Place place,
                @Nullable String gateNumber) {
        this.intent = intent;
        this.confidence = confidence;
        this.score = score;
        this.place = place;
        this.gateNumber = gateNumber;
    }

    static IntentMatch none() {
        return new IntentMatch(AskIntent.UNKNOWN, Confidence.NONE, 0, null, null);
    }

    @Override
    public String toString() {
        return intent + " (" + confidence + ", " + score + ")";
    }
}
