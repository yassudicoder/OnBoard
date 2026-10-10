package com.safarsaathi.app.data.vault;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.safarsaathi.app.data.vault.db.TripVaultEntity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The ONE read-only window into the Document Vault for other parts of the app. Get it with
 * {@code ServiceLocator.get().getVault().summaryReader()}.
 *
 * <p>Rules (the user's decision, 2026-10-11):
 * <ul>
 *   <li>Per document only: type, holder FIRST name, expiry date, covered dates and countries, status (see
 *       {@link SummaryStatus}), the SAMPLE flag, and for hotel bookings the hotel's name and address
 *       ({@link DocumentSummary}). Never document numbers, MRZ lines, page images, file paths, full names or birth
 *       dates: they are not even in the summary class.</li>
 *   <li>Vault locked: only hotel bookings come back, with only their name and address (the Ride hand-off may plan a
 *       ride to the hotel without unlocking). Everything else needs the vault unlocked; a screen can ask for that with
 *       {@code ui.vault.VaultUnlockPrompt}.</li>
 *   <li>Allowed callers: Ask OnBoard (answers made ON the phone only, never sent to a cloud service) and the Ride
 *       hand-off (hotel address only, used on the phone). Nothing from here may be sent to our backend or any other
 *       server.</li>
 *   <li>Read-only: there is no way to change the vault through this class.</li>
 * </ul>
 */
public final class VaultSummaryReader {

    private final Vault vault;
    private final Executor background;
    private final Executor main;
    private final Clock clock;

    VaultSummaryReader(Vault vault, Executor background, Executor main, Clock clock) {
        this.vault = vault;
        this.background = background;
        this.main = main;
        this.clock = clock;
    }

    /** Whether the vault is unlocked right now (full summaries) or locked (hotel name/address only). */
    public boolean isUnlocked() {
        return vault.session().isUnlocked();
    }

    /** Summaries of one trip folder's documents. Background threads only: it reads the vault database. */
    @WorkerThread
    public List<DocumentSummary> getSummaries(String tripId) throws VaultUnavailableException {
        VaultRepository repository = vault.repository();
        TripVaultEntity trip = repository.tripNow(tripId);
        if (trip == null) {
            return Collections.emptyList();
        }
        return VaultSummaries.build(repository.summaryFactsNow(tripId), trip.offlineReadyAt, isUnlocked(), today());
    }

    /** {@link #getSummaries(String)} on the vault's background thread; the answer arrives on the main thread. */
    public void getSummaries(String tripId, VaultRepository.Callback<List<DocumentSummary>> callback) {
        run(() -> getSummaries(tripId), callback);
    }

    /**
     * The trip folder to talk about: dates covering today, else the next one, else the most recent (see
     * {@link VaultSummaries#currentTrip}). Null when the vault has no trips. Background threads only.
     */
    @WorkerThread
    @Nullable
    public TripRef currentTrip() throws VaultUnavailableException {
        return VaultSummaries.currentTrip(vault.repository().tripsNow(), isUnlocked(), today());
    }

    /** {@link #currentTrip()} on the vault's background thread; the answer (maybe null) arrives on the main thread. */
    public void currentTrip(VaultRepository.Callback<TripRef> callback) {
        run(this::currentTrip, callback);
    }

    /** "Today" where the phone is (a traveller abroad means the local date there). */
    private LocalDate today() {
        return clock.instant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private interface Read<T> {
        T run() throws Exception;
    }

    private <T> void run(Read<T> read, VaultRepository.Callback<T> callback) {
        background.execute(() -> {
            try {
                T value = read.run();
                main.execute(() -> callback.onDone(value));
            } catch (Exception e) {
                main.execute(() -> callback.onError(e));
            }
        });
    }
}
