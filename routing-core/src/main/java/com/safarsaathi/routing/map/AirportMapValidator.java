package com.safarsaathi.routing.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a map for mistakes, so a typo in map.json fails a test (and the backend's startup) with a clear message
 * instead of giving a traveller a wrong route. Plain Java; checked by AirportMapValidatorTest.
 *
 * <p>What it checks:
 * <ul>
 *   <li>the header: airport, terminal, version, and an unverified map must call itself a demo schematic;</li>
 *   <li>every floor, place and anchor id is unique; every place has a floor, a zone and names in English, Hindi and
 *       Marathi; every gate has a unique number;</li>
 *   <li>every walkway joins two known places; on one floor it is a WALK or TRAVELATOR as long as the straight line
 *       between them (within {@value #DISTANCE_TOLERANCE_M} m); between floors it is a LIFT, ESCALATOR or STAIRS at the
 *       same spot; STAIRS and ESCALATOR are not step-free, everything else is;</li>
 *   <li><b>no way to airside around security</b>: a walkway from LANDSIDE to AIRSIDE must pass the checkpoint and be
 *       one way;</li>
 *   <li>every gate can be reached from every entry door, and from every gate there is a way back out to landside.</li>
 * </ul>
 * (Only Java 8 library calls, because the Android app compiles this file too and supports Android 8.)
 */
public final class AirportMapValidator {

    /** How far a walkway's length may be from the straight-line distance between its places. */
    public static final double DISTANCE_TOLERANCE_M = 1.0;

    /** Every place needs a name in each of these languages. */
    public static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList("en", "hi", "mr"));

    private AirportMapValidator() {
    }

    /** Every mistake found, in plain words; an empty list means the map is fine. */
    public static List<String> problems(AirportMap map) {
        List<String> problems = new ArrayList<>();
        checkHeader(map, problems);
        Map<String, MapFloor> floors = checkFloors(map, problems);
        Map<String, MapNode> nodes = checkNodes(map, floors, problems);
        checkEdges(map, nodes, problems);
        checkAnchors(map, nodes, problems);
        if (problems.isEmpty()) { // reachability only makes sense on a map without broken parts
            checkReachability(map, problems);
        }
        return problems;
    }

    private static void checkHeader(AirportMap map, List<String> problems) {
        if (blank(map.airport) || blank(map.terminal)) {
            problems.add("The map needs an airport code and a terminal.");
        }
        if (map.version < 1) {
            problems.add("The map version must be 1 or more.");
        }
        if (!map.verified && (map.note == null || !map.note.contains("Demo schematic"))) {
            problems.add("An unverified map must say \"Demo schematic\" in its note.");
        }
    }

    private static Map<String, MapFloor> checkFloors(AirportMap map, List<String> problems) {
        Map<String, MapFloor> floors = new HashMap<>();
        if (map.floors == null || map.floors.isEmpty()) {
            problems.add("The map has no floors.");
            return floors;
        }
        for (MapFloor floor : map.floors) {
            if (blank(floor.id)) {
                problems.add("A floor has no id.");
            } else if (floors.put(floor.id, floor) != null) {
                problems.add("Floor id " + floor.id + " is used twice.");
            }
            if (floor.names == null || blank(floor.names.get("en"))) {
                problems.add("Floor " + floor.id + " has no English name.");
            }
        }
        return floors;
    }

    private static Map<String, MapNode> checkNodes(AirportMap map, Map<String, MapFloor> floors, List<String> problems) {
        Map<String, MapNode> nodes = new HashMap<>();
        Set<String> gateNumbers = new HashSet<>();
        if (map.nodes == null || map.nodes.isEmpty()) {
            problems.add("The map has no places.");
            return nodes;
        }
        for (MapNode node : map.nodes) {
            if (blank(node.id)) {
                problems.add("A place has no id.");
                continue;
            }
            if (nodes.put(node.id, node) != null) {
                problems.add("Place id " + node.id + " is used twice.");
            }
            if (node.type == null) {
                problems.add("Place " + node.id + " has no type.");
            }
            if (!floors.containsKey(node.floor)) {
                problems.add("Place " + node.id + " is on an unknown floor: " + node.floor + ".");
            }
            if (node.zone == null) {
                problems.add("Place " + node.id + " has no zone (LANDSIDE or AIRSIDE).");
            }
            for (String language : LANGUAGES) {
                if (node.names == null || blank(node.names.get(language))) {
                    problems.add("Place " + node.id + " has no name in language \"" + language + "\".");
                }
            }
            if (node.type == NodeType.GATE) {
                if (blank(node.ref)) {
                    problems.add("Gate " + node.id + " has no gate number (ref).");
                } else if (!gateNumbers.add(node.ref)) {
                    problems.add("Gate number " + node.ref + " is used twice.");
                }
            }
        }
        return nodes;
    }

    private static void checkEdges(AirportMap map, Map<String, MapNode> nodes, List<String> problems) {
        if (map.edges == null || map.edges.isEmpty()) {
            problems.add("The map has no walkways.");
            return;
        }
        for (MapEdge edge : map.edges) {
            MapNode from = nodes.get(edge.from);
            MapNode to = nodes.get(edge.to);
            if (from == null || to == null) {
                problems.add("Walkway " + edge + " joins a place that does not exist.");
                continue;
            }
            if (edge.kind == null || edge.zone == null) {
                problems.add("Walkway " + edge + " needs a kind and a zone.");
                continue;
            }
            if (edge.distanceM <= 0) {
                problems.add("Walkway " + edge + " must be longer than 0 m.");
            }
            boolean sameFloor = from.floor != null && from.floor.equals(to.floor);
            if (sameFloor) {
                if (edge.kind != EdgeKind.WALK && edge.kind != EdgeKind.TRAVELATOR) {
                    problems.add("Walkway " + edge + " stays on one floor, so it must be WALK or TRAVELATOR.");
                }
                double straight = Math.hypot(from.x - to.x, from.y - to.y);
                if (Math.abs(straight - edge.distanceM) > DISTANCE_TOLERANCE_M) {
                    problems.add("Walkway " + edge + " says " + edge.distanceM + " m, but its places are "
                            + Math.round(straight * 10) / 10.0 + " m apart.");
                }
            } else {
                if (edge.kind != EdgeKind.LIFT && edge.kind != EdgeKind.ESCALATOR && edge.kind != EdgeKind.STAIRS) {
                    problems.add("Walkway " + edge + " changes floor, so it must be LIFT, ESCALATOR or STAIRS.");
                }
                if (Math.hypot(from.x - to.x, from.y - to.y) > 0.5) {
                    problems.add("Walkway " + edge + " changes floor, so both ends must be at the same spot.");
                }
            }
            boolean shouldBeStepFree = edge.kind != EdgeKind.STAIRS && edge.kind != EdgeKind.ESCALATOR;
            if (edge.stepFree != shouldBeStepFree) {
                problems.add("Walkway " + edge + " is " + edge.kind + ", so stepFree must be " + shouldBeStepFree + ".");
            }
            checkSecurity(edge, from, to, problems);
        }
    }

    /** Landside to airside only through the checkpoint, one way. Airside to landside (an exit door) is fine. */
    private static void checkSecurity(MapEdge edge, MapNode from, MapNode to, List<String> problems) {
        boolean intoAirside = from.zone == Zone.LANDSIDE && to.zone == Zone.AIRSIDE;
        boolean backwardsIntoAirside = !edge.oneWay && from.zone == Zone.AIRSIDE && to.zone == Zone.LANDSIDE;
        if ((intoAirside || backwardsIntoAirside) && !(edge.passesCheckpoint && edge.oneWay && intoAirside)) {
            problems.add("Walkway " + edge + " lets people reach airside without passing security one way.");
        }
        if (edge.passesCheckpoint && !intoAirside) {
            problems.add("Walkway " + edge + " passes a checkpoint, so it must lead from LANDSIDE to AIRSIDE.");
        }
    }

    private static void checkAnchors(AirportMap map, Map<String, MapNode> nodes, List<String> problems) {
        Set<String> ids = new HashSet<>();
        if (map.anchors == null) {
            return;
        }
        for (MapAnchor anchor : map.anchors) {
            if (blank(anchor.id) || !ids.add(anchor.id)) {
                problems.add("QR anchor " + anchor.id + " has no id or is used twice.");
            }
            if (!nodes.containsKey(anchor.node)) {
                problems.add("QR anchor " + anchor.id + " points at a place that does not exist: " + anchor.node + ".");
            }
        }
    }

    /** Every gate can be reached from every landside entry; from every gate there is a way back to landside. */
    private static void checkReachability(AirportMap map, List<String> problems) {
        Map<String, List<String>> next = new HashMap<>();
        for (MapEdge edge : map.edges) {
            link(next, edge.from, edge.to);
            if (!edge.oneWay) {
                link(next, edge.to, edge.from);
            }
        }
        List<MapNode> gates = new ArrayList<>();
        List<MapNode> entries = new ArrayList<>();
        for (MapNode node : map.nodes) {
            if (node.type == NodeType.GATE) {
                gates.add(node);
            } else if (node.type == NodeType.ENTRY && node.zone == Zone.LANDSIDE) {
                entries.add(node);
            }
        }
        if (gates.isEmpty() || entries.isEmpty()) {
            problems.add("The map needs at least one landside entry and one gate.");
            return;
        }
        for (MapNode entry : entries) {
            Set<String> reached = reachableFrom(entry.id, next);
            for (MapNode gate : gates) {
                if (!reached.contains(gate.id)) {
                    problems.add(gate.name("en") + " cannot be reached from " + entry.name("en") + ".");
                }
            }
        }
        for (MapNode gate : gates) {
            boolean wayOut = false;
            for (String id : reachableFrom(gate.id, next)) {
                if (map.node(id).zone == Zone.LANDSIDE) {
                    wayOut = true;
                    break;
                }
            }
            if (!wayOut) {
                problems.add("From " + gate.name("en") + " there is no way back to landside (arrivals exit).");
            }
        }
    }

    private static void link(Map<String, List<String>> next, String from, String to) {
        List<String> list = next.get(from);
        if (list == null) {
            list = new ArrayList<>();
            next.put(from, list);
        }
        list.add(to);
    }

    private static Set<String> reachableFrom(String start, Map<String, List<String>> next) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            List<String> neighbours = next.get(queue.poll());
            if (neighbours == null) {
                continue;
            }
            for (String id : neighbours) {
                if (seen.add(id)) {
                    queue.add(id);
                }
            }
        }
        return seen;
    }

    private static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
