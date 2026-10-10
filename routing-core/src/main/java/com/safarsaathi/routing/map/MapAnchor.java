package com.safarsaathi.routing.map;

/**
 * A QR "You are here" code stuck at a place in the terminal: scanning it puts the traveller exactly on {@link #node}.
 * The QR code holds {@link #id}, e.g. "BOM-T2-ENTRY-4".
 */
public final class MapAnchor {

    public String id;
    /** Id of the place (MapNode) the code stands at. */
    public String node;
}
