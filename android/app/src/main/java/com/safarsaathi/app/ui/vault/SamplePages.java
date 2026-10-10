package com.safarsaathi.app.ui.vault;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.safarsaathi.app.data.vault.SampleVault;
import com.safarsaathi.app.data.vault.VaultRepository;

import java.io.ByteArrayOutputStream;

/**
 * Draws the page image of a SAMPLE document: a plain "paper" with its made-up details, a red "SAMPLE - NOT A REAL
 * DOCUMENT" band and a large diagonal "SAMPLE" across it, so nobody can mistake it for a real document.
 * The result is a JPEG, encrypted and fingerprinted exactly like a real scan.
 */
public final class SamplePages implements VaultRepository.PageRenderer {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 1400;
    private static final int MARGIN = 64;
    private static final int PAPER = Color.rgb(253, 251, 245);
    private static final int INK = Color.rgb(30, 30, 34);
    private static final int WARNING_RED = Color.rgb(176, 32, 24);

    @Override
    public byte[] render(SampleVault.SampleDocument document) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(PAPER);

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(6);
            border.setColor(INK);
            canvas.drawRect(MARGIN / 2f, MARGIN / 2f, WIDTH - MARGIN / 2f, HEIGHT - MARGIN / 2f, border);

            // The warning band at the top.
            Paint band = new Paint();
            band.setColor(WARNING_RED);
            canvas.drawRect(MARGIN / 2f, MARGIN / 2f, WIDTH - MARGIN / 2f, MARGIN / 2f + 96, band);
            Paint bandText = text(40, Color.WHITE, true);
            bandText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("SAMPLE - NOT A REAL DOCUMENT", WIDTH / 2f, MARGIN / 2f + 64, bandText);

            Paint title = text(48, INK, true);
            float y = MARGIN + 170;
            canvas.drawText(document.document.title, MARGIN, y, title);

            Paint line = text(36, INK, false);
            y += 40;
            for (String pageLine : document.pageLines) {
                y += 64;
                canvas.drawText(pageLine, MARGIN, y, line);
            }

            // A passport's machine-readable zone: two lines of 44 characters at the bottom, in a fixed-width font
            // (real passports use OCR-B), large enough for the phone's text recognition to read back.
            if (!document.mrzLines.isEmpty()) {
                Paint mrz = text(36, INK, false);
                mrz.setTypeface(Typeface.MONOSPACE);
                // Shrink until a whole line fits inside the page border.
                while (mrz.measureText(document.mrzLines.get(0)) > WIDTH - 2 * MARGIN - 16) {
                    mrz.setTextSize(mrz.getTextSize() - 1);
                }
                float mrzY = HEIGHT - MARGIN - 110;
                for (String mrzLine : document.mrzLines) {
                    canvas.drawText(mrzLine, MARGIN, mrzY, mrz);
                    mrzY += 60;
                }
            }

            // Big diagonal watermark across the middle.
            Paint watermark = text(220, Color.argb(60, 176, 32, 24), true);
            watermark.setTextAlign(Paint.Align.CENTER);
            canvas.save();
            canvas.rotate(-30, WIDTH / 2f, HEIGHT / 2f);
            canvas.drawText("SAMPLE", WIDTH / 2f, HEIGHT / 2f + 80, watermark);
            canvas.restore();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            return out.toByteArray();
        } finally {
            bitmap.recycle();
        }
    }

    private static Paint text(float size, int color, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return paint;
    }
}
