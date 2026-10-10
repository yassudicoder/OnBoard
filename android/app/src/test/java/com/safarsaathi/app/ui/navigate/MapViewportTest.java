package com.safarsaathi.app.ui.navigate;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Unit tests for the map camera's metres <-> pixels maths ({@link MapViewport}), with and without rotation. */
public class MapViewportTest {

    private static final float DELTA = 0.01f;

    /** A 300 m x 150 m floor in a 1000 x 800 px view with 50 px padding: 3 px per metre, centred. */
    private static MapViewport floor() {
        MapViewport viewport = new MapViewport();
        viewport.setContent(0, 0, 300, 150);
        viewport.setViewSize(1000, 800, 50);
        viewport.fit();
        return viewport;
    }

    @Test
    public void fit_showsTheWholeFloorCentred_northUp() {
        MapViewport viewport = floor();

        assertEquals(3f, viewport.scale(), DELTA);
        assertEquals(50f, viewport.toScreenX(0, 75), DELTA);     // west edge
        assertEquals(950f, viewport.toScreenX(300, 75), DELTA);  // east edge
        assertEquals(175f, viewport.toScreenY(150, 150), DELTA); // north edge at the top
        assertEquals(625f, viewport.toScreenY(0, 0), DELTA);     // south edge at the bottom
    }

    @Test
    public void fit_betweenOverlays_usesOnlyTheFreeArea() {
        MapViewport viewport = floor();
        viewport.fit(200, 800); // a search bar covers the top 200 px

        assertEquals(500f, viewport.toScreenY(150, 75), DELTA); // middle of the floor in the middle of 200..800
    }

    @Test
    public void screenAndMap_areInverse_evenWhenRotated() {
        MapViewport viewport = floor();
        viewport.panBy(37, -12);
        viewport.zoomBy(1.7f, 400, 300);
        viewport.setCamera(120, 60, viewport.scale(), 33);

        float sx = viewport.toScreenX(123.4, 56.7);
        float sy = viewport.toScreenY(123.4, 56.7);
        assertEquals(123.4, viewport.toMapX(sx, sy), 0.001);
        assertEquals(56.7, viewport.toMapY(sx, sy), 0.001);
    }

    @Test
    public void bearing_putsTheWalkingDirectionUp() {
        MapViewport viewport = floor();
        viewport.setCamera(100, 50, 3, 90); // walking east

        // 10 m east of the centre is drawn straight ABOVE the anchor.
        assertEquals(viewport.anchorX(), viewport.toScreenX(110, 50), DELTA);
        assertEquals(viewport.anchorY() - 30, viewport.toScreenY(110, 50), DELTA);
        // 10 m north is on the LEFT (facing east, north is to your left).
        assertEquals(viewport.anchorX() - 30, viewport.toScreenX(100, 60), DELTA);
    }

    @Test
    public void zoom_keepsThePointUnderTheFingers_andStaysInItsLimits() {
        MapViewport viewport = floor();
        viewport.setCamera(150, 75, 3, 40);
        double mapX = viewport.toMapX(600, 400);
        double mapY = viewport.toMapY(600, 400);

        viewport.zoomBy(2f, 600, 400);

        assertEquals(600f, viewport.toScreenX(mapX, mapY), DELTA);
        assertEquals(400f, viewport.toScreenY(mapX, mapY), DELTA);
        viewport.zoomBy(100f, 600, 400);
        assertEquals(MapViewport.MAX_ZOOM, viewport.zoom(), DELTA);
        viewport.zoomBy(0.0001f, 600, 400);
        assertEquals(MapViewport.MIN_ZOOM, viewport.zoom(), DELTA);
    }

    @Test
    public void pan_movesTheMapWithTheFinger_whenRotated() {
        MapViewport viewport = floor();
        viewport.setCamera(150, 75, 3, 125);
        float beforeX = viewport.toScreenX(200, 100);
        float beforeY = viewport.toScreenY(200, 100);

        viewport.panBy(40, -25);

        assertEquals(beforeX + 40, viewport.toScreenX(200, 100), DELTA);
        assertEquals(beforeY - 25, viewport.toScreenY(200, 100), DELTA);
    }

    @Test
    public void setAnchor_doesNotMoveTheMap() {
        MapViewport viewport = floor();
        viewport.setCamera(150, 75, 4, 60);
        float beforeX = viewport.toScreenX(10, 20);
        float beforeY = viewport.toScreenY(10, 20);

        viewport.setAnchor(500, 560);

        assertEquals(beforeX, viewport.toScreenX(10, 20), DELTA);
        assertEquals(beforeY, viewport.toScreenY(10, 20), DELTA);
    }

    @Test
    public void centerOn_putsAPlaceAtTheAnchor() {
        MapViewport viewport = floor();
        viewport.zoomBy(3f, 0, 0);

        viewport.centerOn(280, 110);

        assertEquals(viewport.anchorX(), viewport.toScreenX(280, 110), DELTA);
        assertEquals(viewport.anchorY(), viewport.toScreenY(280, 110), DELTA);
    }

    @Test
    public void fitBounds_showsARouteNorthUp() {
        MapViewport viewport = floor();
        viewport.setCamera(0, 0, 9, 200);

        viewport.fitBounds(100, 20, 160, 120, 100, 700);

        assertEquals(0f, viewport.bearing(), DELTA);
        assertEquals(400f, viewport.toScreenY(130, 70), DELTA); // middle of the box in the middle of 100..700
        assertEquals(500f, viewport.toScreenX(130, 70), DELTA);
        assertEquals(5f, viewport.scale(), DELTA); // 100 m tall box in 600 - 2 x 50 px = 500 px
    }
}
