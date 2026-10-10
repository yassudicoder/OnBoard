package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Puts a document into a trip folder, optionally under one leg. The same document (e.g. the passport) can be
 * linked to many trips, so it is stored only once. legId = null means "for the whole trip".
 */
@Entity(tableName = "trip_document_links",
        foreignKeys = {
                @ForeignKey(entity = TripVaultEntity.class, parentColumns = "id", childColumns = "tripId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TravelDocumentEntity.class, parentColumns = "id", childColumns = "documentId",
                        onDelete = ForeignKey.CASCADE),
                // Deleting a leg keeps its documents in the trip, as "whole trip" documents.
                @ForeignKey(entity = TripLegEntity.class, parentColumns = "id", childColumns = "legId",
                        onDelete = ForeignKey.SET_NULL)},
        indices = {@Index("tripId"), @Index("documentId"), @Index("legId")})
public class TripDocumentLinkEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String tripId = "";

    @NonNull
    public String documentId = "";

    @Nullable
    public String legId;
}
