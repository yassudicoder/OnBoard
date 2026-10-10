package com.safarsaathi.app.data.ask;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What Ask OnBoard may know about the traveller's travel documents: a SUMMARY from the Document Vault's read-only
 * reader (VaultSummaryReader, written by the Vault track), never the documents themselves.
 *
 * <p>PRIVACY (user decision, 2026-10-11): only type, holder FIRST name, expiry, covered dates and countries, and a
 * status. This class has no field that could carry a document number, an MRZ line, an image, a file path, a full name
 * or a birth date, so no answer can ever show one (DocumentFactsTest checks the field list). Only Layer 1 (Quick
 * answers) and Layer 2 (on-device AI) may use it; Layer 3 (cloud) never receives it.
 */
public final class DocumentFacts {

    /** Can Ask see the summaries right now? */
    public enum Access {
        /** The vault is unlocked: {@link #docs} is filled. */
        UNLOCKED,
        /** The vault is locked: answers say "Unlock your vault to see this" with an Unlock button. */
        LOCKED,
        /** This phone (or this build) has no Document Vault, or it holds no trip folder. */
        NOT_AVAILABLE
    }

    /**
     * The vault's verdict on one SAVED document (decided by the vault, Ask only words it). MISSING = the saved
     * document's encrypted file is missing on this phone; it does NOT mean "a required document is not saved": which
     * documents a country requires is not known on the phone (needs sourced country packs, vault phase V4).
     */
    public enum Status { READY, EXPIRES_SOON, EXPIRED, MISSING, MISMATCH, NOT_OFFLINE_YET }

    /** One document's summary. */
    public static final class Doc {
        /** The vault's document type name: PASSPORT, VISA, E_VISA, TRANSIT_VISA, INSURANCE, TICKET, HOTEL, ... */
        public final String type;
        /** Holder's first name only; null for a MISSING entry or when unknown. */
        @Nullable
        public final String holderFirstName;
        /** Expiry of a passport or visa; null when the document has none. */
        @Nullable
        public final LocalDate expiry;
        /** First and last covered day (insurance, visa validity, hotel stay); null when not given. */
        @Nullable
        public final LocalDate coveredFrom;
        @Nullable
        public final LocalDate coveredTo;
        /** Countries it is for or covers (ISO 3166 two-letter codes, e.g. "RW"). */
        public final List<String> countries;
        public final Status status;
        /** True for the vault's made-up SAMPLE documents: answers label them. */
        public final boolean sample;

        public Doc(String type, @Nullable String holderFirstName, @Nullable LocalDate expiry,
                   @Nullable LocalDate coveredFrom, @Nullable LocalDate coveredTo, List<String> countries,
                   Status status, boolean sample) {
            this.type = type;
            this.holderFirstName = holderFirstName;
            this.expiry = expiry;
            this.coveredFrom = coveredFrom;
            this.coveredTo = coveredTo;
            this.countries = Collections.unmodifiableList(new ArrayList<>(countries));
            this.status = status;
            this.sample = sample;
        }

        /** The day it stops being valid: the expiry, else the end of the covered dates; null when neither. */
        @Nullable
        public LocalDate lastValidDay() {
            return expiry != null ? expiry : coveredTo;
        }
    }

    public final Access access;
    /** The vault trip folder these documents belong to, e.g. "Delhi → Kinshasa"; null when not known. */
    @Nullable
    public final String tripTitle;
    public final List<Doc> docs;

    private DocumentFacts(Access access, @Nullable String tripTitle, List<Doc> docs) {
        this.access = access;
        this.tripTitle = tripTitle;
        this.docs = Collections.unmodifiableList(new ArrayList<>(docs));
    }

    public static DocumentFacts unlocked(@Nullable String tripTitle, List<Doc> docs) {
        return new DocumentFacts(Access.UNLOCKED, tripTitle, docs);
    }

    public static DocumentFacts locked(@Nullable String tripTitle) {
        return new DocumentFacts(Access.LOCKED, tripTitle, Collections.<Doc>emptyList());
    }

    public static DocumentFacts notAvailable() {
        return new DocumentFacts(Access.NOT_AVAILABLE, null, Collections.<Doc>emptyList());
    }
}
