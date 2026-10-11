package com.safarsaathi.app.data.ask;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.baggage.BaggageFacts;

/**
 * Ask OnBoard's brain for Layer 1 (Quick answers), in three steps:
 * <ol>
 *   <li>{@link LanguagePicker}: which language to reply in (or the traveller's own choice);</li>
 *   <li>{@link IntentMatcher}: what is being asked;</li>
 *   <li>{@link QuickAnswerer}: the reply, from fixed sentences filled with the phone's saved data.</li>
 * </ol>
 * Plain Java with no Android parts and no network: the screen (AskViewModel) collects the facts and ML Kit's language
 * hint, then calls {@link #ask} on a background thread. Phases A2-A4 add the on-device AI, the cloud and the router
 * around this; Layer 1 itself stays as it is.
 */
public final class AskEngine {

    private final LanguagePicker languagePicker;
    private final IntentMatcher intentMatcher;
    private final QuickAnswerer quickAnswerer;

    public AskEngine(Lexicon lexicon) {
        this(lexicon, null);
    }

    /** @param baggage the checked bag-problem and Lost &amp; Found facts (baggage-facts.json), or null */
    public AskEngine(Lexicon lexicon, @Nullable BaggageFacts baggage) {
        this.languagePicker = new LanguagePicker(lexicon);
        this.intentMatcher = new IntentMatcher(lexicon);
        this.quickAnswerer = new QuickAnswerer(baggage);
    }

    /** The engine with the lexicon files that ship in the app. */
    public static AskEngine standard() {
        return new AskEngine(Lexicon.get());
    }

    /** The same, also quoting the sourced bag-problem and Lost &amp; Found facts. */
    public static AskEngine standard(@Nullable BaggageFacts baggage) {
        return new AskEngine(Lexicon.get(), baggage);
    }

    /**
     * @param question the traveller's words, typed or heard
     * @param override the reply language the traveller chose, or null for automatic
     * @param hint     ML Kit's language guess ({@link LanguageHint#NONE} when there is none)
     * @param facts    the phone's saved data right now
     */
    public Answer ask(String question, @Nullable AskLanguage override, LanguageHint hint, TripFacts facts) {
        AskLanguage language = languagePicker.pick(question, override, hint);
        IntentMatch match = intentMatcher.match(question);
        return quickAnswerer.answer(match, language, facts);
    }

    /** Step 1 alone (the screen uses it to pick the voice for listening next time). */
    public AskLanguage language(String question, @Nullable AskLanguage override, LanguageHint hint) {
        return languagePicker.pick(question, override, hint);
    }

    /** Step 2 alone (tests, and the router in phase A4). */
    public IntentMatch understand(String question) {
        return intentMatcher.match(question);
    }
}
