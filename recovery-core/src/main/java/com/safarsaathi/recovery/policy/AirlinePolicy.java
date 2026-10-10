package com.safarsaathi.recovery.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * What one airline says it provides when its flight is delayed or cancelled, and how to reach it: one entry of
 * mock-data/recovery/airline-policies.json (the "AirlinePolicyPack" of the brief).
 */
public final class AirlinePolicy {

    /** e.g. "MJ". */
    public String airlineCode;
    public String name;
    /** true = an invented demo airline: everything about it is SAMPLE. */
    public boolean fictional;
    /** OFFICIAL (copied from the airline's own page: sourceUrl + lastChecked) or SAMPLE (invented for the demo). */
    public String kind;
    /**
     * Meals, hotel and transport the airline says it provides during long delays, in its own words. null = not known:
     * the app then says "Ask the airline whether meals, a hotel or transport are provided under your ticket."
     */
    public String delayAssistanceText;
    public List<ContactChannel> contactChannels = new ArrayList<>();
    /** The airline's official page; null for a fictional airline. */
    public String sourceUrl;
    /** yyyy-MM-dd */
    public String lastChecked;
    public String notes;
}
