package com.safarsaathi.app.data.vault.capture;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * What {@link MrzParser} read from a machine-readable zone. Everything is exactly as printed (country codes are the
 * 3-letter ICAO codes, e.g. "IND"); fields that were empty are null. The check results say which fields passed their
 * check digit; the confirm screen warns about every one that did not.
 */
public final class MrzResult {

    /** The ICAO 9303 layouts: TD3 = passport, TD1/TD2 = ID cards, MRV-A/MRV-B = visas. */
    public enum Format {
        TD1, TD2, TD3, MRV_A, MRV_B
    }

    public final Format format;
    /** First 1-2 characters, e.g. "P" (passport), "V" (visa), "I", "ID", "AC". */
    public final String documentCode;
    /** The issuing state, 3-letter ICAO code as printed ("IND", or "D" for Germany). */
    public final String issuingState;
    @Nullable
    public final String surname;
    @Nullable
    public final String givenNames;
    @Nullable
    public final String documentNumber;
    @Nullable
    public final String nationality;
    /** Null when the date is not printed or not a real date. */
    @Nullable
    public final LocalDate birthDate;
    /** Expiry date; for a visa, the last day it is valid. */
    @Nullable
    public final LocalDate expiryDate;
    /** "M", "F", "X" or null (not specified). */
    @Nullable
    public final String sex;
    @Nullable
    public final String optionalData;

    public final boolean numberCheckOk;
    public final boolean birthDateCheckOk;
    public final boolean expiryCheckOk;
    /** The check over several fields together; true for visas, which have none. */
    public final boolean compositeCheckOk;
    /** The personal-number check of a passport; true where the layout has none. */
    public final boolean optionalCheckOk;
    /**
     * The check digit printed after the document number ('0'-'9'), or '<' when there is none to use (a TD1 number
     * longer than 9 characters). It lets the confirm screen check a number the traveller corrects by hand.
     */
    public final char numberCheckDigit;

    /** The cleaned-up lines that were parsed. */
    public final List<String> lines;

    MrzResult(Format format, String documentCode, String issuingState, @Nullable String surname,
              @Nullable String givenNames, @Nullable String documentNumber, @Nullable String nationality,
              @Nullable LocalDate birthDate, @Nullable LocalDate expiryDate, @Nullable String sex,
              @Nullable String optionalData, boolean numberCheckOk, boolean birthDateCheckOk, boolean expiryCheckOk,
              boolean compositeCheckOk, boolean optionalCheckOk, char numberCheckDigit, List<String> lines) {
        this.format = format;
        this.documentCode = documentCode;
        this.issuingState = issuingState;
        this.surname = surname;
        this.givenNames = givenNames;
        this.documentNumber = documentNumber;
        this.nationality = nationality;
        this.birthDate = birthDate;
        this.expiryDate = expiryDate;
        this.sex = sex;
        this.optionalData = optionalData;
        this.numberCheckOk = numberCheckOk;
        this.birthDateCheckOk = birthDateCheckOk;
        this.expiryCheckOk = expiryCheckOk;
        this.compositeCheckOk = compositeCheckOk;
        this.optionalCheckOk = optionalCheckOk;
        this.numberCheckDigit = numberCheckDigit;
        this.lines = Collections.unmodifiableList(lines);
    }

    public boolean allChecksOk() {
        return numberCheckOk && birthDateCheckOk && expiryCheckOk && compositeCheckOk && optionalCheckOk;
    }

    public boolean isPassport() {
        return format == Format.TD3 && documentCode.startsWith("P");
    }

    public boolean isVisa() {
        return format == Format.MRV_A || format == Format.MRV_B;
    }

    /** "PARAMVIR SINGH": given names first, as most screens and tickets print it. */
    @Nullable
    public String fullName() {
        if (surname == null && givenNames == null) {
            return null;
        }
        if (givenNames == null) {
            return surname;
        }
        return surname == null ? givenNames : givenNames + " " + surname;
    }
}
