package com.safarsaathi.app.ui.vault;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Reads a document's pages on the phone with Google ML Kit: the text (for a passport's MRZ) and any barcode (for a
 * boarding pass). Both models are inside the app ("bundled"), so this works offline and without Google Play
 * services. Nothing leaves the phone.
 *
 * <p>Call from a background thread: it waits for ML Kit to finish.
 */
public final class PageAnalyzer {

    /** What was found on all pages together. */
    public static final class Result {
        public final String text;
        public final List<String> barcodes;

        Result(String text, List<String> barcodes) {
            this.text = text;
            this.barcodes = barcodes;
        }
    }

    /**
     * The MRZ sits in the bottom third of a passport page. That strip is read a second time, enlarged, because small
     * MRZ characters in a big photo are the most common misreading.
     */
    private static final float MRZ_STRIP = 0.35f;

    private PageAnalyzer() {
    }

    public static Result analyse(List<Bitmap> pages) throws ExecutionException, InterruptedException {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        BarcodeScanner scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC, Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_DATA_MATRIX)
                .build());
        StringBuilder text = new StringBuilder();
        List<String> barcodes = new ArrayList<>();
        try {
            for (int i = 0; i < pages.size(); i++) {
                Bitmap page = pages.get(i);
                InputImage image = InputImage.fromBitmap(page, 0);
                appendLines(text, Tasks.await(recognizer.process(image)));
                for (Barcode barcode : Tasks.await(scanner.process(image))) {
                    if (barcode.getRawValue() != null) {
                        barcodes.add(barcode.getRawValue());
                    }
                }
                if (i == 0) {
                    Bitmap strip = mrzStrip(page);
                    try {
                        appendLines(text, Tasks.await(recognizer.process(InputImage.fromBitmap(strip, 0))));
                    } finally {
                        if (strip != page) {
                            strip.recycle();
                        }
                    }
                }
            }
        } finally {
            recognizer.close();
            scanner.close();
        }
        return new Result(text.toString(), barcodes);
    }

    private static void appendLines(StringBuilder out, Text text) {
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                out.append(line.getText()).append('\n');
            }
        }
    }

    /** The bottom part of the page, enlarged to about 2000 pixels wide. */
    private static Bitmap mrzStrip(Bitmap page) {
        int top = Math.round(page.getHeight() * (1 - MRZ_STRIP));
        int height = page.getHeight() - top;
        float scale = Math.max(1f, 2000f / page.getWidth());
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(scale, scale);
        return Bitmap.createBitmap(page, 0, top, page.getWidth(), height, matrix, true);
    }
}
