package com.safarsaathi.routing.route;

/** A point somewhere along a route: where to draw the traveller and which way they are walking. */
public final class RoutePoint {

    /** Metres in the map's local frame. */
    public final double x;
    public final double y;
    public final String floor;
    /** Walking direction in degrees clockwise from the map's north (kept from the last walkway during a lift ride). */
    public final double bearing;
    /** Index of the route leg the point is on. */
    public final int legIndex;

    RoutePoint(double x, double y, String floor, double bearing, int legIndex) {
        this.x = x;
        this.y = y;
        this.floor = floor;
        this.bearing = bearing;
        this.legIndex = legIndex;
    }
}
