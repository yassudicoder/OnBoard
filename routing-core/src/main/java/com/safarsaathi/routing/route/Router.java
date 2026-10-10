package com.safarsaathi.routing.route;

import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.MapEdge;
import com.safarsaathi.routing.map.MapNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Finds the quickest walking route between two places of an {@link AirportMap} (A* search, cost = walking seconds
 * from the {@link WalkProfile}).
 *
 * <ul>
 *   <li>One-way walkways (security, travelators, the arrivals exit) are only used in their direction, so a route from
 *       landside to airside always goes through security, and never back the other way.</li>
 *   <li>Step-free profiles never use stairs or escalators.</li>
 *   <li>Same map + same places + same profile = the same route every time (ties are broken by the map's order).</li>
 * </ul>
 * Make one Router per map and reuse it; it is cheap (the BOM T2 demo map has 50 places).
 */
public final class Router {

    /** One usable direction of a walkway. */
    private static final class Arc {
        final int to;
        final MapEdge edge;

        Arc(int to, MapEdge edge) {
            this.to = to;
            this.edge = edge;
        }
    }

    /** A place waiting in the search queue. */
    private static final class Entry implements Comparable<Entry> {
        final int node;
        final double priority;
        final long order;

        Entry(int node, double priority, long order) {
            this.node = node;
            this.priority = priority;
            this.order = order;
        }

        @Override
        public int compareTo(Entry other) {
            int byPriority = Double.compare(priority, other.priority);
            return byPriority != 0 ? byPriority : Long.compare(order, other.order);
        }
    }

    private final AirportMap map;
    private final List<MapNode> nodes;
    private final Map<String, Integer> indexById = new HashMap<>();
    private final List<List<Arc>> arcs = new ArrayList<>();

    public Router(AirportMap map) {
        this.map = map;
        this.nodes = new ArrayList<>(map.nodes);
        for (int i = 0; i < nodes.size(); i++) {
            indexById.put(nodes.get(i).id, i);
            arcs.add(new ArrayList<Arc>());
        }
        for (MapEdge edge : map.edges) {
            Integer from = indexById.get(edge.from);
            Integer to = indexById.get(edge.to);
            if (from == null || to == null) {
                continue; // AirportMapValidator reports such a walkway; the route finder just ignores it
            }
            arcs.get(from).add(new Arc(to, edge));
            if (!edge.oneWay) {
                arcs.get(to).add(new Arc(from, edge));
            }
        }
    }

    public AirportMap map() {
        return map;
    }

    /**
     * The quickest route, or null when there is none for this profile (e.g. step-free with no lift).
     *
     * @throws IllegalArgumentException for a place id the map does not have
     */
    public Route route(String fromNodeId, String toNodeId, WalkProfile profile) {
        int start = index(fromNodeId);
        int goal = index(toNodeId);
        MapNode goalNode = nodes.get(goal);

        double[] best = new double[nodes.size()];
        int[] cameFrom = new int[nodes.size()];
        MapEdge[] cameBy = new MapEdge[nodes.size()];
        boolean[] done = new boolean[nodes.size()];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(cameFrom, -1);

        PriorityQueue<Entry> queue = new PriorityQueue<>();
        long order = 0;
        best[start] = 0;
        queue.add(new Entry(start, estimate(nodes.get(start), goalNode, profile), order++));
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            int current = entry.node;
            if (done[current]) {
                continue;
            }
            done[current] = true;
            if (current == goal) {
                break;
            }
            for (Arc arc : arcs.get(current)) {
                if (!profile.allows(arc.edge) || done[arc.to]) {
                    continue;
                }
                double seconds = best[current] + profile.seconds(arc.edge);
                if (seconds < best[arc.to] - 1e-9) {
                    best[arc.to] = seconds;
                    cameFrom[arc.to] = current;
                    cameBy[arc.to] = arc.edge;
                    queue.add(new Entry(arc.to, seconds + estimate(nodes.get(arc.to), goalNode, profile), order++));
                }
            }
        }
        if (Double.isInfinite(best[goal])) {
            return null;
        }

        // Walk back from the goal, then turn it around.
        List<Integer> path = new ArrayList<>();
        for (int at = goal; at != -1; at = cameFrom[at]) {
            path.add(at);
        }
        Collections.reverse(path);
        List<MapNode> routeNodes = new ArrayList<>();
        List<RouteLeg> legs = new ArrayList<>();
        double metres = 0;
        double seconds = 0;
        for (int i = 0; i < path.size(); i++) {
            routeNodes.add(nodes.get(path.get(i)));
            if (i > 0) {
                MapEdge edge = cameBy[path.get(i)];
                RouteLeg leg = new RouteLeg(nodes.get(path.get(i - 1)), nodes.get(path.get(i)), edge,
                        profile.seconds(edge), metres, seconds);
                legs.add(leg);
                metres += leg.metres;
                seconds += leg.seconds;
            }
        }
        return new Route(map, profile, routeNodes, legs);
    }

    /**
     * Estimated walking seconds from one place to every place that can be reached from it (Dijkstra), nearest first.
     * Used for "nearest washroom / help desk / lift" lists.
     */
    public Map<MapNode, Double> secondsFrom(String fromNodeId, WalkProfile profile) {
        int start = index(fromNodeId);
        double[] best = new double[nodes.size()];
        boolean[] done = new boolean[nodes.size()];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        PriorityQueue<Entry> queue = new PriorityQueue<>();
        long order = 0;
        best[start] = 0;
        queue.add(new Entry(start, 0, order++));
        Map<MapNode, Double> result = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            if (done[entry.node]) {
                continue;
            }
            done[entry.node] = true;
            result.put(nodes.get(entry.node), best[entry.node]);
            for (Arc arc : arcs.get(entry.node)) {
                if (!profile.allows(arc.edge) || done[arc.to]) {
                    continue;
                }
                double seconds = best[entry.node] + profile.seconds(arc.edge);
                if (seconds < best[arc.to] - 1e-9) {
                    best[arc.to] = seconds;
                    queue.add(new Entry(arc.to, seconds, order++));
                }
            }
        }
        return result;
    }

    private int index(String nodeId) {
        Integer index = nodeId == null ? null : indexById.get(nodeId);
        if (index == null) {
            throw new IllegalArgumentException("No place '" + nodeId + "' on the map " + map.airport + " "
                    + map.terminal);
        }
        return index;
    }

    /** A lower bound of the seconds still needed: straight-line distance at the fastest possible pace. */
    private static double estimate(MapNode from, MapNode to, WalkProfile profile) {
        return Math.hypot(to.x - from.x, to.y - from.y) / profile.fastestFloorSpeed();
    }
}
