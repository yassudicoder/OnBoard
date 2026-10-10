package com.safarsaathi.routing.map;

/** How a walkway is travelled. Saved in the JSON by name. */
public enum EdgeKind {
    WALK,
    /** Moving walkway: faster than walking (one way in this map). */
    TRAVELATOR,
    /** Between floors; step-free, but there may be a wait. */
    LIFT,
    /** Between floors; not step-free. */
    ESCALATOR,
    /** Between floors; not step-free. */
    STAIRS
}
