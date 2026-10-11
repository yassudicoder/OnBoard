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
    INSURANCE,
    /** expire, valid, khatam, संपणे, मुदत */
    EXPIRY,
    /** saved, do I have, rakha hai, सेव्ह: "is my visa saved?" */
    SAVED,
    WASHROOM,
    HELP_DESK,
    LIFT,
    HELP,
    HOTEL,
    CAB,
    /** find, search, best, cheapest, another, options: asking to look for something new */
    SEARCH,
    BOOK,

    // ---- Added for the everyday questions that used to get "Sorry, I didn't get that" ----
    /** hi, hello, namaste, namaskar */
    GREETING,
    /** thanks, thank you, shukriya, dhanyavaad */
    THANKS,
    /** damaged, broken, torn, toot gaya, फुटली: something is wrong with the bag itself */
    DAMAGE,
    /** "didn't arrive", "not on the belt", "nahi aaya", "आली नाही": the checked bag did not come */
    NOT_ARRIVED,
    /** stolen, taken out, chori, गायब: something is missing from inside the bag */
    PILFER,
    /** lost, left behind, kho gaya, हरवले: the traveller lost something */
    LOST,
    /** lost and found, lost property */
    LOST_FOUND,
    /** food, eat, restaurant, cafe, khana, जेवण */
    FOOD,
    /** security, security check, frisking, सुरक्षा जांच */
    SECURITY,
    /** check-in, bag drop, drop my bag, counter */
    CHECKIN,
    /** airline baggage services, baggage counter */
    BAGGAGE_SERVICES,
    /** buggy, golf cart */
    BUGGY,
    /** wheelchair, special assistance, व्हीलचेयर */
    WHEELCHAIR,
    /** allowance, weight limit, how many kg, excess baggage */
    ALLOWANCE,
    /** power bank, battery, liquids, lighter, "can I carry": what may go in the bags */
    CARRY_RULES,
    /** get to the airport, reach the airport, leave for the airport */
    GET_THERE
}
