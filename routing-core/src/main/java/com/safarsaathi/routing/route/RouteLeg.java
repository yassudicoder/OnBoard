package com.safarsaathi.routing.route;

import com.safarsaathi.routing.map.MapEdge;
import com.safarsaathi.routing.map.MapNode;

/** One walkway of a route, in the direction it is walked, with where it starts along the route. */
public final class RouteLeg {

    public final MapNode from;
    public final MapNode to;
    public final MapEdge edge;
    /** Length in metres. */
    public final double metres;
    /** Time it takes with the route's profile (estimate). */
    public final double seconds;
    /** Metres walked along the route before this leg starts. */
    public final double startMetres;
    /** Seconds spent along the route before this leg starts. */
    public final double startSeconds;
    /** Direction walked, in degrees clockwise from the map's north; NaN for a lift / escalator / stairs ride. */
    public final double bearing;

    RouteLeg(MapNode from, MapNode to, MapEdge edge, double seconds, double startMetres, double startSeconds) {
        this.from = from;
        this.to = to;
        this.edge = edge;
        this.metres = edge.distanceM;
        this.seconds = seconds;
        this.startMetres = startMetres;
        this.startSeconds = startSeconds;
        this.bearing = changesFloor() ? Double.NaN : bearing(from, to);
    }

    public boolean changesFloor() {
        return !from.floor.equals(to.floor);
    }

    /** Degrees clockwise from north (0 = north, 90 = east), in [0, 360); NaN when both places are at the same spot. */
    static double bearing(MapNode from, MapNode to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) {
            return Double.NaN;
        }
        double degrees = Math.toDegrees(Math.atan2(dx, dy));
        return degrees < 0 ? degrees + 360 : degrees;
    }

    @Override
    public String toString() {
        return from.id + " -> " + to.id + " (" + edge.kind + ", " + metres + " m)";
    }
}
