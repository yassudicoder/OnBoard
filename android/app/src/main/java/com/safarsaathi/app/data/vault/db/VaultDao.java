package com.safarsaathi.app.data.vault.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Every read and write of the vault database. Room writes the code. Methods returning LiveData run their query in
 * the background and tell the screen again whenever the data changes; the others must be called from a
 * background thread (VaultRepository does that).
 */
@Dao
public interface VaultDao {

    // ----- Reading (screens) -----

    /** Trip folders, earliest trip first, each with its number of documents (a document counts once). */
    @Query("SELECT t.*, (SELECT COUNT(DISTINCT l.documentId) FROM trip_document_links l WHERE l.tripId = t.id)"
            + " AS documentCount FROM trips t ORDER BY t.startDate IS NULL, t.startDate, t.createdAt")
    LiveData<List<TripFolderRow>> observeTripFolders();

    @Query("SELECT * FROM trips WHERE id = :tripId")
    LiveData<TripVaultEntity> observeTrip(String tripId);

    @Query("SELECT * FROM legs WHERE tripId = :tripId ORDER BY position")
    LiveData<List<TripLegEntity>> observeLegs(String tripId);

    /** The trip's documents, each with the leg it is filed under. */
    @Query("SELECT d.*, l.legId AS legId FROM trip_document_links l JOIN documents d ON d.id = l.documentId"
            + " WHERE l.tripId = :tripId ORDER BY d.title")
    LiveData<List<TripDocumentRow>> observeTripDocuments(String tripId);

    @Query("SELECT * FROM documents WHERE id = :documentId")
    LiveData<TravelDocumentEntity> observeDocument(String documentId);

    @Query("SELECT * FROM document_files WHERE documentId = :documentId ORDER BY page")
    LiveData<List<VaultFileEntity>> observeFiles(String documentId);

    /** Number of trips that use this document (a passport is usually in several). */
    @Query("SELECT COUNT(DISTINCT tripId) FROM trip_document_links WHERE documentId = :documentId")
    LiveData<Integer> observeTripCountOf(String documentId);

    @Query("SELECT COUNT(*) FROM trips WHERE sample = 1")
    LiveData<Integer> observeSampleTripCount();

    /** Documents saved for OTHER trips that this trip does not use yet ("add from your other trips"). */
    @Query("SELECT * FROM documents WHERE id NOT IN"
            + " (SELECT documentId FROM trip_document_links WHERE tripId = :tripId) ORDER BY type, title")
    LiveData<List<TravelDocumentEntity>> observeDocumentsNotIn(String tripId);

    /** The people on a trip. */
    @Query("SELECT t.* FROM travellers t JOIN trip_travellers tt ON tt.travellerId = t.id"
            + " WHERE tt.tripId = :tripId ORDER BY t.name")
    LiveData<List<TravellerEntity>> observeTravellersOf(String tripId);

    /** Everyone in the vault (a new trip can reuse a traveller from another trip). */
    @Query("SELECT * FROM travellers ORDER BY name")
    LiveData<List<TravellerEntity>> observeTravellers();

    // ----- Reading (background thread only) -----

    @Query("SELECT * FROM document_files WHERE documentId = :documentId ORDER BY page")
    List<VaultFileEntity> filesOf(String documentId);

    @Query("SELECT * FROM document_files ORDER BY documentId, page")
    List<VaultFileEntity> allFiles();

    @Query("SELECT COUNT(*) FROM trips WHERE sample = 1")
    int sampleTripCount();

    @Query("SELECT * FROM documents WHERE id = :documentId")
    TravelDocumentEntity document(String documentId);

    @Query("SELECT d.*, l.legId AS legId FROM trip_document_links l JOIN documents d ON d.id = l.documentId"
            + " WHERE l.tripId = :tripId")
    List<TripDocumentRow> tripDocuments(String tripId);

    @Query("SELECT * FROM trips WHERE id = :tripId")
    TripVaultEntity trip(String tripId);

    @Query("SELECT * FROM trips")
    List<TripVaultEntity> trips();

    @Query("SELECT * FROM travellers WHERE id = :travellerId")
    TravellerEntity traveller(String travellerId);

    @Query("SELECT COUNT(*) FROM trip_document_links WHERE tripId = :tripId AND documentId = :documentId")
    int linkCount(String tripId, String documentId);

    /** Documents of this trip that no other trip uses: deleting the trip deletes these, shared ones stay. */
    @Query("SELECT DISTINCT documentId FROM trip_document_links WHERE tripId = :tripId"
            + " AND documentId NOT IN (SELECT documentId FROM trip_document_links WHERE tripId != :tripId)")
    List<String> documentsOnlyIn(String tripId);

    // ----- Writing (background thread only) -----

    @Insert
    void insertTraveller(TravellerEntity traveller);

    @Insert
    void insertTrip(TripVaultEntity trip);

    @Insert
    void insertTripTravellers(List<TripTravellerEntity> tripTravellers);

    /** Adds a traveller to a trip; does nothing when they are on it already. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addTravellerToTrip(TripTravellerEntity tripTraveller);

    @Insert
    void insertLegs(List<TripLegEntity> legs);

    @Insert
    void insertCountries(List<TripCountryEntity> countries);

    @Insert
    void insertDocument(TravelDocumentEntity document);

    @Insert
    void insertFile(VaultFileEntity file);

    @Insert
    void insertLinks(List<TripDocumentLinkEntity> links);

    /** Also deletes the trip's legs, countries, traveller list and links (ON DELETE CASCADE). */
    @Query("DELETE FROM trips WHERE id = :tripId")
    void deleteTrip(String tripId);

    /** Also deletes the document's file rows and its links to trips (ON DELETE CASCADE). */
    @Query("DELETE FROM documents WHERE id = :documentId")
    void deleteDocument(String documentId);

    /** The made-up demo traveller, once no trip uses them any more. Real travellers are never removed this way. */
    @Query("DELETE FROM travellers WHERE sample = 1 AND id NOT IN (SELECT travellerId FROM trip_travellers)")
    void deleteUnusedSampleTravellers();
}
