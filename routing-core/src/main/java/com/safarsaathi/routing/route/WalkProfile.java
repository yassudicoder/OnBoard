package com.safarsaathi.routing.route;

import com.safarsaathi.routing.map.EdgeKind;
import com.safarsaathi.routing.map.MapEdge;

/**
 * How fast someone moves through the terminal, and whether they need a step-free route. The route finder adds up
 * walking SECONDS (not metres), so a lift or a slower pace changes the route the way it changes real life.
 *
 * <p>All speeds and the lift wait are ESTIMATES chosen by the team (CLAUDE.md: walking 1.2 m/s, elderly 0.8 m/s);
 * screens label times as estimates. They are not measured at the airport.
 */
public final class WalkProfile {

    /** Typical adult walking speed, metres per second (estimate). */
    public static final double NORMAL_SPEED = 1.2;
    /** Slower pace for elderly travellers or anyone who wants more time (estimate). */
    public static final double SLOW_SPEED = 0.8;
    /** A moving walkway adds about this much to your own speed (estimate; they run at roughly 0.5-0.75 m/s). */
    public static final double TRAVELATOR_BOOST = 0.6;
    /** Seconds spent waiting for a lift (estimate). */
    public static final double LIFT_WAIT_SECONDS = 60;
    /** Lift cabin speed, metres per second (estimate). */
    public static final double LIFT_SPEED = 1.0;
    /** Escalator speed while standing, metres per second (estimate). */
    public static final double ESCALATOR_SPEED = 0.5;
    /** Stairs are walked at this share of the normal pace (estimate). */
    public static final double STAIRS_FACTOR = 0.5;

    /** Walking speed in metres per second. */
    public final double walkSpeed;
    /** True: never use STAIRS or ESCALATOR walkways (wheelchair, pram, heavy trolley). */
    public final boolean stepFree;

    public WalkProfile(double walkSpeed, boolean stepFree) {
        if (walkSpeed <= 0) {
            throw new IllegalArgumentException("walkSpeed must be positive");
        }
        this.walkSpeed = walkSpeed;
        this.stepFree = stepFree;
    }

    /** 1.2 m/s, stairs and escalators allowed. */
    public static WalkProfile normal() {
        return new WalkProfile(NORMAL_SPEED, false);
    }

    /** 0.8 m/s, stairs and escalators allowed (combine with step-free for a wheelchair). */
    public static WalkProfile slow() {
        return new WalkProfile(SLOW_SPEED, false);
    }

    public WalkProfile withStepFree(boolean stepFree) {
        return new WalkProfile(walkSpeed, stepFree);
    }

    /** True when this walkway may be used at all. */
    public boolean allows(MapEdge edge) {
        return !stepFree || edge.stepFree;
    }

    /** Seconds to go along this walkway (estimate). */
    public double seconds(MapEdge edge) {
        EdgeKind kind = edge.kind == null ? EdgeKind.WALK : edge.kind;
        switch (kind) {
            case TRAVELATOR:
                return edge.distanceM / (walkSpeed + TRAVELATOR_BOOST);
            case LIFT:
                return LIFT_WAIT_SECONDS + edge.distanceM / LIFT_SPEED;
            case ESCALATOR:
                return edge.distanceM / ESCALATOR_SPEED;
            case STAIRS:
                return edge.distanceM / (walkSpeed * STAIRS_FACTOR);
            case WALK:
            default:
                return edge.distanceM / walkSpeed;
        }
    }

    /** The fastest anyone with this profile moves across the floor plan: used for the route finder's estimate. */
    double fastestFloorSpeed() {
        return walkSpeed + TRAVELATOR_BOOST;
    }

    /** Typical step length in metres, for counting steps along the route (estimate). */
    public double stepLengthM() {
        return walkSpeed < NORMAL_SPEED ? 0.55 : 0.7;
    }
}
