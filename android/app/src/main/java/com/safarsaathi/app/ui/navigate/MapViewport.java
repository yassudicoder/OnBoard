package com.safarsaathi.app.ui.navigate;

/**
 * The map's "camera": turns map metres into screen pixels and back, with zoom, pan and rotation. Plain Java (no
 * Android), checked by MapViewportTest.
 *
 * <p>The camera looks at the map point (centerX, centerY) and draws it at the screen point (anchorX, anchorY):
 * the middle of the view when exploring, lower down while navigating (like Google Maps, so you see more of what is
 * ahead). {@code bearing} is the map direction that points up on screen, in degrees clockwise from the map's north:
 * 0 = north up; while navigating it is the walking direction, so the route always goes "up".
 * The map's y grows to the north and the screen's y grows downwards, so y is flipped.
 */
public final class MapViewport {

    /** Zoom limits, relative to the scale that shows the whole floor. */
    public static final float MIN_ZOOM = 0.8f;
    public static final float MAX_ZOOM = 12f;

    private double minX;
    private double minY;
    private double maxX = 1;
    private double maxY = 1;
    private float viewWidth = 1;
    private float viewHeight = 1;
    private float padding;

    /** Pixels per metre when the whole floor fits; the zoom limits are relative to it. */
    private float fitScale = 1;
    private float scale = 1;
    private double centerX;
    private double centerY;
    private float bearing;
    private float anchorX;
    private float anchorY;
    /** cos/sin of the bearing, kept up to date by {@link #setBearing}. */
    private double cos = 1;
    private double sin = 0;

    /** The area of the floor, in metres. */
    public void setContent(double minX, double minY, double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = Math.max(maxX, minX + 1);
        this.maxY = Math.max(maxY, minY + 1);
    }

    /** The view's size in pixels and the empty border to keep around the content when fitting. */
    public void setViewSize(float width, float height, float padding) {
        this.viewWidth = Math.max(1, width);
        this.viewHeight = Math.max(1, height);
        this.padding = padding;
    }

    /** Shows the whole floor, north up, in the screen area between {@code top} and {@code bottom} pixels. */
    public void fit(float top, float bottom) {
        fitScale = scaleToFit(maxX - minX, maxY - minY, top, bottom);
        fitBounds(minX, minY, maxX, maxY, top, bottom);
    }

    /** Shows the whole floor, north up, centred in the view. */
    public void fit() {
        fit(0, viewHeight);
    }

    /**
     * Shows a box of the map (e.g. a route), north up, as large as it fits in the screen area between {@code top}
     * and {@code bottom} pixels (but not closer than {@link #MAX_ZOOM}).
     */
    public void fitBounds(double boxMinX, double boxMinY, double boxMaxX, double boxMaxY, float top, float bottom) {
        float usableTop = Math.max(0, Math.min(top, viewHeight - 1));
        float usableBottom = Math.max(usableTop + 1, Math.min(bottom, viewHeight));
        setBearing(0);
        scale = clampScale(scaleToFit(Math.max(boxMaxX - boxMinX, 1), Math.max(boxMaxY - boxMinY, 1),
                usableTop, usableBottom));
        centerX = (boxMinX + boxMaxX) / 2;
        centerY = (boxMinY + boxMaxY) / 2;
        anchorX = viewWidth / 2;
        anchorY = (usableTop + usableBottom) / 2;
    }

    private float scaleToFit(double width, double height, float top, float bottom) {
        float usableWidth = Math.max(1, viewWidth - 2 * padding);
        float usableHeight = Math.max(1, (bottom - top) - 2 * padding);
        return (float) Math.min(usableWidth / width, usableHeight / height);
    }

    // ---- Reading the camera ----

    /** Pixels per metre right now. */
    public float scale() {
        return scale;
    }

    /** Scale relative to the whole-floor view (1 = whole floor). */
    public float zoom() {
        return scale / fitScale;
    }

    public float fitScale() {
        return fitScale;
    }

    public float bearing() {
        return bearing;
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public float anchorX() {
        return anchorX;
    }

    public float anchorY() {
        return anchorY;
    }

    // ---- Converting ----

    public float toScreenX(double x, double y) {
        double dx = x - centerX;
        double dy = y - centerY;
        return (float) (anchorX + (dx * cos - dy * sin) * scale);
    }

    public float toScreenY(double x, double y) {
        double dx = x - centerX;
        double dy = y - centerY;
        return (float) (anchorY - (dx * sin + dy * cos) * scale);
    }

    public double toMapX(float screenX, float screenY) {
        double rx = (screenX - anchorX) / scale;
        double ry = (anchorY - screenY) / scale;
        return centerX + rx * cos + ry * sin;
    }

    public double toMapY(float screenX, float screenY) {
        double rx = (screenX - anchorX) / scale;
        double ry = (anchorY - screenY) / scale;
        return centerY - rx * sin + ry * cos;
    }

    // ---- Moving the camera ----

    /** Points the camera at once (used by animations); the scale stays inside the zoom limits. */
    public void setCamera(double centerX, double centerY, float scale, float bearing) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.scale = clampScale(scale);
        setBearing(bearing);
    }

    /** Moves where the camera's centre is drawn, without moving the map on screen. */
    public void setAnchor(float x, float y) {
        double mapX = toMapX(x, y);
        double mapY = toMapY(x, y);
        anchorX = x;
        anchorY = y;
        centerX = mapX;
        centerY = mapY;
    }

    /** Moves the map by this many pixels (a finger drag). */
    public void panBy(float dx, float dy) {
        double mapX = toMapX(anchorX - dx, anchorY - dy);
        double mapY = toMapY(anchorX - dx, anchorY - dy);
        centerX = mapX;
        centerY = mapY;
    }

    /**
     * Zooms by {@code factor} around the screen point (focusX, focusY): the map point under the fingers stays under the
     * fingers. The zoom stays between {@link #MIN_ZOOM} and {@link #MAX_ZOOM}.
     */
    public void zoomBy(float factor, float focusX, float focusY) {
        double mapX = toMapX(focusX, focusY);
        double mapY = toMapY(focusX, focusY);
        scale = clampScale(scale * factor);
        // Move the centre so that (mapX, mapY) is drawn at the focus again.
        double rx = (focusX - anchorX) / scale;
        double ry = (anchorY - focusY) / scale;
        centerX = mapX - (rx * cos + ry * sin);
        centerY = mapY - (-rx * sin + ry * cos);
    }

    /** Moves the map so the map point (x, y) is drawn at the anchor (the middle of the view when exploring). */
    public void centerOn(double x, double y) {
        centerX = x;
        centerY = y;
    }

    private void setBearing(float degrees) {
        float normalised = degrees % 360;
        if (normalised < 0) {
            normalised += 360;
        }
        bearing = normalised;
        double radians = Math.toRadians(normalised);
        cos = Math.cos(radians);
        sin = Math.sin(radians);
    }

    private float clampScale(float value) {
        return Math.max(fitScale * MIN_ZOOM, Math.min(fitScale * MAX_ZOOM, value));
    }
}
