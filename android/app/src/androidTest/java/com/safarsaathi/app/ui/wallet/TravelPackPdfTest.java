package com.safarsaathi.app.ui.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.safarsaathi.app.data.TripMapper;
import com.safarsaathi.app.data.local.IdDocumentEntity;
import com.safarsaathi.app.data.wallet.BcbpParser;
import com.safarsaathi.app.data.wallet.IdDocuments;
import com.safarsaathi.app.testing.TestTrips;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The Wallet's PDF (on a phone or emulator, because it uses Android's PdfDocument): it is a real PDF that Android's
 * own PdfRenderer can open, and long content flows onto more pages.
 */
@RunWith(AndroidJUnit4.class)
public class TravelPackPdfTest {

    private static final String BOARDING_PASS = "M1SHARMA/ADITI        EK7Q2ZM BOMBLRIJ 0512 284Y014A0001 100";

    @Test
    public void writesAPdfThatAndroidCanOpen() throws Exception {
        File file = writePdf(1, 3);

        assertTrue(startsWithPdfHeader(file));
        assertEquals(1, pageCount(file));
    }

    @Test
    public void manyDocuments_flowOntoMorePages() throws Exception {
        File file = writePdf(6, 12);

        assertTrue(pageCount(file) > 1);
    }

    /** A PDF with demo trip 1, {@code passCount} copies of a boarding pass and {@code idCount} ID documents. */
    private static File writePdf(int passCount, int idCount) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TripMapper.MappedTrip trip = TestTrips.demoTrip1();
        List<TravelPackPdf.SavedPass> passes = new ArrayList<>();
        for (int i = 0; i < passCount; i++) {
            passes.add(new TravelPackPdf.SavedPass(BcbpParser.parse(BOARDING_PASS, LocalDate.of(2026, 10, 10)),
                    BOARDING_PASS));
        }
        List<IdDocumentEntity> ids = new ArrayList<>();
        for (int i = 0; i < idCount; i++) {
            IdDocumentEntity id = new IdDocumentEntity();
            id.kind = i % 3 == 0 ? IdDocuments.KIND_AADHAAR : i % 3 == 1 ? IdDocuments.KIND_PAN : IdDocuments.KIND_VISA;
            id.holderName = "Aditi";
            id.number = i % 3 == 0 ? "1234" : i % 3 == 1 ? "ABCDE1234F" : "V-998877";
            id.country = "United Arab Emirates";
            id.visaType = "Tourist";
            id.validFrom = "2026-10-01";
            id.validUntil = "2026-12-31";
            id.entries = IdDocuments.ENTRIES_MULTIPLE;
            ids.add(id);
        }
        TravelPackPdf.Content content = new TravelPackPdf.Content("Aditi", trip.trip, trip.blocks, passes, ids,
                Instant.parse("2026-10-10T18:15:00Z"));
        File file = new File(context.getCacheDir(), "travel-pack-test-" + passCount + "-" + idCount + ".pdf");
        try (OutputStream out = new FileOutputStream(file)) {
            TravelPackPdf.write(context, content, out);
        }
        return file;
    }

    private static boolean startsWithPdfHeader(File file) throws Exception {
        byte[] header = new byte[5];
        try (InputStream in = new FileInputStream(file)) {
            assertEquals(5, in.read(header));
        }
        return new String(header, "US-ASCII").equals("%PDF-");
    }

    private static int pageCount(File file) throws Exception {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            return renderer.getPageCount();
        }
    }
}
