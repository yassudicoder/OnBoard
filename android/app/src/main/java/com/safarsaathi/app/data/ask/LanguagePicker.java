package com.safarsaathi.app.data.ask;

import java.util.List;

/**
 * Picks the language to reply in, unless the traveller chose one (the language chip / settings override everything).
 *
 * <p>How, in plain words:
 * <ol>
 *   <li>Script: mostly Devanagari letters = Hindi or Marathi; otherwise English, Hinglish, or Marathi in English
 *       letters.</li>
 *   <li>Marker words: everyday words that give a language away ("है/कहाँ/मेरा" = Hindi, "आहे/कुठे/माझी" = Marathi,
 *       "hai/kahan/mera" = Hinglish, "aahe/kuthe/majhi" = Marathi in English letters, "the/is/my" = English). The
 *       language with the most markers wins. Marathi in English letters is answered in Marathi.</li>
 *   <li>A tie (or no markers at all): Google ML Kit's on-device guess ({@link LanguageHint}) decides; without one,
 *       Hindi for Devanagari, and for English letters the Indian language when it tied with English (an English word
 *       in a Hindi sentence is common, the other way round is not), else English.</li>
 * </ol>
 * Plain Java, checked by LanguagePickerTest and the golden phrase set.
 */
public final class LanguagePicker {

    /** ML Kit must be at least this sure to break a tie. */
    static final float HINT_THRESHOLD = 0.5f;

    private final Lexicon lexicon;

    public LanguagePicker(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    /**
     * @param question the question as typed or heard
     * @param override the language the traveller chose, or null for automatic
     * @param hint     ML Kit's guess, or {@link LanguageHint#NONE}
     */
    public AskLanguage pick(String question, AskLanguage override, LanguageHint hint) {
        if (override != null) {
            return override;
        }
        List<String> words = AskText.words(question);
        if (AskText.isMostlyDevanagari(question)) {
            int hindi = lexicon.markerCount("hi", words);
            int marathi = lexicon.markerCount("mr", words);
            if (marathi != hindi) {
                return marathi > hindi ? AskLanguage.MR : AskLanguage.HI;
            }
            return hint.confidence("mr") >= HINT_THRESHOLD ? AskLanguage.MR : AskLanguage.HI;
        }

        int english = lexicon.markerCount("en", words);
        int hinglish = lexicon.markerCount("hinglish", words);
        int marathi = lexicon.markerCount("mr_latn", words);
        int indian = Math.max(hinglish, marathi);
        if (indian == 0) {
            return hint.confidence("hi-Latn") >= HINT_THRESHOLD ? AskLanguage.HINGLISH : AskLanguage.EN;
        }
        if (english > indian) {
            return AskLanguage.EN;
        }
        if (marathi != hinglish) {
            return marathi > hinglish ? AskLanguage.MR : AskLanguage.HINGLISH;
        }
        // Hinglish and Marathi markers tied: Hinglish, by far the more common of the two. (ML Kit can't help here: it
        // knows Hindi in English letters, but not Marathi in English letters.)
        return AskLanguage.HINGLISH;
    }
}
