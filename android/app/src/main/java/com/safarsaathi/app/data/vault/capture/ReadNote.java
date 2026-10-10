package com.safarsaathi.app.data.vault.capture;

import androidx.annotation.Nullable;

/**
 * One line for the confirm screen about what the phone read from a document, and what the traveller must check.
 * Kinds, not sentences: the screen turns them into words in the traveller's language.
 */
public final class ReadNote {

    public enum Kind {
        /** The machine-readable zone was read. */
        MRZ_READ,
        /** A check digit did not match: that field is probably misread ({@link #field} says which). */
        MRZ_CHECK_FAILED,
        /** The boarding pass barcode was read. */
        BARCODE_READ,
        /** A printed country code is not a country the phone knows ({@link #detail} = the code). */
        COUNTRY_NOT_RECOGNISED,
        /** A visa was read: which countries it covers was guessed from its issuing country. */
        CHECK_COVERED_COUNTRIES,
        /** Nothing could be read automatically; the traveller types the details. */
        NOTHING_READ
    }

    /** Which MRZ field a failed check digit belongs to. */
    public enum Field {
        NUMBER, BIRTH_DATE, EXPIRY_DATE, PERSONAL_NUMBER, WHOLE_ZONE
    }

    public final Kind kind;
    @Nullable
    public final Field field;
    @Nullable
    public final String detail;

    public ReadNote(Kind kind, @Nullable Field field, @Nullable String detail) {
        this.kind = kind;
        this.field = field;
        this.detail = detail;
    }

    /** True for notes that ask the traveller to look carefully (shown with a warning icon). */
    public boolean isWarning() {
        return kind == Kind.MRZ_CHECK_FAILED || kind == Kind.COUNTRY_NOT_RECOGNISED
                || kind == Kind.CHECK_COVERED_COUNTRIES;
    }
}
