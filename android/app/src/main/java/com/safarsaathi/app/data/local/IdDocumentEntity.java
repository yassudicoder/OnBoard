package com.safarsaathi.app.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.util.Objects;

/**
 * An ID document the traveller saved in the Wallet: Aadhaar, PAN or a visa. Room table "id_documents";
 * phone-only, never sent to the server. Rules and masking: com.safarsaathi.app.data.wallet.IdDocuments.
 *
 * <p>For Aadhaar, {@link #number} holds ONLY the last 4 digits. For PAN it holds the 10-character PAN (always
 * shown masked). For a visa it holds the visa number.
 */
@Entity(tableName = "id_documents")
public class IdDocumentEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** AADHAAR, PAN or VISA. */
    @NonNull
    public String kind = "";

    /** Name as printed on the document. */
    @NonNull
    public String holderName = "";

    /** Aadhaar: last 4 digits only. PAN: the PAN. Visa: the visa number. */
    @NonNull
    public String number = "";

    /** Visa only: issuing country, e.g. "United Arab Emirates". */
    @Nullable
    public String country;

    /** Visa only: e.g. "Tourist" or "Business". */
    @Nullable
    public String visaType;

    /** Visa only: first and last valid day, as "2026-10-01" (ISO date). */
    @Nullable
    public String validFrom;

    @Nullable
    public String validUntil;

    /** Visa only: SINGLE or MULTIPLE entry. */
    @Nullable
    public String entries;

    @NonNull
    public Instant addedAt = Instant.EPOCH;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdDocumentEntity)) {
            return false;
        }
        IdDocumentEntity that = (IdDocumentEntity) other;
        return id == that.id && kind.equals(that.kind) && holderName.equals(that.holderName)
                && number.equals(that.number) && Objects.equals(country, that.country)
                && Objects.equals(visaType, that.visaType) && Objects.equals(validFrom, that.validFrom)
                && Objects.equals(validUntil, that.validUntil) && Objects.equals(entries, that.entries)
                && addedAt.equals(that.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, holderName, number, country, visaType, validFrom, validUntil, entries, addedAt);
    }
}
