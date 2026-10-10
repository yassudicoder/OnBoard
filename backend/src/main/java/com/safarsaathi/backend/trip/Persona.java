package com.safarsaathi.backend.trip;

/** Who is travelling (feature F10). Changes the app's hints, text size and tone. Saved by name, e.g. "ELDERLY". */
public enum Persona {
    FIRST_TIME,
    FAMILY,
    BUSINESS,
    /** Elderly traveller or someone who needs help: larger text, simpler steps. */
    ELDERLY
}
