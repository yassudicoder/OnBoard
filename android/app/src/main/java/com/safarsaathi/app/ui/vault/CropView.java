package com.safarsaathi.app.ui.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.safarsaathi.app.data.vault.capture.CropMath;

/**
 * The manual crop: shows a photo with four round handles on the document's corners. The traveller drags each handle
 * onto a corner of the paper; {@link #warp} then straightens that four-sided area into a flat page (a "perspective
 * correction", like Google's scanner does by itself).
 *
 * <p>Corners are kept in PHOTO pixels, in the order top-left, top-right, bottom-right, bottom-left. The screen
 * offers "Use the whole picture" as the accessible alternative to dragging.
 */
public class CropView extends View {

    /** How close (in dp) a finger must land to a handle to grab it: the 48dp touch-target rule, as a radius. */
    private static final float GRAB_RADIUS_DP = 32;
    private static final float HANDLE_RADIUS_DP = 12;

    @Nullable
    private Bitmap photo;
    private float[] corners = new float[8];
    private final Matrix photoToView = new Matrix();
    private final Matrix viewToPhoto = new Matrix();
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint();
    private final Path outline = new Path();
    private int dragging = -1;

    public CropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        int accent = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.BLUE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3 * density);
        linePaint.setColor(accent);
        handlePaint.setColor(accent);
        shadePaint.setColor(Color.argb(110, 0, 0, 0));
    }

    /** Shows {@code bitmap} with the handles at {@code startCorners} (photo pixels, see the class comment). */
    public void setPhoto(Bitmap bitmap, float[] startCorners) {
        photo = bitmap;
        corners = startCorners.clone();
        updateMatrices();
        invalidate();
    }

    public float[] getCorners() {
        return corners.clone();
    }

    public void setCorners(float[] newCorners) {
        corners = newCorners.clone();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateMatrices();
    }

    /** Fit the whole photo in the view, centred (like an ImageView with fitCenter). */
    private void updateMatrices() {
        if (photo == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float scale = Math.min((float) getWidth() / photo.getWidth(), (float) getHeight() / photo.getHeight());
        float dx = (getWidth() - photo.getWidth() * scale) / 2f;
        float dy = (getHeight() - photo.getHeight() * scale) / 2f;
        photoToView.setScale(scale, scale);
        photoToView.postTranslate(dx, dy);
        photoToView.invert(viewToPhoto);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (photo == null) {
            return;
        }
        canvas.drawBitmap(photo, photoToView, photoPaint);
        float[] onScreen = corners.clone();
        photoToView.mapPoints(onScreen);

        // Darken everything outside the chosen area, so the page stands out.
        outline.reset();
        outline.moveTo(onScreen[0], onScreen[1]);
        for (int i = 1; i < 4; i++) {
            outline.lineTo(onScreen[2 * i], onScreen[2 * i + 1]);
        }
        outline.close();
        Path shade = new Path();
        shade.setFillType(Path.FillType.EVEN_ODD);
        shade.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        shade.addPath(outline);
        canvas.drawPath(shade, shadePaint);
        canvas.drawPath(outline, linePaint);

        float radius = HANDLE_RADIUS_DP * getResources().getDisplayMetrics().density;
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(onScreen[2 * i], onScreen[2 * i + 1], radius, handlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (photo == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = nearestHandle(event.getX(), event.getY());
                if (dragging >= 0) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) {
                    float[] point = {event.getX(), event.getY()};
                    viewToPhoto.mapPoints(point);
                    corners[2 * dragging] = CropMath.clamp(point[0], 0, photo.getWidth());
                    corners[2 * dragging + 1] = CropMath.clamp(point[1], 0, photo.getHeight());
                    invalidate();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasDragging = dragging >= 0;
                dragging = -1;
                if (wasDragging) {
                    performClick();
                }
                return wasDragging;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int nearestHandle(float x, float y) {
        float[] onScreen = corners.clone();
        photoToView.mapPoints(onScreen);
        float grab = GRAB_RADIUS_DP * getResources().getDisplayMetrics().density;
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double distance = Math.hypot(onScreen[2 * i] - x, onScreen[2 * i + 1] - y);
            if (distance <= grab && distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    /**
     * Straightens the area inside {@code corners} into an upright page, at most {@code maxSide} pixels on its longer
     * side. Android's Matrix.setPolyToPoly works out the "perspective" mapping from the four photo corners to the
     * four corners of the new page.
     */
    public static Bitmap warp(Bitmap photo, float[] corners, int maxSide) {
        int[] size = CropMath.outputSize(corners, maxSide);
        Bitmap page = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        float[] target = {0, 0, size[0], 0, size[0], size[1], 0, size[1]};
        Matrix mapping = new Matrix();
        mapping.setPolyToPoly(corners, 0, target, 0, 4);
        Canvas canvas = new Canvas(page);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(photo, mapping, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return page;
    }
}
