package com.safarsaathi.routing.map;

/**
 * A walkway between two places. Unless {@link #oneWay} is true it can be used in both directions.
 * A walkway on one floor is as long as the straight line between its two places; LIFT, ESCALATOR and STAIRS walkways
 * join the same spot on two floors.
 */
public final class MapEdge {

    public String from;
    public String to;
    /** Length in metres. */
    public double distanceM;
    public EdgeKind kind;
    /** True when a wheelchair or a heavy trolley can use it (false for STAIRS and ESCALATOR). */
    public boolean stepFree;
    /** True when it can only be used from {@link #from} to {@link #to}, e.g. through security. */
    public boolean oneWay;
    public Zone zone;
    /** True for the walkway through a checkpoint (security): the only way from LANDSIDE to AIRSIDE. */
    public boolean passesCheckpoint;

    @Override
    public String toString() {
        return from + (oneWay ? " -> " : " <-> ") + to + " (" + kind + ", " + distanceM + " m)";
    }
}
