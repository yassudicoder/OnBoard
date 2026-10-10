package com.safarsaathi.app.data.ask;

import androidx.annotation.Nullable;

import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.MapFloor;
import com.safarsaathi.routing.map.MapNode;
import com.safarsaathi.routing.map.NodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * "Where are the washrooms?": finds places of one kind on the terminal map and describes each one by the nearest
 * named spot on the same floor ("near Gate 32"), because the map calls every washroom just "Washrooms".
 * Everything comes from the map file (a demo schematic, which the answer says); nothing is guessed.
 *
 * <p>Order: when the question names a gate that is on the map ("washroom near gate 32"), the closest to that gate
 * first; otherwise floor by floor, as in the map file. Plain Java, checked by QuickAnswererTest.
 */
public final class TerminalPlaces {

    /** Kinds of spot good for saying where something is: signs, gates, landmarks, security. */
    private static final Set<NodeType> LANDMARKS = EnumSet.of(NodeType.GATE, NodeType.PIER_JUNCTION,
            NodeType.LANDMARK, NodeType.SECURITY, NodeType.CHECKIN_ROW, NodeType.BAG_DROP, NodeType.ENTRY,
            NodeType.BAGGAGE_BELT, NodeType.BAGGAGE_SERVICES, NodeType.LOST_FOUND);

    /** One place found. */
    public static final class Place {
        public final MapNode node;
        /** The nearest landmark on the same floor; null when the floor has none. */
        @Nullable
        public final MapNode near;
        /** True when the floor has a security check, so the place is "before" or "after security". */
        public final boolean floorHasSecurity;

        Place(MapNode node, @Nullable MapNode near, boolean floorHasSecurity) {
            this.node = node;
            this.near = near;
            this.floorHasSecurity = floorHasSecurity;
        }
    }

    private TerminalPlaces() {
    }

    /**
     * @param gateNumber a gate number from the question ("32"), or null
     */
    public static List<Place> find(AirportMap map, NodeType type, @Nullable String gateNumber) {
        List<Place> places = new ArrayList<>();
        for (MapFloor floor : map.floors) {
            List<MapNode> onFloor = map.nodesOnFloor(floor.id);
            boolean hasSecurity = false;
            for (MapNode node : onFloor) {
                hasSecurity |= node.type == NodeType.SECURITY;
            }
            for (MapNode node : onFloor) {
                if (node.type == type) {
                    places.add(new Place(node, nearestLandmark(node, onFloor), hasSecurity));
                }
            }
        }
        final MapNode gate = gateNumber == null ? null : map.gate(gateNumber);
        if (gate != null) {
            // Same floor as the gate first, then the closest. The sort is stable: equal distances keep the floor order.
            Collections.sort(places, Comparator.comparingDouble(place -> distanceToGate(place.node, gate)));
        }
        return places;
    }

    /** True when the map has a gate with this number (so the answer can say "closest to gate 32 first"). */
    public static boolean hasGate(AirportMap map, @Nullable String gateNumber) {
        return gateNumber != null && map.gate(gateNumber) != null;
    }

    private static double distanceToGate(MapNode node, MapNode gate) {
        double straight = Math.hypot(node.x - gate.x, node.y - gate.y);
        // Another floor is always "further" than anything on the gate's own floor.
        return node.floor.equals(gate.floor) ? straight : 100_000 + straight;
    }

    @Nullable
    private static MapNode nearestLandmark(MapNode place, List<MapNode> onFloor) {
        MapNode best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MapNode candidate : onFloor) {
            if (candidate == place || !LANDMARKS.contains(candidate.type)) {
                continue;
            }
            double distance = Math.hypot(candidate.x - place.x, candidate.y - place.y);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }
}
