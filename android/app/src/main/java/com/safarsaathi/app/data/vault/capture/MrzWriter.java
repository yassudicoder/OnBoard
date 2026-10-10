package com.safarsaathi.app.data.vault.capture;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Writes a passport MRZ (ICAO 9303 TD3, 2 lines of 44) with correct check digits. Only used for the made-up SAMPLE
 * passport page (so scanning it shows the reader working) and in tests; never for a real person's document.
 */
public final class MrzWriter {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);

    private MrzWriter() {
    }

    /**
     * @param issuingState   3-letter ICAO code, e.g. "IND"
     * @param personalNumber may be empty
     */
    public static List<String> passport(String issuingState, String surname, String givenNames, String number,
                                        String nationality, LocalDate birthDate, char sex, LocalDate expiryDate,
                                        String personalNumber) {
        String name = field(surname, 39).replaceAll("<+$", "") + "<<" + field(givenNames, 39);
        String line1 = pad("P<" + pad(issuingState, 3) + name, 44);

        String numberField = pad(field(number, 9), 9);
        String birth = YYMMDD.format(birthDate);
        String expiry = YYMMDD.format(expiryDate);
        String personal = pad(field(personalNumber, 14), 14);
        String withoutComposite = numberField + MrzCheckDigit.of(numberField) + pad(nationality, 3)
                + birth + MrzCheckDigit.of(birth) + sex
                + expiry + MrzCheckDigit.of(expiry)
                + personal + MrzCheckDigit.of(personal);
        String composite = withoutComposite.substring(0, 10) + withoutComposite.substring(13, 20)
                + withoutComposite.substring(21, 43);
        String line2 = withoutComposite + MrzCheckDigit.of(composite);
        return Arrays.asList(line1, line2);
    }

    /** Capitals, spaces and hyphens become fillers, cut to {@code max} characters. */
    private static String field(String text, int max) {
        String cleaned = text.toUpperCase(Locale.ROOT).trim().replaceAll("[\\s-]+", "<").replaceAll("[^A-Z0-9<]", "");
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private static String pad(String text, int length) {
        StringBuilder out = new StringBuilder(text.length() > length ? text.substring(0, length) : text);
        while (out.length() < length) {
            out.append('<');
        }
        return out.toString();
    }
}
