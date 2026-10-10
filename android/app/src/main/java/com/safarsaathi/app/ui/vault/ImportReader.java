package com.safarsaathi.app.ui.vault;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.vault.crypto.EncryptedFileStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a file the traveller picked ("Import PDF or image") or a page Google's scanner handed back, straight into
 * memory. Nothing is copied to the phone's storage: the bytes go from here to encryption.
 * Call from a background thread.
 */
final class ImportReader {

    static final String MIME_PDF = "application/pdf";

    /** Longest side of a photo decoded for cropping and reading (keeps memory use sensible on older phones). */
    private static final int MAX_DECODE_SIDE = 3000;

    private ImportReader() {
    }

    /** The file's bytes; refuses files over the vault's size limit. */
    static byte[] readBytes(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Could not open the file");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > EncryptedFileStore.MAX_FILE_BYTES) {
                    throw new FileTooLargeException();
                }
            }
            return out.toByteArray();
        }
    }

    /** Thrown for files over EncryptedFileStore.MAX_FILE_BYTES (the screen says how big a file may be). */
    static final class FileTooLargeException extends IOException {
        FileTooLargeException() {
            super("File too large");
        }
    }

    static boolean isPdf(ContentResolver resolver, Uri uri) {
        return MIME_PDF.equals(resolver.getType(uri));
    }

    /**
     * A photo, the right way up. Android 9+ (ImageDecoder) applies the photo's own rotation note (EXIF); on Android
     * 8 a photo taken sideways may need the crop screen's "rotate" (not in V2: it stays as stored).
     */
    static Bitmap decodeImage(Context context, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE); // a normal bitmap we can draw and crop
                int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (longest > MAX_DECODE_SIDE) {
                    float scale = (float) MAX_DECODE_SIDE / longest;
                    decoder.setTargetSize(Math.round(info.getSize().getWidth() * scale),
                            Math.round(info.getSize().getHeight() * scale));
                }
            });
        }
        return decodeBytes(readBytes(context.getContentResolver(), uri));
    }

    /** A JPEG or PNG in memory, shrunk by powers of 2 to at most about MAX_DECODE_SIDE. */
    @Nullable
    static Bitmap decodeBytes(byte[] bytes) {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, size);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(size.outWidth, size.outHeight) / options.inSampleSize > MAX_DECODE_SIDE) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    /**
     * The first page of a PDF as a picture, only to look for a barcode or MRZ on it. Null when the PDF cannot be
     * opened that way (some apps hand over files that cannot be read at random positions, which PdfRenderer needs);
     * the traveller then simply types the details.
     */
    @Nullable
    static Bitmap renderFirstPdfPage(ContentResolver resolver, Uri uri) {
        try (ParcelFileDescriptor file = resolver.openFileDescriptor(uri, "r")) {
            if (file == null) {
                return null;
            }
            try (PdfRenderer renderer = new PdfRenderer(file); PdfRenderer.Page page = renderer.openPage(0)) {
                float scale = 2000f / Math.max(page.getWidth(), page.getHeight());
                Bitmap bitmap = Bitmap.createBitmap(Math.round(page.getWidth() * scale),
                        Math.round(page.getHeight() * scale), Bitmap.Config.ARGB_8888);
                new Canvas(bitmap).drawColor(Color.WHITE); // PDFs are transparent; text recognition needs paper
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** JPEG bytes of a page picture (quality 90: sharp enough to read small print). */
    static byte[] toJpeg(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        return out.toByteArray();
    }
}
