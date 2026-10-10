package com.safarsaathi.routing.route;

import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.MapNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A walking route from one place to another, made by {@link Router}: its places, walkways (legs) and step-by-step
 * instructions, plus the maths a navigation screen needs ("where am I after 120 m?", "how long is left?").
 *
 * <p>Progress along a route is measured in metres from the start. Times are estimates from the {@link WalkProfile}.
 */
public final class Route {

    public final AirportMap map;
    public final WalkProfile profile;
    /** Places in walking order, start first, destination last. */
    public final List<MapNode> nodes;
    public final List<RouteLeg> legs;
    public final List<RouteStep> steps;
    public final double totalMetres;
    public final double totalSeconds;

    Route(AirportMap map, WalkProfile profile, List<MapNode> nodes, List<RouteLeg> legs) {
        this.map = map;
        this.profile = profile;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.legs = Collections.unmodifiableList(new ArrayList<>(legs));
        RouteLeg last = legs.isEmpty() ? null : legs.get(legs.size() - 1);
        this.totalMetres = last == null ? 0 : last.startMetres + last.metres;
        this.totalSeconds = last == null ? 0 : last.startSeconds + last.seconds;
        this.steps = Collections.unmodifiableList(StepBuilder.build(map, this.legs, destination(), totalMetres));
    }

    public MapNode start() {
        return nodes.get(0);
    }

    public MapNode destination() {
        return nodes.get(nodes.size() - 1);
    }

    /** True when the route goes through the security checkpoint (its time then leaves out any queue). */
    public boolean passesCheckpoint() {
        for (RouteLeg leg : legs) {
            if (leg.edge.passesCheckpoint) {
                return true;
            }
        }
        return false;
    }

    // ---- Progress maths ----

    /** Keeps a progress value inside the route. */
    public double clamp(double metres) {
        return Math.max(0, Math.min(totalMetres, metres));
    }

    /** Index of the leg the traveller is on after walking this far (the last leg at the very end). */
    public int legIndexAt(double metres) {
        if (legs.isEmpty()) {
            return -1;
        }
        double m = clamp(metres);
        for (int i = 0; i < legs.size(); i++) {
            RouteLeg leg = legs.get(i);
            if (m < leg.startMetres + leg.metres) {
                return i;
            }
        }
        return legs.size() - 1;
    }

    /** Where the traveller is after walking this far along the route. */
    public RoutePoint pointAt(double metres) {
        int index = legIndexAt(metres);
        if (index < 0) {
            MapNode only = start();
            return new RoutePoint(only.x, only.y, only.floor, 0, -1);
        }
        RouteLeg leg = legs.get(index);
        double fraction = leg.metres <= 0 ? 1 : (clamp(metres) - leg.startMetres) / leg.metres;
        fraction = Math.max(0, Math.min(1, fraction));
        double x = leg.from.x + (leg.to.x - leg.from.x) * fraction;
        double y = leg.from.y + (leg.to.y - leg.from.y) * fraction;
        // During a lift / escalator / stairs ride: the old floor for the first half, the new floor after that.
        String floor = fraction < 0.5 ? leg.from.floor : leg.to.floor;
        return new RoutePoint(x, y, floor, bearingAround(index), index);
    }

    /** The walking direction on a leg; for a ride between floors, the direction of the last walkway before it. */
    private double bearingAround(int legIndex) {
        for (int i = legIndex; i >= 0; i--) {
            if (!Double.isNaN(legs.get(i).bearing)) {
                return legs.get(i).bearing;
            }
        }
        for (int i = legIndex + 1; i < legs.size(); i++) {
            if (!Double.isNaN(legs.get(i).bearing)) {
                return legs.get(i).bearing;
            }
        }
        return 0;
    }

    /** Estimated seconds needed to walk this far along the route. */
    public double secondsAt(double metres) {
        int index = legIndexAt(metres);
        if (index < 0) {
            return 0;
        }
        RouteLeg leg = legs.get(index);
        double fraction = leg.metres <= 0 ? 1 : (clamp(metres) - leg.startMetres) / leg.metres;
        return leg.startSeconds + leg.seconds * Math.max(0, Math.min(1, fraction));
    }

    /** How far along the route the traveller gets in this many seconds (the opposite of {@link #secondsAt}). */
    public double metresAt(double seconds) {
        if (legs.isEmpty() || seconds <= 0) {
            return 0;
        }
        for (RouteLeg leg : legs) {
            if (seconds < leg.startSeconds + leg.seconds) {
                double fraction = leg.seconds <= 0 ? 1 : (seconds - leg.startSeconds) / leg.seconds;
                return leg.startMetres + leg.metres * fraction;
            }
        }
        return totalMetres;
    }

    /** Index of the step the traveller is on: the last step that starts at or before this point. */
    public int stepIndexAt(double metres) {
        double m = clamp(metres) + 0.01;
        int index = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).startMetres <= m) {
                index = i;
            }
        }
        return index;
    }

    /** Metres along the route at a place, or -1 when the route does not pass it. */
    public double metresAtNode(String nodeId) {
        if (start().id.equals(nodeId)) {
            return 0;
        }
        for (RouteLeg leg : legs) {
            if (leg.to.id.equals(nodeId)) {
                return leg.startMetres + leg.metres;
            }
        }
        return -1;
    }

    /** The place the traveller reaches next (the end of the leg they are on); the destination at the very end. */
    public MapNode nextNodeAt(double metres) {
        int index = legIndexAt(metres);
        return index < 0 ? start() : legs.get(index).to;
    }

    /** The places from this point on (the leg's end first), for redrawing the part still to walk. */
    public List<MapNode> remainingNodes(double metres) {
        int index = legIndexAt(metres);
        if (index < 0) {
            return nodes;
        }
        return nodes.subList(index + 1, nodes.size());
    }
}
