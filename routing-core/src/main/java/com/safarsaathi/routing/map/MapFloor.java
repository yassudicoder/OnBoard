package com.safarsaathi.routing.map;

import java.util.HashMap;
import java.util.Map;

/** One floor of the terminal, e.g. Departures. */
public final class MapFloor {

    /** Unique id inside the map, e.g. "DEP". */
    public String id;
    /** Higher = further up; screens list floors from the top down. */
    public int order;
    /** Names by language code: "en", "hi", "mr". */
    public Map<String, String> names = new HashMap<>();

    /** The name in this language, or the English one when that language is missing. */
    public String name(String language) {
        return MapNode.localised(names, language);
    }
}
