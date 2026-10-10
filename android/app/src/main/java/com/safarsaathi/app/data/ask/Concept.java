package com.safarsaathi.app.data.ask;

/**
 * The building blocks of a question, the same in every language: "gate", "गेट" and "get" (a common Hinglish typo)
 * are all {@link #GATE}; "when", "kab", "कब", "kadhi" and "केव्हा" are all {@link #WHEN}.
 *
 * <p>The lexicon files (lexicon_*.properties) list the words for each concept, one line per concept. The
 * {@link IntentMatcher} then decides the intent from the concepts alone, so its rules never depend on a language.
 */
public enum Concept {
    GATE,
    BOARDING,
    /** close, band, बंद, last call */
    CLOSE,
    /** start, open, shuru, सुरू */
    START,
    /** when, what time, kab, kitne baje, kadhi, kiti vajta */
    WHEN,
    /** where, kahan, kuthe */
    WHERE,
    /** go, jaana, jau: "where do I go now?" */
    GO,
    FLIGHT,
    STATUS,
    DELAY,
    CANCEL,
    ON_TIME,
    /** land, arrive, pahunchna, पोहोचणे */
    ARRIVE,
    CONNECTION,
    /** miss, chhootna, चुकणे */
    MISS,
    BAG,
    BELT,
    /** collect, pick up, milega, मिळेल */
    COLLECT,
    NEXT,
    /** "what should I do", "kya karna hai", "kay karayche" */
    WHAT_TO_DO,
    DOCUMENT,
    PASSPORT,
    VISA,
    /** expire, valid, khatam, संपणे, मुदत */
    EXPIRY,
    WASHROOM,
    HELP_DESK,
    LIFT,
    HELP,
    HOTEL,
    CAB,
    /** find, search, best, cheapest, another, options: asking to look for something new */
    SEARCH,
    BOOK
}
