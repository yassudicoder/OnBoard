package com.safarsaathi.app.data.ask;

/**
 * What the traveller wants to know. The first group are the Quick answers (Layer 1): the phone answers them at once
 * from its own saved data, with no AI model and no internet. The last group are requests that need searching or
 * booking: the phone recognises them, but cannot answer them by itself.
 */
public enum AskIntent {
    GATE(true),
    BOARDING_TIME(true),
    BOARDING_CLOSE(true),
    FLIGHT_STATUS(true),
    CONNECTION_STATUS(true),
    BAGGAGE_BELT(true),
    BAG_STATUS(true),
    NEXT_STEP(true),
    /** "When does my passport expire?": expiry and status from the vault's summaries. */
    DOCUMENT_EXPIRY(true),
    /** "Is my visa saved?", "Am I missing any document?": what the vault has, and what it says is missing. */
    DOCUMENT_MISSING(true),
    /** Washrooms, help desks and lifts on the terminal map. */
    WHERE_IS(true),
    HELP(true),
    /** "Hi", "Namaste": a friendly hello and what Ask can do. */
    GREETING(true),
    /** "Thank you". */
    THANKS(true),
    /** "My bag is damaged": what to do at the airport and the complaint time limit (from baggage-facts.json). */
    BAG_DAMAGED(true),
    /** "My bag didn't arrive" / "lost my bag": report it before leaving the baggage hall, then the time limits. */
    BAG_NOT_ARRIVED(true),
    /** "Something was stolen from my bag". */
    BAG_PILFERED(true),
    /** "I lost my headphones", "Where is lost and found?": CSMIA Lost &amp; Found (from baggage-facts.json). */
    LOST_ITEM(true),
    /** "I need a wheelchair": OnBoard can't book it; who can, and the nearest help desk. */
    ASSISTANCE(true),
    /** "Can I carry a power bank?": no checked rules on the phone yet, so it says so instead of guessing. */
    ITEM_RULES(true),
    /** "What is my baggage allowance?": not saved on the phone; where to find it. */
    BAG_ALLOWANCE(true),
    /** "How do I get to the airport?", "When should I leave?": the flight time and the directions on Home. */
    GETTING_THERE(true),

    /** "Show the best flights to London tomorrow": needs a flight search (online). */
    SEARCH_FLIGHTS(false),
    /** "Find me a hotel near the airport tonight": needs a hotel search (online). */
    SEARCH_HOTELS(false),
    /** "Get me a cab to my hotel": handed to Uber. */
    CAB(false),

    /** Nothing matched. */
    UNKNOWN(false);

    /** True for the intents the phone answers by itself from saved data. */
    public final boolean quickAnswer;

    AskIntent(boolean quickAnswer) {
        this.quickAnswer = quickAnswer;
    }
}
