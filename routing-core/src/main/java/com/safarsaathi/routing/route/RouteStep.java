package com.safarsaathi.routing.route;

import com.safarsaathi.routing.map.MapNode;

/**
 * One instruction of a route, e.g. "Turn left at Central hall", tied to the place where it happens. The words come
 * from {@link InstructionText}; this class only holds the facts.
 */
public final class RouteStep {

    public final Maneuver maneuver;
    /** The place where the step happens (the turn, the lift, the destination). */
    public final MapNode at;
    /** The place named in the instruction: {@link #at}, or for "Head towards ..." / travelator the place ahead. */
    public final MapNode landmark;
    /** For a lift, escalator or stairs: the floor id it goes to; otherwise null. */
    public final String toFloor;
    /** Metres along the route where the step happens. */
    public final double startMetres;
    /** Metres to walk after this step until the next one (0 for the arrival). */
    public final double distanceM;
    /** For a turn: how far, in degrees (+ = right, - = left); otherwise 0. */
    public final double turnDegrees;

    RouteStep(Maneuver maneuver, MapNode at, MapNode landmark, String toFloor, double startMetres, double distanceM,
              double turnDegrees) {
        this.maneuver = maneuver;
        this.at = at;
        this.landmark = landmark;
        this.toFloor = toFloor;
        this.startMetres = startMetres;
        this.distanceM = distanceM;
        this.turnDegrees = turnDegrees;
    }

    @Override
    public String toString() {
        return maneuver + " at " + at.id + " (" + Math.round(distanceM) + " m)";
    }
}
