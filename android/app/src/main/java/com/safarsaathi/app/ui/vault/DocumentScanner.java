package com.safarsaathi.app.ui.vault;

import android.content.Context;

import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;

/**
 * Google's ML Kit document scanner: its own screen finds the page edges, straightens the page and handles several
 * pages. That screen and its models come from Google Play services, not from our app: downloaded once, after that
 * it works offline, entirely on the phone (developers.google.com/ml-kit/vision/doc-scanner, checked 2026-10-11).
 *
 * <p>When it cannot run (no Play services, not downloaded yet and offline, or less than about 1.7 GB of memory),
 * the vault falls back to its own camera + manual crop. Note: the scanner screen belongs to Google Play services, so
 * the vault's screenshot blocking does not cover that one screen (docs/privacy.md).
 */
final class DocumentScanner {

    /** Most pages one scan may have (a passport photo page plus visa pages fit easily). */
    static final int MAX_PAGES = 10;

    private DocumentScanner() {
    }

    static GmsDocumentScanner client() {
        return GmsDocumentScanning.getClient(new GmsDocumentScannerOptions.Builder()
                // Picking from the gallery is offered separately ("Import a PDF or photo").
                .setGalleryImportAllowed(false)
                .setPageLimit(MAX_PAGES)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build());
    }

    /**
     * Asks Google Play services to download the scanner now, while the phone may be online, so it is ready when the
     * traveller needs it offline. Fire and forget: if it fails, the vault's own camera is used. Sends no document
     * data anywhere.
     */
    static void prepare(Context context) {
        try {
            ModuleInstall.getClient(context.getApplicationContext())
                    .installModules(ModuleInstallRequest.newBuilder().addApi(client()).build());
        } catch (RuntimeException noPlayServices) {
            // nothing to prepare on a phone without Google Play services
        }
    }
}
