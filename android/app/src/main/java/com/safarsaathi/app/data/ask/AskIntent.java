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
    DOCUMENT_EXPIRY(true),
    /** Washrooms, help desks and lifts on the terminal map. */
    WHERE_IS(true),
    HELP(true),

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
