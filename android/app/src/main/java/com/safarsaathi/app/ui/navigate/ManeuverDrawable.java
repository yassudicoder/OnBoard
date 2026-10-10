package com.safarsaathi.app.ui.navigate;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.safarsaathi.routing.route.Maneuver;

/**
 * The big arrow in the turn-by-turn banner and the small one in the steps list ("turn left", "keep right",
 * "take the lift down", ...), drawn as thick rounded lines with an arrow head, like Google Maps. Drawn in one colour
 * (white in the green banner) on a 24 x 24 grid scaled to the drawable's size.
 */
public final class ManeuverDrawable extends Drawable {

    private static final float STROKE = 3.2f;

    private final Maneuver maneuver;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ManeuverDrawable(Maneuver maneuver, int color) {
        this.maneuver = maneuver;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(color);
        solid.setStyle(Paint.Style.FILL);
        solid.setColor(color);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
        switch (maneuver) {
            case LEFT:
                turn(canvas, false);
                break;
            case RIGHT:
                turn(canvas, true);
                break;
            case SLIGHT_LEFT:
                slight(canvas, false);
                break;
            case SLIGHT_RIGHT:
                slight(canvas, true);
                break;
            case SHARP_LEFT:
                sharp(canvas, false);
                break;
            case SHARP_RIGHT:
                sharp(canvas, true);
                break;
            case U_TURN:
                uTurn(canvas);
                break;
            case ARRIVE:
                pin(canvas);
                break;
            case SECURITY:
                shield(canvas);
                break;
            case TRAVELATOR:
                travelator(canvas);
                break;
            case LIFT_UP:
            case LIFT_DOWN:
                lift(canvas, maneuver == Maneuver.LIFT_UP);
                break;
            case ESCALATOR_UP:
            case ESCALATOR_DOWN:
            case STAIRS_UP:
            case STAIRS_DOWN:
                steps(canvas, maneuver == Maneuver.ESCALATOR_UP || maneuver == Maneuver.STAIRS_UP);
                break;
            case DEPART:
            default:
                straight(canvas);
                break;
        }
        canvas.restore();
    }

    private void straight(Canvas canvas) {
        canvas.drawLine(12, 21, 12, 7.5f, line);
        head(canvas, 12, 3.5f, 0, -1);
    }

    private void turn(Canvas canvas, boolean right) {
        Path path = new Path();
        float x = right ? 8 : 16;
        path.moveTo(x, 21);
        path.lineTo(x, 13);
        path.quadTo(x, 9.5f, right ? x + 3.5f : x - 3.5f, 9.5f);
        path.lineTo(right ? 16.5f : 7.5f, 9.5f);
        canvas.drawPath(path, line);
        head(canvas, right ? 20.5f : 3.5f, 9.5f, right ? 1 : -1, 0);
    }

    private void slight(Canvas canvas, boolean right) {
        float side = right ? 1 : -1;
        Path path = new Path();
        path.moveTo(12 - 2 * side, 21);
        path.lineTo(12 - 2 * side, 14);
        path.lineTo(12 + 3 * side, 9);
        canvas.drawPath(path, line);
        head(canvas, 12 + 5.8f * side, 6.2f, 0.707f * side, -0.707f);
    }

    private void sharp(Canvas canvas, boolean right) {
        float side = right ? 1 : -1;
        Path path = new Path();
        path.moveTo(12 - 3 * side, 21);
        path.lineTo(12 - 3 * side, 6);
        path.lineTo(12 + 3.5f * side, 12.5f);
        canvas.drawPath(path, line);
        head(canvas, 12 + 6.3f * side, 15.3f, 0.707f * side, 0.707f);
    }

    private void uTurn(Canvas canvas) {
        Path path = new Path();
        path.moveTo(16, 21);
        path.lineTo(16, 10);
        path.arcTo(new RectF(8, 6, 16, 14), 0, -180, false);
        path.lineTo(8, 14);
        canvas.drawPath(path, line);
        head(canvas, 8, 18.5f, 0, 1);
    }

    private void pin(Canvas canvas) {
        Path path = new Path();
        path.moveTo(12, 22);
        path.cubicTo(10.5f, 19, 5, 14.5f, 5, 9.5f);
        path.arcTo(new RectF(5, 2.5f, 19, 16.5f), 180, 180, false);
        path.cubicTo(19, 14.5f, 13.5f, 19, 12, 22);
        path.close();
        path.addCircle(12, 9.5f, 2.6f, Path.Direction.CCW);
        path.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(path, solid);
    }

    private void shield(Canvas canvas) {
        Path path = new Path();
        path.moveTo(12, 2.5f);
        path.lineTo(19.5f, 5.5f);
        path.lineTo(19.5f, 11);
        path.cubicTo(19.5f, 16, 16.2f, 20, 12, 21.5f);
        path.cubicTo(7.8f, 20, 4.5f, 16, 4.5f, 11);
        path.lineTo(4.5f, 5.5f);
        path.close();
        canvas.drawPath(path, line);
        Path tick = new Path();
        tick.moveTo(8.5f, 12);
        tick.lineTo(11, 14.5f);
        tick.lineTo(15.5f, 9.5f);
        canvas.drawPath(tick, line);
    }

    private void travelator(Canvas canvas) {
        canvas.drawRoundRect(new RectF(6, 3, 18, 21), 2.5f, 2.5f, line);
        for (float y : new float[] {9.5f, 15.5f}) {
            Path chevron = new Path();
            chevron.moveTo(9, y + 1.5f);
            chevron.lineTo(12, y - 1.5f);
            chevron.lineTo(15, y + 1.5f);
            canvas.drawPath(chevron, line);
        }
    }

    private void lift(Canvas canvas, boolean up) {
        canvas.drawRoundRect(new RectF(4.5f, 3, 19.5f, 21), 2.5f, 2.5f, line);
        Path triangle = new Path();
        if (up) {
            triangle.moveTo(12, 7);
            triangle.lineTo(16.5f, 13);
            triangle.lineTo(7.5f, 13);
        } else {
            triangle.moveTo(12, 17);
            triangle.lineTo(16.5f, 11);
            triangle.lineTo(7.5f, 11);
        }
        triangle.close();
        canvas.drawPath(triangle, solid);
    }

    private void steps(Canvas canvas, boolean up) {
        Path stairs = new Path();
        stairs.moveTo(3, 20);
        stairs.lineTo(8, 20);
        stairs.lineTo(8, 15);
        stairs.lineTo(13, 15);
        stairs.lineTo(13, 10);
        stairs.lineTo(18, 10);
        stairs.lineTo(18, 5);
        stairs.lineTo(21, 5);
        canvas.drawPath(stairs, line);
        if (up) {
            canvas.drawLine(4, 12, 7.5f, 8.5f, line);
            head(canvas, 9.5f, 6.5f, 0.707f, -0.707f);
        } else {
            canvas.drawLine(9.5f, 6.5f, 6, 10, line);
            head(canvas, 4, 12, -0.707f, 0.707f);
        }
    }

    /** A filled arrow head with its point at (x, y), pointing along the unit vector (dx, dy). */
    private void head(Canvas canvas, float x, float y, float dx, float dy) {
        float length = 6f;
        float half = 4.8f;
        float baseX = x - dx * length;
        float baseY = y - dy * length;
        Path triangle = new Path();
        triangle.moveTo(x, y);
        triangle.lineTo(baseX - dy * half, baseY + dx * half);
        triangle.lineTo(baseX + dy * half, baseY - dx * half);
        triangle.close();
        canvas.drawPath(triangle, solid);
    }

    @Override
    public void setAlpha(int alpha) {
        line.setAlpha(alpha);
        solid.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        line.setColorFilter(filter);
        solid.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
