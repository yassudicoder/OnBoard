package com.safarsaathi.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.util.Objects;

/**
 * A travel document saved on the phone for the offline locker (Wallet tab): for now, boarding passes.
 * Room table "documents"; phone-only (the server never sees it). Mirrors CLAUDE.md's Document.
 *
 * <p>Only the barcode's text is stored. The screen reads it again with BcbpParser each time, so the stored data
 * can never disagree with what the barcode said.
 */
@Entity(tableName = "documents", indices = {@Index("tripId")})
public class DocumentEntity {

    public static final String KIND_BOARDING_PASS = "BOARDING_PASS";

    /** Room hands out the ids: 1, 2, 3, ... */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** The trip the document belongs to (TripEntity.id). */
    public long tripId;

    /** BOARDING_PASS (TICKET, ID and HOTEL_VOUCHER later). */
    @NonNull
    public String kind = "";

    /** The barcode's text, exactly as scanned or pasted. */
    @NonNull
    public String rawData = "";

    /** When it was saved on the phone. */
    @NonNull
    public Instant addedAt = Instant.EPOCH;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DocumentEntity)) {
            return false;
        }
        DocumentEntity that = (DocumentEntity) other;
        return id == that.id && tripId == that.tripId && kind.equals(that.kind) && rawData.equals(that.rawData)
                && addedAt.equals(that.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tripId, kind, rawData, addedAt);
    }
}
