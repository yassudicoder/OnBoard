package com.safarsaathi.app.data;

import androidx.lifecycle.LiveData;

import com.safarsaathi.app.data.local.DocumentEntity;
import com.safarsaathi.app.data.local.IdDocumentEntity;
import com.safarsaathi.app.data.local.SavedCardEntity;
import com.safarsaathi.app.data.local.WalletDao;
import com.safarsaathi.app.data.wallet.IdDocuments;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The Wallet and Profile tabs' own data, kept ONLY on the phone (Room): boarding passes, ID documents (Aadhaar
 * last 4 digits only, PAN, visas) and saved cards. None of it is ever sent to the server.
 * Reading is LiveData (screens redraw by themselves); writing happens on the shared single background thread, like
 * TripRepository, so it never blocks the screen and never runs at the same time as a trip save.
 */
public class WalletRepository {

    private final WalletDao dao;
    private final Executor ioExecutor;
    private final Clock clock;

    public WalletRepository(WalletDao dao, Executor ioExecutor, Clock clock) {
        this.dao = dao;
        this.ioExecutor = ioExecutor;
        this.clock = clock;
    }

    /** The trip's saved boarding passes, newest first. */
    public LiveData<List<DocumentEntity>> observeBoardingPasses(long tripId) {
        return dao.observeDocuments(tripId, DocumentEntity.KIND_BOARDING_PASS);
    }

    /** Saves a boarding pass barcode's text. Check it with BcbpParser first: this method stores it as it is. */
    public void addBoardingPass(long tripId, String rawData) {
        DocumentEntity document = new DocumentEntity();
        document.tripId = tripId;
        document.kind = DocumentEntity.KIND_BOARDING_PASS;
        document.rawData = rawData;
        document.addedAt = clock.instant();
        ioExecutor.execute(() -> dao.insertDocument(document));
    }

    public void deleteDocument(long id) {
        ioExecutor.execute(() -> dao.deleteDocument(id));
    }

    public LiveData<List<SavedCardEntity>> observeCards() {
        return dao.observeCards();
    }

    /**
     * Saves a card: bank, CREDIT/DEBIT and the last 4 digits only.
     *
     * @throws IllegalArgumentException when last4 is not exactly 4 digits or the bank is blank
     */
    public void addCard(String bank, String cardType, String last4) {
        if (bank == null || bank.trim().isEmpty() || !SavedCardEntity.isValidLast4(last4)) {
            throw new IllegalArgumentException("A card needs a bank and exactly 4 digits");
        }
        SavedCardEntity card = new SavedCardEntity();
        card.bank = bank.trim();
        card.cardType = SavedCardEntity.TYPE_DEBIT.equals(cardType) ? SavedCardEntity.TYPE_DEBIT
                : SavedCardEntity.TYPE_CREDIT;
        card.last4 = last4;
        card.addedAt = clock.instant();
        ioExecutor.execute(() -> dao.insertCard(card));
    }

    public void deleteCard(long id) {
        ioExecutor.execute(() -> dao.deleteCard(id));
    }

    /** Aadhaar, PAN and visas, in that order. */
    public LiveData<List<IdDocumentEntity>> observeIdDocuments() {
        return dao.observeIdDocuments();
    }

    /**
     * Saves an Aadhaar: the name and ONLY the last 4 digits (masked Aadhaar).
     *
     * @throws IllegalArgumentException for a blank name or anything but exactly 4 digits
     */
    public void addAadhaar(String holderName, String last4) {
        if (isBlank(holderName) || !IdDocuments.isValidAadhaarLast4(last4)) {
            throw new IllegalArgumentException("An Aadhaar needs a name and exactly the last 4 digits");
        }
        save(newDocument(IdDocuments.KIND_AADHAAR, holderName, last4));
    }

    /** @throws IllegalArgumentException for a blank name or a PAN that is not like ABCDE1234F */
    public void addPan(String holderName, String pan) {
        String normalised = IdDocuments.normalisePan(pan);
        if (isBlank(holderName) || !IdDocuments.isValidPan(normalised)) {
            throw new IllegalArgumentException("A PAN needs a name and a PAN like ABCDE1234F");
        }
        save(newDocument(IdDocuments.KIND_PAN, holderName, normalised));
    }

    /** @throws IllegalArgumentException for missing fields or a validity that ends before it starts */
    public void addVisa(String holderName, String country, String visaType, String number, LocalDate validFrom,
                        LocalDate validUntil, boolean multipleEntry) {
        if (isBlank(holderName) || isBlank(country) || isBlank(number) || validFrom == null || validUntil == null
                || validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("A visa needs a name, country, number and a valid date range");
        }
        IdDocumentEntity visa = newDocument(IdDocuments.KIND_VISA, holderName, number.trim());
        visa.country = country.trim();
        visa.visaType = isBlank(visaType) ? null : visaType.trim();
        visa.validFrom = validFrom.toString();
        visa.validUntil = validUntil.toString();
        visa.entries = multipleEntry ? IdDocuments.ENTRIES_MULTIPLE : IdDocuments.ENTRIES_SINGLE;
        save(visa);
    }

    public void deleteIdDocument(long id) {
        ioExecutor.execute(() -> dao.deleteIdDocument(id));
    }

    private IdDocumentEntity newDocument(String kind, String holderName, String number) {
        IdDocumentEntity document = new IdDocumentEntity();
        document.kind = kind;
        document.holderName = holderName.trim();
        document.number = number;
        document.addedAt = clock.instant();
        return document;
    }

    private void save(IdDocumentEntity document) {
        ioExecutor.execute(() -> dao.insertIdDocument(document));
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
