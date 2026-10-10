package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.DocType;
import com.safarsaathi.app.data.vault.Entries;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One travel document, stored ONCE even when several trips use it (a passport is linked to every trip through
 * {@link TripDocumentLinkEntity}). Its scanned pages or PDF are {@link VaultFileEntity} rows, one per page, each
 * with its own SHA-256 fingerprint.
 *
 * <p>Which fields are used depends on the type: a passport has a number, nationality and expiry; a ticket a
 * flight number and booking reference; a hotel booking or an insurance uses validFrom/validTo for the days it
 * covers. Anything not printed on the document stays null; the app never guesses.
 */
@Entity(tableName = "documents", indices = {@Index("travellerId")})
public class TravelDocumentEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    /** Whose document it is; null for e.g. a group hotel booking. */
    @Nullable
    public String travellerId;

    @NonNull
    public DocType type = DocType.OTHER;

    /** Short name in the folder, e.g. "Passport" or "E-ticket Delhi → Kigali". */
    @NonNull
    public String title = "";

    /** Name exactly as printed on the document (the V4 checks compare these across documents). */
    @Nullable
    public String holderName;

    /**
     * Document number (passport, visa, policy, ...), encrypted a second time with the Keystore key
     * (SecretText, label "doc-number:" + id). Screens show it masked. Null when there is none.
     */
    @Nullable
    public String numberEncrypted;

    /** 2-letter code of the country that issued it. */
    @Nullable
    public String issuingCountry;

    @Nullable
    public LocalDate issueDate;

    @Nullable
    public LocalDate expiryDate;

    /**
     * First and last day the document covers (visa validity, hotel nights, insurance cover). For a ticket or
     * boarding pass both are the day of the flight.
     */
    @Nullable
    public LocalDate validFrom;

    @Nullable
    public LocalDate validTo;

    /** Countries a visa or entry document is for, as comma-separated 2-letter codes, e.g. "CD" or "RW,UG,KE". */
    @Nullable
    public String coversCountries;

    @Nullable
    public Entries entries;

    /** Tickets and boarding passes, e.g. "ZZ 101". */
    @Nullable
    public String flightNo;

    /** Booking reference / PNR / hotel confirmation number. */
    @Nullable
    public String bookingRef;

    @NonNull
    public DocSource source = DocSource.MANUAL;

    /**
     * True once the traveller has checked the fields against the paper document. Fields read by the camera (V2)
     * start false and are always shown to the traveller to confirm.
     */
    public boolean fieldsConfirmed;

    /** True for the made-up demo documents: shown with a SAMPLE label and never presented as real. */
    public boolean sample;

    @NonNull
    public Instant createdAt = Instant.EPOCH;
}
