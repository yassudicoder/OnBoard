package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Embedded;

/** A document as it sits in one trip folder: the document plus the leg it is filed under (null = whole trip). */
public class TripDocumentRow {

    @Embedded
    @NonNull
    public TravelDocumentEntity document = new TravelDocumentEntity();

    @Nullable
    public String legId;
}
