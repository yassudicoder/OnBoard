package com.safarsaathi.routing.route;

import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.EdgeKind;
import com.safarsaathi.routing.map.MapFloor;
import com.safarsaathi.routing.map.MapNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a route's legs into instructions, the way a person would give directions:
 * <ul>
 *   <li>"Head towards ..." at the start, and again after a lift / escalator / stairs ride;</li>
 *   <li>a turn wherever the walking direction changes by 25 degrees or more ("Turn left at Central hall"), named
 *       after the place where it happens; nearly straight bends are left out;</li>
 *   <li>security, travelators and rides between floors always get their own step;</li>
 *   <li>the arrival last.</li>
 * </ul>
 */
final class StepBuilder {

    private StepBuilder() {
    }

    /** A step before its distance is known. */
    private static final class Draft {
        final Maneuver maneuver;
        final MapNode at;
        MapNode landmark;
        final String toFloor;
        final double startMetres;
        final double turnDegrees;

        Draft(Maneuver maneuver, MapNode at, MapNode landmark, String toFloor, double startMetres, double turnDegrees) {
            this.maneuver = maneuver;
            this.at = at;
            this.landmark = landmark;
            this.toFloor = toFloor;
            this.startMetres = startMetres;
            this.turnDegrees = turnDegrees;
        }
    }

    static List<RouteStep> build(AirportMap map, List<RouteLeg> legs, MapNode destination, double totalMetres) {
        List<Draft> drafts = new ArrayList<>();
        if (!legs.isEmpty()) {
            drafts.add(new Draft(Maneuver.DEPART, legs.get(0).from, null, null, 0, 0));
        }
        double inBearing = Double.NaN;
        boolean afterRide = false;
        for (int i = 0; i < legs.size(); i++) {
            RouteLeg leg = legs.get(i);
            if (leg.changesFloor()) {
                add(drafts, new Draft(ride(map, leg), leg.from, leg.from, leg.to.floor, leg.startMetres, 0));
                afterRide = true;
                continue;
            }
            if (leg.edge.passesCheckpoint) {
                add(drafts, new Draft(Maneuver.SECURITY, leg.from, leg.from, null, leg.startMetres, 0));
            } else if (leg.edge.kind == EdgeKind.TRAVELATOR) {
                add(drafts, new Draft(Maneuver.TRAVELATOR, leg.from, leg.to, null, leg.startMetres, 0));
            } else if (afterRide) {
                add(drafts, new Draft(Maneuver.DEPART, leg.from, null, null, leg.startMetres, 0));
            } else if (i > 0 && !Double.isNaN(inBearing)) {
                double turn = turnAngle(inBearing, leg.bearing);
                Maneuver maneuver = Maneuver.forTurn(turn);
                if (maneuver != null) {
                    add(drafts, new Draft(maneuver, leg.from, leg.from, null, leg.startMetres, turn));
                }
            }
            inBearing = leg.bearing;
            afterRide = false;
        }
        drafts.add(new Draft(Maneuver.ARRIVE, destination, destination, null, totalMetres, 0));

        List<RouteStep> steps = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            Draft next = i + 1 < drafts.size() ? drafts.get(i + 1) : null;
            if (draft.landmark == null) {
                // "Head towards" the place of the next step (the destination when nothing comes before it).
                draft.landmark = next == null ? destination : next.at;
            }
            double distance = next == null ? 0 : next.startMetres - draft.startMetres;
            steps.add(new RouteStep(draft.maneuver, draft.at, draft.landmark, draft.toFloor, draft.startMetres,
                    distance, draft.turnDegrees));
        }
        return steps;
    }

    /** Adds a step; a "Head towards" at the same spot is replaced (e.g. a route that starts in a lift). */
    private static void add(List<Draft> drafts, Draft draft) {
        if (!drafts.isEmpty()) {
            Draft last = drafts.get(drafts.size() - 1);
            if (last.maneuver == Maneuver.DEPART && Math.abs(last.startMetres - draft.startMetres) < 0.01) {
                drafts.remove(drafts.size() - 1);
            }
        }
        drafts.add(draft);
    }

    /** Lift / escalator / stairs, up or down (by the floors' order). */
    private static Maneuver ride(AirportMap map, RouteLeg leg) {
        MapFloor from = map.floor(leg.from.floor);
        MapFloor to = map.floor(leg.to.floor);
        boolean up = from == null || to == null || to.order > from.order;
        EdgeKind kind = leg.edge.kind == null ? EdgeKind.LIFT : leg.edge.kind;
        switch (kind) {
            case ESCALATOR:
                return up ? Maneuver.ESCALATOR_UP : Maneuver.ESCALATOR_DOWN;
            case STAIRS:
                return up ? Maneuver.STAIRS_UP : Maneuver.STAIRS_DOWN;
            case LIFT:
            default:
                return up ? Maneuver.LIFT_UP : Maneuver.LIFT_DOWN;
        }
    }

    /** Change of direction in degrees, in (-180, 180]: + = turning right (clockwise), - = left. */
    static double turnAngle(double fromBearing, double toBearing) {
        double turn = (toBearing - fromBearing) % 360;
        if (turn > 180) {
            turn -= 360;
        } else if (turn <= -180) {
            turn += 360;
        }
        return turn;
    }
}
