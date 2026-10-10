package com.safarsaathi.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Reads and writes the phone-only Wallet and Profile data: boarding passes, ID documents and saved cards.
 * LiveData methods are for screens; the others block, so call them on a background thread (WalletRepository does).
 */
@Dao
public abstract class WalletDao {

    /** Newest first. */
    @Query("SELECT * FROM documents WHERE tripId = :tripId AND kind = :kind ORDER BY addedAt DESC, id DESC")
    public abstract LiveData<List<DocumentEntity>> observeDocuments(long tripId, String kind);

    @Insert
    public abstract long insertDocument(DocumentEntity document);

    @Query("DELETE FROM documents WHERE id = :id")
    public abstract void deleteDocument(long id);

    /** Oldest first, the order they were added. */
    @Query("SELECT * FROM saved_cards ORDER BY addedAt, id")
    public abstract LiveData<List<SavedCardEntity>> observeCards();

    @Insert
    public abstract long insertCard(SavedCardEntity card);

    @Query("DELETE FROM saved_cards WHERE id = :id")
    public abstract void deleteCard(long id);

    /** Aadhaar, then PAN, then visas; oldest first within each. */
    @Query("SELECT * FROM id_documents "
            + "ORDER BY CASE kind WHEN 'AADHAAR' THEN 0 WHEN 'PAN' THEN 1 ELSE 2 END, addedAt, id")
    public abstract LiveData<List<IdDocumentEntity>> observeIdDocuments();

    @Insert
    public abstract long insertIdDocument(IdDocumentEntity document);

    @Query("DELETE FROM id_documents WHERE id = :id")
    public abstract void deleteIdDocument(long id);
}
