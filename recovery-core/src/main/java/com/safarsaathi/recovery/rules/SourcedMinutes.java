package com.safarsaathi.recovery.rules;

/**
 * A number of minutes together with where it comes from, e.g. "the gate closes 20 minutes before departure (Hamad
 * International Airport, Transfers page, checked 2026-10-11)". The app shows the source next to the number.
 */
public final class SourcedMinutes {

    public int minutes;
    public ValueKind kind;
    /** Where the number comes from, in words, e.g. "DEMO VALUE - not an official figure". */
    public String source;
    /** The official page; null for demo values and estimates. */
    public String sourceUrl;
    /** When a teammate last checked the source, as yyyy-MM-dd. */
    public String lastChecked;

    /** For the JSON readers (Jackson on the backend, Gson in the app). */
    public SourcedMinutes() {
    }

    public SourcedMinutes(int minutes, ValueKind kind, String source, String sourceUrl, String lastChecked) {
        this.minutes = minutes;
        this.kind = kind;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.lastChecked = lastChecked;
    }
}
