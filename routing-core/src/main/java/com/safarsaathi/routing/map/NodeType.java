package com.safarsaathi.routing.map;

/** What kind of place a map node is (CLAUDE.md, Gate Navigation map model). Saved in the JSON by name. */
public enum NodeType {
    /** A door into or out of the terminal. */
    ENTRY,
    CHECKIN_ROW,
    BAG_DROP,
    /** Security check: the way from LANDSIDE to AIRSIDE. */
    SECURITY,
    IMMIGRATION,
    /** Where a pier (a long arm with gates) starts. */
    PIER_JUNCTION,
    GATE,
    LIFT,
    ESCALATOR,
    STAIRS,
    TRAVELATOR,
    WASHROOM,
    HELP_DESK,
    BUGGY_STOP,
    /** Something easy to see that directions can name, e.g. "Duty Free". */
    LANDMARK,
    BAGGAGE_BELT,
    /** The airline's baggage services counter in arrivals (where a damaged or missing bag is reported). */
    BAGGAGE_SERVICES,
    LOST_FOUND
}
