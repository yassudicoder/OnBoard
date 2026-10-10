package com.safarsaathi.app.data.vault;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;

import com.safarsaathi.app.data.vault.capture.DocumentDraft;
import com.safarsaathi.app.data.vault.crypto.EncryptedFileStore;
import com.safarsaathi.app.data.vault.crypto.SecretText;
import com.safarsaathi.app.data.vault.crypto.VaultMasterKey;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.TravellerEntity;
import com.safarsaathi.app.data.vault.db.TripDocumentLinkEntity;
import com.safarsaathi.app.data.vault.db.TripDocumentRow;
import com.safarsaathi.app.data.vault.db.TripFolderRow;
import com.safarsaathi.app.data.vault.db.TripLegEntity;
import com.safarsaathi.app.data.vault.db.TripTravellerEntity;
import com.safarsaathi.app.data.vault.db.TripVaultEntity;
import com.safarsaathi.app.data.vault.db.VaultDao;
import com.safarsaathi.app.data.vault.db.VaultDatabase;
import com.safarsaathi.app.data.vault.db.VaultFileEntity;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Everything the vault screens read and write. Nothing here talks to the internet: the vault works the same in
 * airplane mode.
 *
 * <p>Reading the folder lists is LiveData from Room (screens redraw by themselves). Writing, decrypting and
 * checking run on the vault's own background thread, one job after another; the answer comes back on the main
 * thread through a {@link Callback}.
 */
public class VaultRepository {

    /** The answer of a background job, delivered on the main thread. */
    public interface Callback<T> {
        void onDone(T value);

        /** Something went wrong; the default does nothing (the screen shows its own message). */
        default void onError(@NonNull Exception error) {
        }
    }

    /** Draws the page image of a sample document (Android graphics, so the screens supply it). */
    public interface PageRenderer {
        byte[] render(SampleVault.SampleDocument document) throws IOException;
    }

    /** One page or PDF to add to a document. */
    public static final class NewPage {
        public final byte[] content;
        public final String mimeType;

        public NewPage(byte[] content, String mimeType) {
            this.content = content;
            this.mimeType = mimeType;
        }
    }

    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PDF = "application/pdf";

    private final VaultDatabase database;
    private final VaultDao dao;
    private final EncryptedFileStore files;
    private final VaultMasterKey masterKey;
    private final Executor vaultExecutor;
    private final Executor mainExecutor;
    private final Clock clock;

    public VaultRepository(VaultDatabase database, EncryptedFileStore files, VaultMasterKey masterKey,
                           Executor vaultExecutor, Executor mainExecutor, Clock clock) {
        this.database = database;
        this.dao = database.dao();
        this.files = files;
        this.masterKey = masterKey;
        this.vaultExecutor = vaultExecutor;
        this.mainExecutor = mainExecutor;
        this.clock = clock;
    }

    // ============================================================================================
    // Reading (LiveData)
    // ============================================================================================

    public LiveData<List<TripFolderRow>> observeTripFolders() {
        return dao.observeTripFolders();
    }

    public LiveData<TripVaultEntity> observeTrip(String tripId) {
        return dao.observeTrip(tripId);
    }

    public LiveData<List<TripLegEntity>> observeLegs(String tripId) {
        return dao.observeLegs(tripId);
    }

    public LiveData<List<TripDocumentRow>> observeTripDocuments(String tripId) {
        return dao.observeTripDocuments(tripId);
    }

    public LiveData<TravelDocumentEntity> observeDocument(String documentId) {
        return dao.observeDocument(documentId);
    }

    public LiveData<List<VaultFileEntity>> observeFiles(String documentId) {
        return dao.observeFiles(documentId);
    }

    public LiveData<Integer> observeTripCountOf(String documentId) {
        return dao.observeTripCountOf(documentId);
    }

    public LiveData<Integer> observeSampleTripCount() {
        return dao.observeSampleTripCount();
    }

    public LiveData<List<TravelDocumentEntity>> observeDocumentsNotIn(String tripId) {
        return dao.observeDocumentsNotIn(tripId);
    }

    public LiveData<List<TravellerEntity>> observeTravellersOf(String tripId) {
        return dao.observeTravellersOf(tripId);
    }

    public LiveData<List<TravellerEntity>> observeTravellers() {
        return dao.observeTravellers();
    }

    // ============================================================================================
    // Writing
    // ============================================================================================

    /** A new, empty trip folder. Answers with its id. */
    public void createTrip(String title, @Nullable LocalDate startDate, @Nullable LocalDate endDate,
                           Callback<String> callback) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("A trip folder needs a name");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("A trip cannot end before it starts");
        }
        run(callback, () -> {
            TripVaultEntity trip = new TripVaultEntity();
            trip.id = UUID.randomUUID().toString();
            trip.title = title.trim();
            trip.startDate = startDate;
            trip.endDate = endDate;
            trip.createdAt = clock.instant();
            dao.insertTrip(trip);
            return trip.id;
        });
    }

    /**
     * Saves Paramvir's sample trip ({@link SampleVault}): every page image is drawn, encrypted and fingerprinted
     * exactly like a real scan would be. Answers with the new trip's id. Does nothing (answers null) when the sample
     * trip is already there.
     */
    public void loadSample(PageRenderer renderer, Callback<String> callback) {
        run(callback, () -> loadSampleNow(renderer));
    }

    /** {@link #loadSample} on the calling thread (background threads and tests only). */
    @Nullable
    public String loadSampleNow(PageRenderer renderer) throws IOException, GeneralSecurityException {
        if (dao.sampleTripCount() > 0) {
            return null;
        }
        SampleVault.Content content = SampleVault.paramvir(clock.instant());
        // 1. Encrypt every number and page first. Writing files can fail (storage full), so this happens BEFORE the
        //    database changes; if it fails, the files written so far are deleted again and nothing is half-saved.
        List<VaultFileEntity> pages = new ArrayList<>();
        try {
            for (SampleVault.SampleDocument sample : content.documents) {
                sample.document.numberEncrypted = encryptNumber(sample.document.id, sample.number);
                pages.add(storePage(sample.document.id, 0, new NewPage(renderer.render(sample), MIME_JPEG)));
            }
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            for (VaultFileEntity page : pages) {
                files.delete(page.id);
            }
            throw e;
        }
        // 2. All rows in one transaction: either the whole sample trip is saved, or none of it.
        database.runInTransaction(() -> {
            dao.insertTraveller(content.traveller);
            dao.insertTrip(content.trip);
            dao.insertTripTravellers(content.tripTravellers);
            dao.insertLegs(content.legs);
            dao.insertCountries(content.countries);
            for (SampleVault.SampleDocument sample : content.documents) {
                dao.insertDocument(sample.document);
            }
            for (VaultFileEntity page : pages) {
                dao.insertFile(page);
            }
            dao.insertLinks(content.links);
        });
        return content.trip.id;
    }

    /** Whose a new document is: someone already in the vault, a new person, or nobody in particular. */
    public static final class TravellerChoice {
        @Nullable
        final String existingId;
        @Nullable
        final String newName;
        @Nullable
        final String newNationality;
        @Nullable
        final LocalDate newBirthDate;

        private TravellerChoice(@Nullable String existingId, @Nullable String newName, @Nullable String newNationality,
                                @Nullable LocalDate newBirthDate) {
            this.existingId = existingId;
            this.newName = newName;
            this.newNationality = newNationality;
            this.newBirthDate = newBirthDate;
        }

        public static TravellerChoice existing(String travellerId) {
            return new TravellerChoice(travellerId, null, null, null);
        }

        /** A new traveller, e.g. from a passport's MRZ: name, 2-letter nationality and birth date (may be null). */
        public static TravellerChoice newTraveller(String name, @Nullable String nationality,
                                                   @Nullable LocalDate birthDate) {
            return new TravellerChoice(null, name.trim(), nationality, birthDate);
        }

        public static TravellerChoice nobody() {
            return new TravellerChoice(null, null, null, null);
        }
    }

    /**
     * Saves a document the traveller has just confirmed, with its pages, into a trip folder (under {@code legId},
     * or the whole trip when null). Every page is encrypted with its own key, the number is encrypted a second time,
     * and the person is added to the trip. Answers with the new document's id.
     *
     * @throws IllegalArgumentException (straight away) when the draft still has problems
     */
    public void addDocument(String tripId, @Nullable String legId, DocumentDraft draft, TravellerChoice traveller,
                            List<NewPage> pages, Callback<String> callback) {
        if (!draft.problems().isEmpty() || draft.type == null || draft.title == null) {
            throw new IllegalArgumentException("The document still has problems: " + draft.problems());
        }
        run(callback, () -> addDocumentNow(tripId, legId, draft, traveller, pages));
    }

    /** {@link #addDocument} on the calling thread (background threads and tests only). */
    public String addDocumentNow(String tripId, @Nullable String legId, DocumentDraft draft,
                                 TravellerChoice traveller, List<NewPage> pages)
            throws IOException, GeneralSecurityException {
        TravelDocumentEntity document = new TravelDocumentEntity();
        document.id = UUID.randomUUID().toString();
        document.type = draft.type;
        document.title = draft.title.trim();
        document.holderName = blankToNull(draft.holderName);
        document.issuingCountry = draft.issuingCountry;
        document.issueDate = draft.issueDate;
        document.expiryDate = draft.expiryDate;
        document.validFrom = draft.validFrom;
        document.validTo = draft.validTo;
        document.coversCountries = DocumentDraft.normaliseCountries(draft.coversCountries);
        document.entries = draft.entries;
        document.flightNo = blankToNull(draft.flightNo);
        document.bookingRef = blankToNull(draft.bookingRef);
        document.placeName = blankToNull(draft.placeName);
        document.address = blankToNull(draft.address);
        document.source = draft.source;
        // Read automatically or typed in, the traveller has looked at every field on the confirm screen.
        document.fieldsConfirmed = true;
        document.createdAt = clock.instant();
        document.numberEncrypted = encryptNumber(document.id, draft.cleanNumber());

        TravellerEntity newTraveller = null;
        if (traveller.newName != null && !traveller.newName.isEmpty()) {
            newTraveller = new TravellerEntity();
            newTraveller.id = UUID.randomUUID().toString();
            newTraveller.name = traveller.newName;
            newTraveller.nationality = traveller.newNationality;
            newTraveller.dateOfBirth = traveller.newBirthDate;
            newTraveller.createdAt = clock.instant();
            document.travellerId = newTraveller.id;
        } else {
            document.travellerId = traveller.existingId;
        }

        // Encrypt the pages first (this can fail, e.g. storage full); nothing is half-saved if it does.
        List<VaultFileEntity> stored = new ArrayList<>();
        try {
            for (int page = 0; page < pages.size(); page++) {
                stored.add(storePage(document.id, page, pages.get(page)));
            }
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            deleteFiles(fileIdsOf(stored));
            throw e;
        }
        TravellerEntity travellerToInsert = newTraveller;
        database.runInTransaction(() -> {
            if (travellerToInsert != null) {
                dao.insertTraveller(travellerToInsert);
            }
            dao.insertDocument(document);
            for (VaultFileEntity file : stored) {
                dao.insertFile(file);
            }
            dao.insertLinks(Collections.singletonList(link(tripId, document.id, legId)));
            if (document.travellerId != null) {
                dao.addTravellerToTrip(tripTraveller(tripId, document.travellerId));
            }
        });
        return document.id;
    }

    /**
     * Puts a document that is already in the vault (e.g. the passport, saved for another trip) into this trip too.
     * It is not copied: both trips point at the same document. Does nothing if the trip has it already.
     */
    public void linkDocument(String tripId, String documentId, @Nullable String legId, Callback<Void> callback) {
        run(callback, () -> {
            database.runInTransaction(() -> {
                if (dao.linkCount(tripId, documentId) > 0) {
                    return;
                }
                dao.insertLinks(Collections.singletonList(link(tripId, documentId, legId)));
                TravelDocumentEntity document = dao.document(documentId);
                if (document != null && document.travellerId != null) {
                    dao.addTravellerToTrip(tripTraveller(tripId, document.travellerId));
                }
            });
            return null;
        });
    }

    /**
     * Deletes a trip folder. Documents that only this trip used are deleted too (rows and encrypted files);
     * documents other trips still use, like a shared passport, stay.
     */
    public void deleteTrip(String tripId, Callback<Void> callback) {
        run(callback, () -> {
            List<String> fileIds = database.runInTransaction(() -> {
                List<String> deletedFiles = new ArrayList<>();
                for (String documentId : dao.documentsOnlyIn(tripId)) {
                    deletedFiles.addAll(deleteDocumentRows(documentId));
                }
                dao.deleteTrip(tripId);
                dao.deleteUnusedSampleTravellers();
                return deletedFiles;
            });
            deleteFiles(fileIds);
            return null;
        });
    }

    /** Deletes a document everywhere: from every trip folder, with all its pages. */
    public void deleteDocument(String documentId, Callback<Void> callback) {
        run(callback, () -> {
            deleteFiles(database.runInTransaction(() -> deleteDocumentRows(documentId)));
            return null;
        });
    }

    /**
     * Deletes a document's rows and answers with its file ids. The rows (with the wrapped file keys) go first, the
     * encrypted files after the transaction: if deleting a file then fails, what is left can never be decrypted.
     */
    private List<String> deleteDocumentRows(String documentId) {
        List<String> fileIds = new ArrayList<>();
        for (VaultFileEntity file : dao.filesOf(documentId)) {
            fileIds.add(file.id);
        }
        dao.deleteDocument(documentId); // its file rows and links go with it (ON DELETE CASCADE)
        return fileIds;
    }

    private void deleteFiles(List<String> fileIds) {
        for (String fileId : fileIds) {
            files.delete(fileId);
        }
    }

    // ============================================================================================
    // Opening and checking
    // ============================================================================================

    /** Decrypts one page and checks its SHA-256 fingerprint. Answers with the original bytes. */
    public void readFile(VaultFileEntity file, Callback<byte[]> callback) {
        run(callback, () -> files.read(file.id, file.wrappedKey, file.sha256));
    }

    /** Decrypts a document's number (null when it has none). Screens show it masked unless asked. */
    public void readNumber(TravelDocumentEntity document, Callback<String> callback) {
        run(callback, () -> document.numberEncrypted == null ? null
                : SecretText.decrypt(masterKey, document.numberEncrypted, numberLabel(document.id)));
    }

    /** The decrypt-test of every file in the vault (see {@link #checkAllFilesNow}). */
    public void checkAllFiles(Callback<FileCheckReport> callback) {
        run(callback, this::checkAllFilesNow);
    }

    /**
     * Decrypts every file in memory, compares its SHA-256 fingerprint with the saved one and forgets the content
     * again. Background threads and tests only.
     */
    public FileCheckReport checkAllFilesNow() {
        FileCheckReport report = new FileCheckReport();
        for (VaultFileEntity file : dao.allFiles()) {
            report.add(file.id, file.documentId, file.sizeBytes, files.check(file.id, file.wrappedKey, file.sha256));
        }
        return report;
    }

    /**
     * Facts for {@link VaultSummaries} about every document of a trip: the document, its traveller's name and how many
     * of its encrypted pages are missing on the phone. Nothing is decrypted. Background threads only
     * (VaultSummaryReader).
     */
    List<VaultSummaries.DocumentFacts> summaryFactsNow(String tripId) {
        List<VaultSummaries.DocumentFacts> facts = new ArrayList<>();
        for (TripDocumentRow row : dao.tripDocuments(tripId)) {
            TravellerEntity traveller = row.document.travellerId == null ? null
                    : dao.traveller(row.document.travellerId);
            List<VaultFileEntity> pages = dao.filesOf(row.document.id);
            int missing = 0;
            for (VaultFileEntity page : pages) {
                if (!files.fileFor(page.id).isFile()) {
                    missing++;
                }
            }
            facts.add(new VaultSummaries.DocumentFacts(row.document, traveller == null ? null : traveller.name,
                    pages.size(), missing));
        }
        return facts;
    }

    /** The trip row (background threads only). */
    @Nullable
    TripVaultEntity tripNow(String tripId) {
        return dao.trip(tripId);
    }

    /** All trip rows (background threads only). */
    List<TripVaultEntity> tripsNow() {
        return dao.trips();
    }

    /** The database calls, for instrumented tests that check what was stored. Never used by app code. */
    @VisibleForTesting
    VaultDao daoForTests() {
        return dao;
    }

    /** Closes the database. Only {@link Vault#deleteEverything} uses this. */
    void close() {
        database.close();
    }

    // ============================================================================================
    // Helpers
    // ============================================================================================

    private VaultFileEntity storePage(String documentId, int page, NewPage newPage)
            throws IOException, GeneralSecurityException {
        EncryptedFileStore.StoredFile stored = files.write(UUID.randomUUID().toString(), newPage.content);
        VaultFileEntity file = new VaultFileEntity();
        file.id = stored.fileId;
        file.documentId = documentId;
        file.page = page;
        file.mimeType = newPage.mimeType;
        file.sizeBytes = stored.sizeBytes;
        file.sha256 = stored.sha256;
        file.wrappedKey = stored.wrappedKey;
        file.addedAt = clock.instant();
        return file;
    }

    @Nullable
    private String encryptNumber(String documentId, @Nullable String number) throws GeneralSecurityException {
        String cleaned = DocumentNumbers.normalise(number);
        return cleaned == null ? null : SecretText.encrypt(masterKey, cleaned, numberLabel(documentId));
    }

    private static String numberLabel(String documentId) {
        return "doc-number:" + documentId;
    }

    private static TripDocumentLinkEntity link(String tripId, String documentId, @Nullable String legId) {
        TripDocumentLinkEntity link = new TripDocumentLinkEntity();
        link.tripId = tripId;
        link.documentId = documentId;
        link.legId = legId;
        return link;
    }

    private static TripTravellerEntity tripTraveller(String tripId, String travellerId) {
        TripTravellerEntity onTrip = new TripTravellerEntity();
        onTrip.tripId = tripId;
        onTrip.travellerId = travellerId;
        return onTrip;
    }

    private static List<String> fileIdsOf(List<VaultFileEntity> files) {
        List<String> ids = new ArrayList<>();
        for (VaultFileEntity file : files) {
            ids.add(file.id);
        }
        return ids;
    }

    @Nullable
    private static String blankToNull(@Nullable String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    /** A job that may fail, run on the vault thread. */
    private interface Job<T> {
        T run() throws Exception;
    }

    private <T> void run(Callback<T> callback, Job<T> job) {
        vaultExecutor.execute(() -> {
            try {
                T value = job.run();
                mainExecutor.execute(() -> callback.onDone(value));
            } catch (Exception e) {
                mainExecutor.execute(() -> callback.onError(e));
            }
        });
    }
}
