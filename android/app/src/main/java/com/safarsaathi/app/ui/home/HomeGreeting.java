package com.safarsaathi.app.ui.home;

import java.time.LocalTime;

/**
 * Which greeting the Home tab shows ("Good morning / afternoon / evening, Aditi"), from the time in India.
 * Plain Java, checked by HomeGreetingTest.
 */
public enum HomeGreeting {
    MORNING, AFTERNOON, EVENING;

    private static final LocalTime NOON = LocalTime.NOON;
    private static final LocalTime FIVE_AM = LocalTime.of(5, 0);
    private static final LocalTime FIVE_PM = LocalTime.of(17, 0);

    /** 05:00-11:59 morning, 12:00-16:59 afternoon, otherwise (17:00-04:59) evening. */
    public static HomeGreeting at(LocalTime time) {
        if (!time.isBefore(FIVE_AM) && time.isBefore(NOON)) {
            return MORNING;
        }
        if (!time.isBefore(NOON) && time.isBefore(FIVE_PM)) {
            return AFTERNOON;
        }
        return EVENING;
    }

    /** The first letter of a name, as a capital, for the round avatar ("Aditi" -> "A"). Empty for an empty name. */
    public static String initial(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return new String(Character.toChars(Character.toUpperCase(trimmed.codePointAt(0))));
    }
}
