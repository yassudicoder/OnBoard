package com.safarsaathi.app.data.vault.capture;

/**
 * The check digit of the machine-readable zone (MRZ), as defined in ICAO Doc 9303 Part 3: every character gets a
 * value (0-9 = 0-9, A-Z = 10-35, the filler "<" = 0), the values are multiplied by 7, 3, 1, 7, 3, 1, ... and the
 * last digit of the sum is the check digit. One wrong character almost always changes it, which is how the app
 * notices a camera misreading.
 */
public final class MrzCheckDigit {

    private static final int[] WEIGHTS = {7, 3, 1};

    private MrzCheckDigit() {
    }

    /** The check digit (0-9) of {@code field}. */
    public static int of(String field) {
        int sum = 0;
        for (int i = 0; i < field.length(); i++) {
            sum += value(field.charAt(i)) * WEIGHTS[i % 3];
        }
        return sum % 10;
    }

    /**
     * True when {@code digit} is the check digit of {@code field}. ICAO allows a filler "<" instead of 0 when the
     * field is all fillers (e.g. an empty personal-number field).
     */
    public static boolean matches(String field, char digit) {
        if (digit == '<') {
            return field.chars().allMatch(c -> c == '<');
        }
        return Character.isDigit(digit) && of(field) == digit - '0';
    }

    private static int value(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        return 0; // "<" (and anything unreadable) counts as 0
    }
}
