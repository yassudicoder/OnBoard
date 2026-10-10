package com.safarsaathi.recovery.policy;

/**
 * One way to reach an airline: a web page, an email address, a phone number or "the transfer desk at the airport".
 * Fictional airlines only get {@code .example} web and email addresses (a reserved domain that never reaches anyone)
 * and no phone numbers, so OnBoard never sends a traveller to a real stranger.
 */
public final class ContactChannel {

    /** WEB, EMAIL, PHONE or DESK. */
    public String type;
    /** What the app shows, e.g. "Rebooking by email (fictional airline)". */
    public String label;
    /** The address or number; null for DESK (no invented desk locations). */
    public String value;
}
