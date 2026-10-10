package com.safarsaathi.app.ui.wallet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.IdDocumentEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.data.wallet.BoardingPass;
import com.safarsaathi.app.data.wallet.IdDocuments;
import com.safarsaathi.app.ui.trip.TripOverview;
import com.safarsaathi.app.ui.trip.TripTime;
import com.safarsaathi.app.ui.trip.TripUi;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the Wallet's "travel pack" as a PDF file (A4), made entirely on the phone with Android's own
 * {@link PdfDocument}, so it also works offline:
 * <ol>
 *   <li><b>Ticket</b>: every flight of every saved boarding pass, with its barcode text drawn again as a QR code
 *       (the boarding pass standard allows QR), so the page can be scanned like the original;</li>
 *   <li><b>Trip and bookings</b>: each booking of the saved trip with its time, booking reference and status (the
 *       same status rules as the Home and Trip tabs);</li>
 *   <li><b>ID documents</b>: Aadhaar and PAN MASKED (IdDocuments), visas with their validity.</li>
 * </ol>
 * Every page ends with a note that it is not an official document. The page is always light (white paper), whatever
 * the phone's dark mode, and the text sizes are fixed, so large text mode does not change the PDF.
 */
final class TravelPackPdf {

    /** What goes into the PDF. Everything comes from what is saved on the phone. */
    static final class Content {
        final String travellerName;
        @Nullable
        final TripEntity trip;
        final List<BlockEntity> blocks;
        /** Each pass with the barcode text it was read from (for the QR code). */
        final List<SavedPass> passes;
        final List<IdDocumentEntity> ids;
        final Instant generatedAt;

        Content(String travellerName, @Nullable TripEntity trip, List<BlockEntity> blocks, List<SavedPass> passes,
                List<IdDocumentEntity> ids, Instant generatedAt) {
            this.travellerName = travellerName;
            this.trip = trip;
            this.blocks = blocks;
            this.passes = passes;
            this.ids = ids;
            this.generatedAt = generatedAt;
        }
    }

    /** A boarding pass and the barcode text it came from. */
    static final class SavedPass {
        final BoardingPass pass;
        final String rawData;

        SavedPass(BoardingPass pass, String rawData) {
            this.pass = pass;
            this.rawData = rawData;
        }
    }

    // A4 in PDF points (1/72 inch).
    static final int PAGE_WIDTH = 595;
    static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 40;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float FOOTER_HEIGHT = 40;

    private static final int INK = Color.rgb(0x1A, 0x1B, 0x1E);
    private static final int GREY = Color.rgb(0x44, 0x47, 0x4E);
    private static final int BRAND = Color.rgb(0x28, 0x5E, 0xA7);
    private static final int LINE = Color.rgb(0xC4, 0xC6, 0xCF);
    private static final int TICKET_FILL = Color.rgb(0xEE, 0xF3, 0xFF);

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final Context context;
    private final PdfDocument pdf = new PdfDocument();
    private final TextPaint title = paint(20, true, BRAND);
    private final TextPaint heading = paint(13, true, INK);
    private final TextPaint body = paint(10.5f, false, INK);
    private final TextPaint bodyBold = paint(10.5f, true, INK);
    private final TextPaint small = paint(9, false, GREY);
    private final TextPaint label = paint(8, false, GREY);
    private final TextPaint route = paint(24, true, INK);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PdfDocument.Page page;
    private Canvas canvas;
    private float y;
    private int pageNumber;

    private TravelPackPdf(Context context) {
        this.context = context;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(0.8f);
        linePaint.setColor(LINE);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    /** Makes the PDF and writes it to {@code out} (not closed here). */
    static void write(Context context, Content content, OutputStream out) throws IOException {
        TravelPackPdf writer = new TravelPackPdf(context);
        try {
            writer.draw(content);
            writer.pdf.writeTo(out);
        } finally {
            writer.pdf.close();
        }
    }

    /** "OnBoard-travel-pack-2026-10-11.pdf": the trip's first day, or today when there is no trip. */
    static String fileName(Context context, @Nullable List<BlockEntity> blocks) {
        LocalDate day = blocks == null || blocks.isEmpty()
                ? LocalDate.now(AppConfig.TRIP_ZONE)
                : TripTime.localDate(blocks.get(0).startTime);
        return context.getString(R.string.pdf_file_name_prefix) + "-" + day + ".pdf";
    }

    private void draw(Content content) {
        startPage();
        text(context.getString(R.string.pdf_title), title, MARGIN, CONTENT_WIDTH);
        String madeOn = LONG_DATE.format(content.generatedAt.atZone(AppConfig.TRIP_ZONE)) + ", "
                + HOURS_MINUTES.format(content.generatedAt.atZone(AppConfig.TRIP_ZONE));
        y += 2;
        text(context.getString(R.string.pdf_subtitle, content.travellerName, madeOn), small, MARGIN, CONTENT_WIDTH);
        y += 14;

        drawTickets(content.passes);
        drawTrip(content.trip, content.blocks);
        drawIds(content.ids);
        finishPage();
    }

    // ---- Sections ----

    private void drawTickets(List<SavedPass> passes) {
        sectionHeading(context.getString(R.string.pdf_section_ticket));
        if (passes.isEmpty()) {
            text(context.getString(R.string.pdf_no_ticket), small, MARGIN, CONTENT_WIDTH);
            y += 12;
            return;
        }
        for (SavedPass saved : passes) {
            for (BoardingPass.Leg leg : saved.pass.legs) {
                drawTicket(saved, leg);
            }
        }
        y += 4;
    }

    /** One flight as a ticket-like box: details on the left, the barcode as a QR code on the right. */
    private void drawTicket(SavedPass saved, BoardingPass.Leg leg) {
        float height = 150;
        float qrSize = 112;
        ensureSpace(height + 10);
        RectF box = new RectF(MARGIN, y, MARGIN + CONTENT_WIDTH, y + height);
        fillPaint.setColor(TICKET_FILL);
        canvas.drawRoundRect(box, 10, 10, fillPaint);
        canvas.drawRoundRect(box, 10, 10, linePaint);

        float left = MARGIN + 16;
        float textWidth = CONTENT_WIDTH - qrSize - 48;
        float top = y + 14;
        drawAt(leg.flight() + " · " + LONG_DATE.format(leg.date), bodyBold, left, top, textWidth);
        drawAt(leg.from + "  →  " + leg.to, route, left, top + 18, textWidth);

        float fieldsTop = top + 56;
        float column = textWidth / 3;
        field(context.getString(R.string.pdf_passenger), saved.pass.passengerName(), left, fieldsTop, column * 2);
        field(context.getString(R.string.pdf_seat), leg.seat.isEmpty() ? "-" : leg.seat, left + column * 2,
                fieldsTop, column);
        field(context.getString(R.string.pdf_booking_ref), leg.pnr, left, fieldsTop + 32, column);
        field(context.getString(R.string.pdf_class), leg.compartment.isEmpty() ? "-" : leg.compartment,
                left + column, fieldsTop + 32, column);
        field(context.getString(R.string.pdf_sequence), leg.sequence.isEmpty() ? "-" : leg.sequence,
                left + column * 2, fieldsTop + 32, column);

        float qrLeft = MARGIN + CONTENT_WIDTH - qrSize - 16;
        drawQr(saved.rawData, qrLeft, y + 12, qrSize);
        drawAt(context.getString(R.string.pdf_barcode_caption), label, qrLeft, y + 12 + qrSize + 4, qrSize);

        y += height + 10;
    }

    private void drawTrip(@Nullable TripEntity trip, List<BlockEntity> blocks) {
        sectionHeading(context.getString(R.string.pdf_section_trip));
        if (trip == null) {
            text(context.getString(R.string.pdf_no_trip), small, MARGIN, CONTENT_WIDTH);
            y += 12;
            return;
        }
        text(trip.title, bodyBold, MARGIN, CONTENT_WIDTH);
        text(TripUi.tripSummary(context, trip, blocks), small, MARGIN, CONTENT_WIDTH);
        y += 8;
        float timeWidth = 92;
        for (BlockEntity block : blocks) {
            List<String> lines = new ArrayList<>();
            String booking = TripUi.bookingText(context, block);
            lines.add(booking + " · " + context.getString(TripUi.statusLabel(TripOverview.displayStatus(block))));
            String planned = TripUi.plannedText(context, block);
            if (planned != null) {
                lines.add(planned);
            }
            String details = String.join("\n", lines);
            float needed = measure(block.title, bodyBold, CONTENT_WIDTH - timeWidth)
                    + measure(details, small, CONTENT_WIDTH - timeWidth) + 10;
            ensureSpace(needed);
            float rowTop = y;
            String day = TripTime.dayHeader(TripTime.localDate(block.startTime), Locale.ENGLISH);
            drawAt(TripTime.timeRange(block.startTime, block.endTime) + "\n" + day, small, MARGIN, rowTop, timeWidth - 8);
            y = rowTop;
            text(block.title, bodyBold, MARGIN + timeWidth, CONTENT_WIDTH - timeWidth);
            text(details, small, MARGIN + timeWidth, CONTENT_WIDTH - timeWidth);
            y = Math.max(y, rowTop + 26) + 6;
            canvas.drawLine(MARGIN, y - 3, MARGIN + CONTENT_WIDTH, y - 3, linePaint);
        }
        y += 10;
    }

    private void drawIds(List<IdDocumentEntity> ids) {
        sectionHeading(context.getString(R.string.pdf_section_ids));
        if (ids.isEmpty()) {
            text(context.getString(R.string.pdf_no_ids), small, MARGIN, CONTENT_WIDTH);
            return;
        }
        LocalDate today = LocalDate.now(AppConfig.TRIP_ZONE);
        for (IdDocumentEntity id : ids) {
            String titleText;
            String number;
            String detail = id.holderName;
            if (IdDocuments.KIND_AADHAAR.equals(id.kind)) {
                titleText = context.getString(R.string.ids_title_aadhaar);
                number = IdDocuments.maskAadhaar(id.number);
            } else if (IdDocuments.KIND_PAN.equals(id.kind)) {
                titleText = context.getString(R.string.ids_title_pan);
                number = IdDocuments.maskPan(id.number);
            } else {
                titleText = context.getString(R.string.ids_title_visa, id.country == null ? "" : id.country);
                number = id.number;
                detail = id.holderName + "\n" + WalletIdText.visaDetail(context, id) + "\n"
                        + WalletIdText.visaStatus(context, id, today);
            }
            float needed = measure(titleText, small, CONTENT_WIDTH) + measure(number, bodyBold, CONTENT_WIDTH)
                    + measure(detail, body, CONTENT_WIDTH) + 12;
            ensureSpace(needed);
            text(titleText, small, MARGIN, CONTENT_WIDTH);
            text(number, bodyBold, MARGIN, CONTENT_WIDTH);
            text(detail, body, MARGIN, CONTENT_WIDTH);
            y += 6;
            canvas.drawLine(MARGIN, y - 3, MARGIN + CONTENT_WIDTH, y - 3, linePaint);
            y += 4;
        }
    }

    // ---- Drawing helpers ----

    private void sectionHeading(String text) {
        ensureSpace(60); // never leave a heading alone at the bottom of a page
        text(text, heading, MARGIN, CONTENT_WIDTH);
        y += 6;
    }

    /** A small grey label with its value under it. */
    private void field(String name, String value, float x, float top, float width) {
        drawAt(name, label, x, top, width);
        drawAt(value, bodyBold, x, top + 11, width);
    }

    /** Draws wrapped text at the cursor and moves the cursor below it. */
    private void text(String text, TextPaint paint, float x, float width) {
        StaticLayout layout = layout(text, paint, width);
        ensureSpace(layout.getHeight());
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
        y += layout.getHeight();
    }

    /** Draws wrapped text at a fixed place; the cursor does not move. */
    private void drawAt(String text, TextPaint paint, float x, float top, float width) {
        StaticLayout layout = layout(text, paint, width);
        canvas.save();
        canvas.translate(x, top);
        layout.draw(canvas);
        canvas.restore();
    }

    private float measure(String text, TextPaint paint, float width) {
        return layout(text, paint, width).getHeight();
    }

    private static StaticLayout layout(String text, TextPaint paint, float width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build();
    }

    /** The barcode text as a QR code, one small square per dark module (sharp at any zoom). */
    private void drawQr(String code, float left, float top, float size) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException e) {
            return; // cannot happen for boarding pass text; the details are on the page anyway
        }
        float module = size / matrix.getWidth();
        fillPaint.setColor(Color.WHITE);
        canvas.drawRect(left - 4, top - 4, left + size + 4, top + size + 4, fillPaint);
        fillPaint.setColor(Color.BLACK);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int col = 0; col < matrix.getWidth(); col++) {
                if (matrix.get(col, row)) {
                    float x = left + col * module;
                    float yTop = top + row * module;
                    canvas.drawRect(x, yTop, x + module + 0.05f, yTop + module + 0.05f, fillPaint);
                }
            }
        }
    }

    // ---- Pages ----

    private void ensureSpace(float height) {
        if (y + height > PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) {
            finishPage();
            startPage();
        }
    }

    private void startPage() {
        pageNumber++;
        page = pdf.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
        canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        y = MARGIN;
    }

    private void finishPage() {
        float footerTop = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT + 12;
        canvas.drawLine(MARGIN, footerTop - 6, MARGIN + CONTENT_WIDTH, footerTop - 6, linePaint);
        drawAt(context.getString(R.string.pdf_footer), label, MARGIN, footerTop, CONTENT_WIDTH - 60);
        String pageText = context.getString(R.string.pdf_page, pageNumber);
        drawAt(pageText, label, MARGIN + CONTENT_WIDTH - 50, footerTop, 50);
        pdf.finishPage(page);
    }

    private static TextPaint paint(float size, boolean bold, int color) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return paint;
    }

    /** "1 Oct 2026" */
    static String shortDate(LocalDate date) {
        return SHORT_DATE.format(date);
    }
}
