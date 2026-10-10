package com.safarsaathi.app.data.wallet;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for the ID documents in the Wallet (Aadhaar, PAN, visa). Plain Java, checked by IdDocumentsTest.
 *
 * <p>Privacy rules (these IDs are sensitive):
 * <ul>
 *   <li><b>Aadhaar</b>: only the LAST 4 digits are ever kept, like UIDAI's "masked Aadhaar" (XXXX XXXX 1234).
 *       The app never asks for, stores or prints the full 12-digit number.</li>
 *   <li><b>PAN</b>: kept on the phone so the traveller can read it offline, but always SHOWN masked
 *       (XXXXX1234F), on screen and in the PDF.</li>
 *   <li>Everything stays on the phone: it is never sent to the backend, and the database is left out of cloud
 *       backups (res/xml/data_extraction_rules.xml).</li>
 * </ul>
 */
public final class IdDocuments {

    public static final String KIND_AADHAAR = "AADHAAR";
    public static final String KIND_PAN = "PAN";
    public static final String KIND_VISA = "VISA";

    public static final String ENTRIES_SINGLE = "SINGLE";
    public static final String ENTRIES_MULTIPLE = "MULTIPLE";

    /** A visa that ends within this many days gets an "expires soon" warning. */
    public static final int EXPIRES_SOON_DAYS = 30;

    /** PAN: 5 letters, 4 digits, 1 letter, e.g. ABCDE1234F. */
    private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");

    /** How the traveller types dates: 31/12/2027. Strict, so 31/02/2027 is refused. */
    public static final DateTimeFormatter INPUT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private IdDocuments() {
    }

    /** True for exactly 4 digits: the only part of an Aadhaar number the app keeps. */
    public static boolean isValidAadhaarLast4(@Nullable String text) {
        return text != null && text.length() == 4 && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** The PAN in capitals without spaces, e.g. " abcde 1234 f" -> "ABCDE1234F". */
    public static String normalisePan(@Nullable String text) {
        return text == null ? "" : text.replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    /** True for a well-formed PAN (after {@link #normalisePan}). */
    public static boolean isValidPan(@Nullable String pan) {
        return pan != null && PAN.matcher(pan).matches();
    }

    /** "XXXX XXXX 1234" */
    public static String maskAadhaar(String last4) {
        return "XXXX XXXX " + last4;
    }

    /** "ABCDE1234F" -> "XXXXX1234F": only the digits and the last letter stay readable. */
    public static String maskPan(String pan) {
        if (pan == null || pan.length() != 10) {
            return "XXXXXXXXXX";
        }
        return "XXXXX" + pan.substring(5);
    }

    /** Reads "31/12/2027"; null when it is not a real date in that format. */
    @Nullable
    public static LocalDate parseDate(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), INPUT_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Where a visa stands today. */
    public enum VisaStatus { NOT_YET_VALID, VALID, EXPIRES_SOON, EXPIRED }

    public static VisaStatus visaStatus(LocalDate validFrom, LocalDate validUntil, LocalDate today) {
        if (today.isAfter(validUntil)) {
            return VisaStatus.EXPIRED;
        }
        if (today.isBefore(validFrom)) {
            return VisaStatus.NOT_YET_VALID;
        }
        return ChronoUnit.DAYS.between(today, validUntil) <= EXPIRES_SOON_DAYS
                ? VisaStatus.EXPIRES_SOON : VisaStatus.VALID;
    }

    /** Days from today until the visa ends (0 = ends today). */
    public static long daysLeft(LocalDate validUntil, LocalDate today) {
        return ChronoUnit.DAYS.between(today, validUntil);
    }
}
