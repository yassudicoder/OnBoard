package com.safarsaathi.app.data.vault.capture;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.DocType;
import com.safarsaathi.app.data.vault.DocumentNumbers;
import com.safarsaathi.app.data.vault.Entries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A document that is being added: the fields on the confirm screen before they are saved. Filled in by
 * {@link DraftReader} (from the MRZ or a boarding pass barcode) or typed in by the traveller, then checked with
 * {@link #problems()}. Plain Java, unit-tested.
 */
public final class DocumentDraft {

    /** Something the traveller must fix before saving. */
    public enum Problem {
        TYPE_MISSING,
        TITLE_MISSING,
        ISSUING_COUNTRY_NOT_A_COUNTRY,
        COVERED_COUNTRY_NOT_A_COUNTRY,
        EXPIRY_BEFORE_ISSUE,
        VALID_TO_BEFORE_VALID_FROM
    }

    @Nullable
    public DocType type;
    @Nullable
    public String title;
    @Nullable
    public String holderName;
    @Nullable
    public String number;
    /** 2-letter country code. */
    @Nullable
    public String issuingCountry;
    @Nullable
    public LocalDate issueDate;
    @Nullable
    public LocalDate expiryDate;
    @Nullable
    public LocalDate validFrom;
    @Nullable
    public LocalDate validTo;
    /** 2-letter codes separated by commas, e.g. "CD" or "RW,UG". */
    @Nullable
    public String coversCountries;
    @Nullable
    public Entries entries;
    @Nullable
    public String flightNo;
    @Nullable
    public String bookingRef;
    public DocSource source = DocSource.MANUAL;

    /** Helps the screen suggest a title: a country code ("IN") or a route ("DEL → KGL"). */
    @Nullable
    public String titleHint;
    /** From a passport's MRZ, to suggest who the document belongs to. */
    @Nullable
    public String nationality;
    @Nullable
    public LocalDate birthDate;

    /**
     * The check digit printed after the number in the MRZ (null when the number was not read from an MRZ). It
     * cannot say WHICH character of a misread number is wrong (any change inside the number shifts the overall check
     * digit in exactly the same way), so the app never "repairs" a number by itself; it only checks the traveller's
     * own correction with {@link #numberMatchesCheckDigit}.
     */
    @Nullable
    public Character numberCheckDigit;

    /** True when any field was filled in by the phone: the traveller must then confirm before saving. */
    public boolean readAutomatically;
    public final List<ReadNote> notes = new ArrayList<>();

    /** What must be fixed before saving; empty = ready. */
    public Set<Problem> problems() {
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        if (type == null) {
            problems.add(Problem.TYPE_MISSING);
        }
        if (isBlank(title)) {
            problems.add(Problem.TITLE_MISSING);
        }
        if (issuingCountry != null && !CountryCodes.isCountry(issuingCountry)) {
            problems.add(Problem.ISSUING_COUNTRY_NOT_A_COUNTRY);
        }
        if (coversCountries != null && normaliseCountries(coversCountries) == null) {
            problems.add(Problem.COVERED_COUNTRY_NOT_A_COUNTRY);
        }
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate)) {
            problems.add(Problem.EXPIRY_BEFORE_ISSUE);
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            problems.add(Problem.VALID_TO_BEFORE_VALID_FROM);
        }
        return problems;
    }

    /**
     * Whether {@code typed} agrees with the printed check digit: true or false, or null when there is no check digit
     * to compare with (not from an MRZ, or a number longer than the 9 characters the check covers).
     */
    @Nullable
    public Boolean numberMatchesCheckDigit(@Nullable String typed) {
        String cleaned = DocumentNumbers.normalise(typed);
        if (numberCheckDigit == null || numberCheckDigit == '<' || cleaned == null || cleaned.length() > 9) {
            return null;
        }
        StringBuilder field = new StringBuilder(cleaned);
        while (field.length() < 9) {
            field.append('<');
        }
        return MrzCheckDigit.matches(field.toString(), numberCheckDigit);
    }

    /** The number as it will be saved: capitals, no spaces (null when empty). */
    @Nullable
    public String cleanNumber() {
        return DocumentNumbers.normalise(number);
    }

    /**
     * "cd, rw ug" becomes "CD,RW,UG". Null when the text holds a code that is not a country, or nothing at all.
     */
    @Nullable
    public static String normaliseCountries(@Nullable String text) {
        if (text == null) {
            return null;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String part : text.toUpperCase(Locale.ROOT).split("[,;\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!CountryCodes.isCountry(part)) {
                return null;
            }
            codes.add(part);
        }
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    private static boolean isBlank(@Nullable String text) {
        return text == null || text.trim().isEmpty();
    }
}
