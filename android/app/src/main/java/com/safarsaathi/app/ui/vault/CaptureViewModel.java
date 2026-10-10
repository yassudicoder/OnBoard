package com.safarsaathi.app.ui.vault;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.VaultRepository;
import com.safarsaathi.app.data.vault.capture.DocumentDraft;
import com.safarsaathi.app.data.vault.capture.DraftReader;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The document being added, shared by the add-document screens (camera, crop, confirm). It lives as long as the
 * vault's screens do (it belongs to the nav graph "vault_graph"), survives screen rotation, and is wiped as soon as
 * the document is saved or the traveller leaves the vault.
 *
 * <p>Pages are held ONLY in memory, never written to the phone's storage unencrypted: from the camera, scanner or
 * picked file straight to here, and from here straight to encryption.
 */
public class CaptureViewModel extends ViewModel {

    /** Where the add-document flow is. */
    public enum Stage {
        /** Nothing being added. */
        IDLE,
        /** Pages are being read in or analysed. */
        WORKING,
        /** The draft is ready for the confirm screen. */
        READY
    }

    /** The shared instance for any vault screen (the same object for all of them). */
    public static CaptureViewModel of(Fragment screen) {
        return new ViewModelProvider(NavHostFragment.findNavController(screen).getBackStackEntry(R.id.vault_graph))
                .get(CaptureViewModel.class);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "vault-capture"));
    private final MutableLiveData<Stage> stage = new MutableLiveData<>(Stage.IDLE);

    // Changed only on the main thread (work results are posted back there).
    private String tripId = "";
    private DocSource source = DocSource.MANUAL;
    private final List<VaultRepository.NewPage> pages = new ArrayList<>();
    private final List<Bitmap> forAnalysis = new ArrayList<>();
    @Nullable
    private Bitmap pendingCrop;
    @Nullable
    private DocumentDraft draft;
    /** The confirm screen filled its form from the draft already (not again after a rotation). */
    public boolean formFilled;
    /** Choices on the confirm screen that must survive a rotation. */
    @Nullable
    public String chosenLegId;
    /** The leg was pre-chosen once (e.g. a boarding pass DEL → KGL under that flight); after that it is the traveller's. */
    public boolean legDefaultApplied;
    @Nullable
    public String chosenTravellerKey;

    public LiveData<Stage> stage() {
        return stage;
    }

    public String tripId() {
        return tripId;
    }

    public DocSource source() {
        return source;
    }

    public int pageCount() {
        return pages.size();
    }

    /** The pages to save, in order. */
    public List<VaultRepository.NewPage> pages() {
        return Collections.unmodifiableList(new ArrayList<>(pages));
    }

    @Nullable
    public DocumentDraft draft() {
        return draft;
    }

    /** Starts adding a document to a trip (forgets anything half-added before). */
    public void begin(String tripId, DocSource source) {
        clear();
        this.tripId = tripId;
        this.source = source;
    }

    /** "Type it in": no pages, an empty draft, straight to the confirm screen. */
    public void beginManual(String tripId) {
        begin(tripId, DocSource.MANUAL);
        draft = new DocumentDraft();
        stage.setValue(Stage.READY);
    }

    // ----- Photos waiting for the crop screen -----

    /**
     * The photo the crop screen shows. Never recycled by hand: the screen may still be drawing it, and Android frees
     * a bitmap's memory by itself once nothing refers to it.
     */
    public void setPendingCrop(@Nullable Bitmap photo) {
        pendingCrop = photo;
    }

    @Nullable
    public Bitmap pendingCrop() {
        return pendingCrop;
    }

    /** The cropped page goes in as a JPEG (encoded in the background); {@code done} runs on the main thread. */
    public void addCroppedPage(Bitmap page, Runnable done, Consumer<Exception> failed) {
        stage.setValue(Stage.WORKING);
        work(() -> ImportReader.toJpeg(page), jpeg -> {
            pages.add(new VaultRepository.NewPage(jpeg, VaultRepository.MIME_JPEG));
            forAnalysis.add(page);
            stage.setValue(Stage.IDLE);
            done.run();
        }, failed);
    }

    // ----- Google's scanner and imported files -----

    /**
     * Reads the pages Google's document scanner returned (JPEG files it put in this app's private cache), then
     * deletes those files at once, so the only lasting copy is the encrypted one. Then analyses them.
     */
    public void addScannedPages(Context context, List<Uri> uris, Runnable done, Consumer<Exception> failed) {
        ContentResolver resolver = context.getContentResolver();
        File cache = context.getCacheDir();
        stage.setValue(Stage.WORKING);
        work(() -> {
            List<byte[]> jpegs = new ArrayList<>();
            try {
                for (Uri uri : uris) {
                    jpegs.add(ImportReader.readBytes(resolver, uri));
                }
            } finally {
                for (Uri uri : uris) {
                    deleteScannerFile(cache, uri);
                }
            }
            return jpegs;
        }, jpegs -> {
            for (byte[] jpeg : jpegs) {
                pages.add(new VaultRepository.NewPage(jpeg, VaultRepository.MIME_JPEG));
            }
            done.run();
        }, failed);
    }

    /**
     * An imported file: a PDF is kept as it is (its first page is pictured only to look for a barcode or MRZ); a
     * photo goes to the crop screen first. {@code needsCrop} answers true for a photo.
     */
    public void importFile(Context context, Uri uri, Consumer<Boolean> needsCrop, Consumer<Exception> failed) {
        ContentResolver resolver = context.getContentResolver();
        boolean pdf = ImportReader.isPdf(resolver, uri);
        source = pdf ? DocSource.PDF : DocSource.IMPORT;
        stage.setValue(Stage.WORKING);
        if (pdf) {
            work(() -> new Object[] {ImportReader.readBytes(resolver, uri), ImportReader.renderFirstPdfPage(resolver, uri)},
                    result -> {
                        pages.add(new VaultRepository.NewPage((byte[]) result[0], ImportReader.MIME_PDF));
                        if (result[1] != null) {
                            forAnalysis.add((Bitmap) result[1]);
                        }
                        needsCrop.accept(false);
                    }, failed);
        } else {
            work(() -> ImportReader.decodeImage(context, uri), photo -> {
                setPendingCrop(photo);
                stage.setValue(Stage.IDLE);
                needsCrop.accept(true);
            }, failed);
        }
    }

    /**
     * Reads every page on the phone (text + barcodes, see PageAnalyzer) and turns what it found into a pre-filled
     * draft. If reading fails, the draft is simply empty: the traveller types the details.
     */
    public void analyse() {
        stage.setValue(Stage.WORKING);
        List<Bitmap> images = new ArrayList<>(forAnalysis);
        List<VaultRepository.NewPage> toDecode = new ArrayList<>();
        if (images.isEmpty()) {
            for (VaultRepository.NewPage page : pages) {
                if (page.mimeType.startsWith("image/")) {
                    toDecode.add(page);
                }
            }
        }
        DocSource draftSource = source;
        work(() -> {
            List<Bitmap> decoded = new ArrayList<>();
            try {
                for (VaultRepository.NewPage page : toDecode) {
                    Bitmap bitmap = ImportReader.decodeBytes(page.content);
                    if (bitmap != null) {
                        decoded.add(bitmap);
                    }
                }
                List<Bitmap> all = new ArrayList<>(images);
                all.addAll(decoded);
                PageAnalyzer.Result found = all.isEmpty() ? null : PageAnalyzer.analyse(all);
                return DraftReader.read(found == null ? null : found.text,
                        found == null ? Collections.emptyList() : found.barcodes, draftSource, LocalDate.now());
            } catch (Exception e) {
                return DraftReader.read(null, Collections.emptyList(), draftSource, LocalDate.now());
            } finally {
                for (Bitmap bitmap : decoded) {
                    bitmap.recycle();
                }
            }
        }, result -> {
            draft = result;
            stage.setValue(Stage.READY);
        }, error -> stage.setValue(Stage.IDLE));
    }

    /** Forgets everything: page bytes are overwritten with zeros, pictures are freed. */
    public void clear() {
        for (VaultRepository.NewPage page : pages) {
            Arrays.fill(page.content, (byte) 0);
        }
        pages.clear();
        // The pictures may still be in use by an analysis on the capture thread: free them on that same thread,
        // after it has finished.
        List<Bitmap> toFree = new ArrayList<>(forAnalysis);
        forAnalysis.clear();
        if (!toFree.isEmpty()) {
            worker.execute(() -> {
                for (Bitmap bitmap : toFree) {
                    bitmap.recycle();
                }
            });
        }
        pendingCrop = null;
        draft = null;
        formFilled = false;
        chosenLegId = null;
        legDefaultApplied = false;
        chosenTravellerKey = null;
        stage.setValue(Stage.IDLE);
    }

    @Override
    protected void onCleared() {
        clear();
        worker.shutdown(); // lets the queued clean-up run, then stops the thread
        super.onCleared();
    }

    /** TEST HOOK: a draft and pages as if they had just been read (instrumented tests only). */
    @VisibleForTesting
    public void setForTests(String tripId, DocumentDraft draft, List<VaultRepository.NewPage> newPages) {
        begin(tripId, draft.source);
        pages.addAll(newPages);
        this.draft = draft;
        stage.setValue(Stage.READY);
    }

    // ---------------------------------------------------------------------------------------------

    /** Work that may fail, run on the capture thread. */
    private interface Job<T> {
        T run() throws Exception;
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    private <T> void work(Job<T> job, Consumer<T> onMain, Consumer<Exception> failedOnMain) {
        worker.execute(() -> {
            try {
                T result = job.run();
                main.post(() -> onMain.accept(result));
            } catch (Exception e) {
                main.post(() -> {
                    stage.setValue(Stage.IDLE);
                    failedOnMain.accept(e);
                });
            }
        });
    }

    /** Google's scanner writes its pages into this app's cache folder; delete them once read. */
    private static void deleteScannerFile(File cache, Uri uri) {
        if (!"file".equals(uri.getScheme()) || uri.getPath() == null) {
            return;
        }
        File file = new File(uri.getPath());
        try {
            if (file.getCanonicalPath().startsWith(cache.getCanonicalPath())) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (IOException ignored) {
            // could not resolve the path: leave it (the cache folder is private to the app)
        }
    }
}
