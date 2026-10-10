package com.safarsaathi.routing.route;

import java.util.Locale;

/** What the traveller does at one step of a route. The instruction templates use {@link #key()}. */
public enum Maneuver {
    /** Start walking (also right after a lift, escalator or stairs): "Head towards {place}". */
    DEPART,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    /** Walk through the security checkpoint (landside to airside). */
    SECURITY,
    TRAVELATOR,
    LIFT_UP,
    LIFT_DOWN,
    ESCALATOR_UP,
    ESCALATOR_DOWN,
    STAIRS_UP,
    STAIRS_DOWN,
    /** The destination. */
    ARRIVE;

    /** Template key, e.g. "slight_left". */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True for the turns a compass can confirm (left, right, slight, sharp, U-turn). */
    public boolean isTurn() {
        switch (this) {
            case SLIGHT_LEFT:
            case LEFT:
            case SHARP_LEFT:
            case SLIGHT_RIGHT:
            case RIGHT:
            case SHARP_RIGHT:
            case U_TURN:
                return true;
            default:
                return false;
        }
    }

    /** True when the step goes to another floor. */
    public boolean changesFloor() {
        switch (this) {
            case LIFT_UP:
            case LIFT_DOWN:
            case ESCALATOR_UP:
            case ESCALATOR_DOWN:
            case STAIRS_UP:
            case STAIRS_DOWN:
                return true;
            default:
                return false;
        }
    }

    /** The turn for an angle in degrees (+ = to the right), or null when it is (nearly) straight on. */
    static Maneuver forTurn(double degrees) {
        double size = Math.abs(degrees);
        boolean right = degrees > 0;
        if (size < 25) {
            return null;
        }
        if (size < 50) {
            return right ? SLIGHT_RIGHT : SLIGHT_LEFT;
        }
        if (size < 140) {
            return right ? RIGHT : LEFT;
        }
        if (size < 170) {
            return right ? SHARP_RIGHT : SHARP_LEFT;
        }
        return U_TURN;
    }
}
