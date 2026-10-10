package com.safarsaathi.app.data.vault;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * What other parts of the app (Ask OnBoard, Ride hand-off) may know about one saved document. Built ONLY by
 * {@link VaultSummaries}; see {@link VaultSummaryReader} for the rules.
 *
 * <p>Deliberately NOT here, ever: the document number, MRZ lines, page images, file paths or ids of files, the full
 * name, the date of birth. The unit test VaultSummariesTest checks that no such field exists and no such value leaks.
 *
 * <p>While the vault is locked ({@link #lockedView} = true) only hotel bookings are returned, and only with
 * {@link #hotelName}, {@link #hotelAddress} and {@link #sample}; every other field is null or empty.
 */
public final class DocumentSummary {

    /** Opaque id, only useful to open this document inside the vault (which asks to unlock first). Null when locked. */
    @Nullable
    public final String documentId;
    public final DocType type;
    /** First word of the name printed on the document, e.g. "Paramvir". */
    @Nullable
    public final String holderFirstName;
    @Nullable
    public final LocalDate expiryDate;
    /** The days the document covers (a visa's validity, hotel nights, insurance cover; a ticket: the flight day). */
    @Nullable
    public final LocalDate coveredFrom;
    @Nullable
    public final LocalDate coveredTo;
    /** Countries a visa or entry document is for, 2-letter codes (e.g. "CD"). */
    public final List<String> coveredCountries;
    /** Null while locked. */
    @Nullable
    public final SummaryStatus status;
    /** True for the made-up demo documents: callers must label them SAMPLE. */
    public final boolean sample;
    /** Hotel bookings only: the hotel's name and address as typed from the booking. */
    @Nullable
    public final String hotelName;
    @Nullable
    public final String hotelAddress;
    /** True when the vault was locked: only the hotel fields (and {@link #sample}) are filled in. */
    public final boolean lockedView;

    DocumentSummary(@Nullable String documentId, DocType type, @Nullable String holderFirstName,
                    @Nullable LocalDate expiryDate, @Nullable LocalDate coveredFrom, @Nullable LocalDate coveredTo,
                    List<String> coveredCountries, @Nullable SummaryStatus status, boolean sample,
                    @Nullable String hotelName, @Nullable String hotelAddress, boolean lockedView) {
        this.documentId = documentId;
        this.type = type;
        this.holderFirstName = holderFirstName;
        this.expiryDate = expiryDate;
        this.coveredFrom = coveredFrom;
        this.coveredTo = coveredTo;
        this.coveredCountries = Collections.unmodifiableList(coveredCountries);
        this.status = status;
        this.sample = sample;
        this.hotelName = hotelName;
        this.hotelAddress = hotelAddress;
        this.lockedView = lockedView;
    }
}
