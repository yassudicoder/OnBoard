package com.safarsaathi.app.ui.help;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.data.baggage.BagIssue;
import com.safarsaathi.app.data.baggage.BaggageFacts;
import com.safarsaathi.app.data.baggage.ClaimDeadline;
import com.safarsaathi.app.data.baggage.ClaimDraft;
import com.safarsaathi.app.data.baggage.EvidencePhoto;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The claim pack: one A4 PDF made on the phone with Android's own {@link PdfDocument} (works offline) that the
 * traveller attaches when writing to the airline. It holds the details, the deadline with its rule and source, every
 * photo (before and after) with when it was added and its full SHA-256 fingerprint, the "show to staff" QR code and
 * the escalation steps. Every page says who made it and that it is not an airline document or legal advice. Always
 * light (white paper) with fixed text sizes, like the Wallet's travel pack.
 */
final class ClaimPackPdf {

    /** Everything the PDF shows, copied on the main thread. */
    static final class Content {
        final String issueTitle;
        final ClaimDraft draft;
        final BaggageFacts facts;
        @Nullable
        final ClaimDeadline deadline;
        final String staffText;
        /** Photo file name to the file on the phone. */
        final Map<String, File> photoFiles;
        final Instant generatedAt;

        Content(String issueTitle, ClaimDraft draft, BaggageFacts facts, @Nullable ClaimDeadline deadline,
                String staffText, Map<String, File> photoFiles, Instant generatedAt) {
            this.issueTitle = issueTitle;
            this.draft = draft;
            this.facts = facts;
            this.deadline = deadline;
            this.staffText = staffText;
            this.photoFiles = photoFiles;
            this.generatedAt = generatedAt;
        }
    }

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 40;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float FOOTER_HEIGHT = 36;

    private static final int INK = Color.rgb(0x1A, 0x1B, 0x1E);
    private static final int GREY = Color.rgb(0x44, 0x47, 0x4E);
    private static final int BRAND = Color.rgb(0x1B, 0x4D, 0xFF);
    private static final int LINE = Color.rgb(0xC4, 0xC6, 0xCF);
    private static final int WARN_FILL = Color.rgb(0xFF, 0xF1, 0xE6);

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.ENGLISH);

    private final PdfDocument pdf = new PdfDocument();
    private final TextPaint title = paint(20, true, BRAND);
    private final TextPaint heading = paint(13, true, INK);
    private final TextPaint body = paint(10.5f, false, INK);
    private final TextPaint bodyBold = paint(10.5f, true, INK);
    private final TextPaint small = paint(8.5f, false, GREY);
    private final TextPaint mono = monoPaint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private PdfDocument.Page page;
    private Canvas canvas;
    private float y;
    private int pageNumber;

    private ClaimPackPdf() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(0.8f);
        linePaint.setColor(LINE);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    static void write(Content content, OutputStream out) throws IOException {
        ClaimPackPdf writer = new ClaimPackPdf();
        try {
            writer.draw(content);
            writer.pdf.writeTo(out);
        } finally {
            writer.pdf.close();
        }
    }

    private void draw(Content c) {
        newPage();
        text(title, "Baggage claim pack: " + c.issueTitle);
        text(small, "Made by the passenger with OnBoard on " + STAMP.format(c.generatedAt.atZone(AppConfig.TRIP_ZONE))
                + " (phone time). Not an airline document. Information only, not legal advice.");
        y += 8;

        section("Details");
        ClaimDraft d = c.draft;
        row("Problem", c.issueTitle);
        row("Airline", d.airline);
        row("Flight", d.flightNo);
        row("Bag tag", d.bagTag.replace(" ", ""));
        row("PIR reference", d.pirRef);
        row(d.issue() == BagIssue.DELAY ? "Flight landed" : "Bag received", d.date);
        if (d.issue() == BagIssue.DELAY) {
            row("Bag delivered", d.deliveredDate.trim().isEmpty() ? "Not yet" : d.deliveredDate);
        }
        row("What is damaged or missing", d.description);

        section("Deadline");
        drawDeadline(c);

        section("Photos");
        text(small, "Each photo's SHA-256 fingerprint was computed on the phone when it was added. Re-compute it from the "
                + "original file to check the photo has not been changed. \"Added\" is the phone's clock; \"in the file\" is "
                + "the camera's own data, which can be edited. No server signature yet (OnBoard Baggage Shield B2).");
        drawPhotos(c, EvidencePhoto.Stage.BEFORE, "Before the bag was handed over");
        drawPhotos(c, EvidencePhoto.Stage.AFTER, "On arrival");

        section("Show to staff");
        drawQr(c.staffText);

        section("If the airline doesn't help (information only)");
        for (BaggageFacts.Step step : c.facts.escalation) {
            text(bodyBold, step.title);
            text(body, step.detail);
            String source = step.url + (step.sourceNote.isEmpty() ? "" : (step.url.isEmpty() ? "" : " · ") + step.sourceNote);
            if (!source.trim().isEmpty()) {
                text(small, source + " (checked " + step.lastChecked + ")");
            }
            y += 4;
        }
        finishPage();
    }

    private void drawDeadline(Content c) {
        BaggageFacts.Deadlines rules = c.facts.deadlines;
        ClaimDeadline deadline = c.deadline;
        String main;
        if (deadline == null) {
            main = "Not worked out: the date was not filled in.";
        } else if (deadline.kind == ClaimDeadline.Kind.COMPLAIN_BY) {
            main = "Complain to the airline in writing by " + LONG_DATE.format(deadline.date) + ".";
        } else {
            main = "Bag still missing. From " + LONG_DATE.format(deadline.date)
                    + " it may be treated as lost. Once delivered, the delay complaint is due within "
                    + rules.delayDays + " days of delivery.";
        }
        ensureSpace(40);
        StaticLayout layout = layout(bodyBold, main, CONTENT_WIDTH - 16);
        fillPaint.setColor(WARN_FILL);
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + layout.getHeight() + 12, fillPaint);
        canvas.save();
        canvas.translate(MARGIN + 8, y + 6);
        layout.draw(canvas);
        canvas.restore();
        y += layout.getHeight() + 18;
        if (c.draft.issue() == BagIssue.PILFERAGE) {
            text(body, "The Convention names damage, not missing items: ask the airline which limit it uses.");
        }
        text(small, "Rule: " + rules.rule + ". " + rules.appliesTo);
        text(small, "\"" + (c.draft.issue() == BagIssue.DELAY ? rules.delayQuote + "\" / \"" + rules.lostQuote
                : rules.damageQuote) + "\"");
        text(small, "Source: " + rules.sourceUrl + " · " + rules.sourceNote + " (checked " + rules.lastChecked + ")");
    }

    private void drawPhotos(Content c, EvidencePhoto.Stage stage, String label) {
        List<EvidencePhoto> photos = c.draft.photos(stage);
        y += 4;
        text(bodyBold, label + " (" + photos.size() + ")");
        if (photos.isEmpty()) {
            text(body, "No photos.");
            return;
        }
        float box = (CONTENT_WIDTH - 16) / 2;
        float imageHeight = box * 0.75f;
        for (int i = 0; i < photos.size(); i += 2) {
            ensureSpace(imageHeight + 70);
            float rowTop = y;
            float rowBottom = rowTop;
            for (int col = 0; col < 2 && i + col < photos.size(); col++) {
                EvidencePhoto photo = photos.get(i + col);
                float left = MARGIN + col * (box + 16);
                drawImage(c.photoFiles.get(photo.fileName), left, rowTop, box, imageHeight);
                float captionTop = rowTop + imageHeight + 4;
                String caption = "Added " + BagClaimFragment.addedTime(photo)
                        + (photo.takenAt == null ? "" : " · in the file " + BagClaimFragment.takenTime(photo))
                        + " · " + ("CAMERA".equals(photo.origin) ? "taken in OnBoard" : "from the gallery");
                StaticLayout captionLayout = layout(small, caption, box);
                StaticLayout hashLayout = layout(mono, "SHA-256 " + photo.sha256, box);
                canvas.save();
                canvas.translate(left, captionTop);
                captionLayout.draw(canvas);
                canvas.translate(0, captionLayout.getHeight() + 2);
                hashLayout.draw(canvas);
                canvas.restore();
                rowBottom = Math.max(rowBottom, captionTop + captionLayout.getHeight() + 2 + hashLayout.getHeight());
            }
            y = rowBottom + 12;
        }
    }

    private void drawImage(@Nullable File file, float left, float top, float width, float height) {
        canvas.drawRect(left, top, left + width, top + height, linePaint);
        if (file == null || !file.exists()) {
            return;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 900 || bounds.outHeight / (sample * 2) >= 900) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            return;
        }
        float scale = Math.min(width / bitmap.getWidth(), height / bitmap.getHeight());
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        float x = left + (width - w) / 2;
        float yTop = top + (height - h) / 2;
        canvas.drawBitmap(bitmap, null, new RectF(x, yTop, x + w, yTop + h), imagePaint);
        bitmap.recycle();
    }

    private void drawQr(String text) {
        float size = 130;
        ensureSpace(size + 10);
        Bitmap qr = BagClaimFragment.qrBitmap(text, 520);
        if (qr != null) {
            canvas.drawBitmap(qr, null, new RectF(MARGIN, y, MARGIN + size, y + size), null);
        }
        StaticLayout layout = layout(small, text, CONTENT_WIDTH - size - 16);
        canvas.save();
        canvas.translate(MARGIN + size + 16, y);
        layout.draw(canvas);
        canvas.restore();
        y += Math.max(size, layout.getHeight()) + 10;
    }

    // ---- Text helpers ----

    private void section(String name) {
        ensureSpace(40);
        y += 10;
        text(heading, name);
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint);
        y += 6;
    }

    private void row(String label, @Nullable String value) {
        String shown = value == null || value.trim().isEmpty() ? "Not filled in" : value.trim();
        StaticLayout labelLayout = layout(small, label, 150);
        StaticLayout valueLayout = layout(body, shown, CONTENT_WIDTH - 160);
        float height = Math.max(labelLayout.getHeight(), valueLayout.getHeight());
        ensureSpace(height + 4);
        canvas.save();
        canvas.translate(MARGIN, y + 1);
        labelLayout.draw(canvas);
        canvas.restore();
        canvas.save();
        canvas.translate(MARGIN + 160, y);
        valueLayout.draw(canvas);
        canvas.restore();
        y += height + 4;
    }

    private void text(TextPaint paint, String text) {
        StaticLayout layout = layout(paint, text, CONTENT_WIDTH);
        ensureSpace(layout.getHeight());
        canvas.save();
        canvas.translate(MARGIN, y);
        layout.draw(canvas);
        canvas.restore();
        y += layout.getHeight() + 3;
    }

    private static StaticLayout layout(TextPaint paint, String text, float width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build();
    }

    private static TextPaint paint(float size, boolean bold, int color) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return paint;
    }

    private static TextPaint monoPaint() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(7.5f);
        paint.setColor(GREY);
        paint.setTypeface(Typeface.MONOSPACE);
        return paint;
    }

    // ---- Pages ----

    private void ensureSpace(float height) {
        if (y + height > PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) {
            finishPage();
            newPage();
        }
    }

    private void newPage() {
        pageNumber++;
        page = pdf.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
        canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        y = MARGIN;
    }

    private void finishPage() {
        float footerTop = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT + 12;
        canvas.drawLine(MARGIN, footerTop, MARGIN + CONTENT_WIDTH, footerTop, linePaint);
        StaticLayout footer = layout(small, "OnBoard claim pack · made by the passenger on their phone · not an airline "
                + "document · not legal advice · page " + pageNumber, CONTENT_WIDTH);
        canvas.save();
        canvas.translate(MARGIN, footerTop + 4);
        footer.draw(canvas);
        canvas.restore();
        pdf.finishPage(page);
    }
}
