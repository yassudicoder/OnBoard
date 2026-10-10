package com.safarsaathi.app.data.vault;

import java.util.Locale;

/** How document numbers are cleaned up and hidden on screen. */
public final class DocumentNumbers {

    /** How many characters stay visible at the end: enough to tell two documents apart, not enough to misuse. */
    public static final int VISIBLE_AT_END = 2;

    private static final char HIDDEN = '•';

    private DocumentNumbers() {
    }

    /** "s 000 0001 " becomes "S0000001": capitals, no spaces. Returns null for blank input. */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * "S0000001" becomes "••••••01". Numbers of 4 characters or fewer are hidden completely, because the visible
     * end would be half of the number.
     */
    public static String mask(String number) {
        if (number == null || number.isEmpty()) {
            return "";
        }
        int visible = number.length() > 4 ? VISIBLE_AT_END : 0;
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < number.length() - visible; i++) {
            masked.append(HIDDEN);
        }
        masked.append(number, number.length() - visible, number.length());
        return masked.toString();
    }

    /** The visible end of {@link #mask}, for screen readers ("ending in 0 1"); empty when nothing is visible. */
    public static String visibleEnd(String number) {
        if (number == null || number.length() <= 4) {
            return "";
        }
        return number.substring(number.length() - VISIBLE_AT_END);
    }
}
