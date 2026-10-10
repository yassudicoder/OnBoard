package com.safarsaathi.routing.map;

import java.util.ArrayList;
import java.util.List;

/**
 * One terminal's walking map: floors, places (nodes), walkways (edges) and QR "You are here" anchors. Read from
 * mock-data/airports/&lt;AIRPORT&gt;-&lt;TERMINAL&gt;/map.json.
 *
 * <p>Shared by the backend and the Android app (both compile this folder, see routing-core/README.md), so it is
 * plain Java with no library at all: public fields and no-argument constructors, which both Jackson (backend) and Gson
 * (app) fill in directly from the JSON.
 *
 * <p>Honesty rule (CLAUDE.md): a map the team drew itself has {@code verified = false} and a {@link #note} saying it is
 * a demo schematic; screens show that note.
 */
public final class AirportMap {

    /** IATA airport code, e.g. "BOM". */
    public String airport;
    /** Terminal, e.g. "T2". */
    public String terminal;
    /** Goes up by 1 whenever the map changes; the app keeps the newest copy it has seen. */
    public int version;
    /** Where the data comes from, in plain words. */
    public String source;
    public String preparedBy;
    /** False until the team has checked positions on site or against official signage. */
    public boolean verified;
    /** Shown on screen, e.g. "Demo schematic, not an official airport map". */
    public String note;
    /** How x and y are measured, e.g. metres in a local frame. */
    public String units;

    public List<MapFloor> floors = new ArrayList<>();
    public List<MapNode> nodes = new ArrayList<>();
    public List<MapEdge> edges = new ArrayList<>();
    public List<MapAnchor> anchors = new ArrayList<>();

    /** The place with this id, or null. */
    public MapNode node(String id) {
        for (MapNode node : nodes) {
            if (node.id.equals(id)) {
                return node;
            }
        }
        return null;
    }

    /** The floor with this id, or null. */
    public MapFloor floor(String id) {
        for (MapFloor floor : floors) {
            if (floor.id.equals(id)) {
                return floor;
            }
        }
        return null;
    }

    /** All places on one floor, in file order. */
    public List<MapNode> nodesOnFloor(String floorId) {
        List<MapNode> result = new ArrayList<>();
        for (MapNode node : nodes) {
            if (floorId.equals(node.floor)) {
                result.add(node);
            }
        }
        return result;
    }

    /** The gate with this number, e.g. gate("32"), or null when the map has no such gate. */
    public MapNode gate(String gateNumber) {
        for (MapNode node : nodes) {
            if (node.type == NodeType.GATE && gateNumber.equals(node.ref)) {
                return node;
            }
        }
        return null;
    }

    /** The place a QR anchor stands at, or null for an unknown anchor id. */
    public MapNode anchorNode(String anchorId) {
        for (MapAnchor anchor : anchors) {
            if (anchor.id.equals(anchorId)) {
                return node(anchor.node);
            }
        }
        return null;
    }
}
