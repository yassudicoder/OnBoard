package com.safarsaathi.app.data.ask;

import androidx.annotation.Nullable;

import java.time.Instant;

/** One reply from Ask OnBoard: the words, where they came from, and an optional button. */
public final class Answer {

    /** Which layer answered; the chip under the reply says so. A2 adds the on-device AI, A3 the cloud. */
    public enum Source {
        /** Layer 1, Quick answers: fixed sentences filled with the phone's own data. "Answered on your phone". */
        PHONE
    }

    /** A button under the reply that opens the right screen. */
    public enum Action {
        /** The Navigate tab (gate known: "Navigate to gate"). */
        NAVIGATE,
        /** The Navigate tab, to find a place on the terminal map. */
        OPEN_MAP,
        OPEN_BAGS,
        /** The Document Vault (it asks for the fingerprint / PIN itself). */
        OPEN_VAULT,
        /** The vault is locked: show its fingerprint / face / PIN prompt here, then answer the same question again. */
        UNLOCK_VAULT,
        /** Home: flight card, changes, and the Uber ride buttons. */
        OPEN_HOME
    }

    public final String text;
    public final AskLanguage language;
    public final AskIntent intent;
    public final IntentMatch.Confidence confidence;
    public final Source source;
    /** The saved data's "as of" time ("as of 07:02" on the chip); null for fixed data such as the map. */
    @Nullable
    public final Instant asOf;
    @Nullable
    public final Action action;
    /**
     * True when the question asked for something only an online service can do (search flights or hotels, a cab):
     * the reply explains that, and the router (phase A4) will send such questions to the cloud when online.
     */
    public final boolean needsOnline;

    public Answer(String text, AskLanguage language, AskIntent intent, IntentMatch.Confidence confidence,
                  Source source, @Nullable Instant asOf, @Nullable Action action, boolean needsOnline) {
        this.text = text;
        this.language = language;
        this.intent = intent;
        this.confidence = confidence;
        this.source = source;
        this.asOf = asOf;
        this.action = action;
        this.needsOnline = needsOnline;
    }

    @Override
    public String toString() {
        return intent + "/" + language + ": " + text;
    }
}
