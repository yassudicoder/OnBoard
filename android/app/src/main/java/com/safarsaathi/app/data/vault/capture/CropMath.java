package com.safarsaathi.app.data.vault.capture;

/**
 * The arithmetic of the manual crop screen (used when Google's scanner is not available): the four corners of the
 * document in the photo, always in the order top-left, top-right, bottom-right, bottom-left, as
 * {@code {x0, y0, x1, y1, x2, y2, x3, y3}} in image pixels. Plain Java, unit-tested.
 */
public final class CropMath {

    private CropMath() {
    }

    /** Corners a little inside the photo's edges: a good first guess for a document filling most of the photo. */
    public static float[] insetCorners(int width, int height, float insetFraction) {
        float dx = width * insetFraction;
        float dy = height * insetFraction;
        return new float[] {dx, dy, width - dx, dy, width - dx, height - dy, dx, height - dy};
    }

    /** The whole photo ("use the whole picture", also the accessible way to skip cropping). */
    public static float[] fullCorners(int width, int height) {
        return insetCorners(width, height, 0f);
    }

    /**
     * Width and height of the straightened page: the longer of the two opposite edges, so nothing is squeezed. Scaled
     * down (keeping the shape) so the longer side is at most {@code maxSide}, which keeps saved pages a sensible size.
     */
    public static int[] outputSize(float[] corners, int maxSide) {
        double top = distance(corners, 0, 1);
        double bottom = distance(corners, 3, 2);
        double left = distance(corners, 0, 3);
        double right = distance(corners, 1, 2);
        double width = Math.max(top, bottom);
        double height = Math.max(left, right);
        double scale = Math.min(1.0, maxSide / Math.max(width, height));
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    /** Keeps a dragged corner inside the photo. */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distance(float[] corners, int a, int b) {
        return Math.hypot(corners[2 * a] - corners[2 * b], corners[2 * a + 1] - corners[2 * b + 1]);
    }
}
