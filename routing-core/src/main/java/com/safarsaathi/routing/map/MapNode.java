package com.safarsaathi.routing.map;

import java.util.HashMap;
import java.util.Map;

/**
 * One place on the map: an entry, a check-in row, security, a gate, a lift, washrooms, a landmark, ...
 * Positions are metres in the map's local frame (x to the east, y to the north), see {@link AirportMap#units}.
 */
public final class MapNode {

    /** Unique id inside the map, e.g. "gate_32". */
    public String id;
    public NodeType type;
    /** Floor id, e.g. "DEP". */
    public String floor;
    public double x;
    public double y;
    /** Before security (LANDSIDE) or after it (AIRSIDE). */
    public Zone zone;
    /** Short public number or letter, when there is one: gate "32", check-in row "C", belt "4", lift "A". */
    public String ref;
    /** Names by language code: "en", "hi", "mr". */
    public Map<String, String> names = new HashMap<>();
    /** Optional extra help by language code, e.g. "Gates 31–35" for a pier sign. */
    public Map<String, String> hint = new HashMap<>();

    /** The name in this language, or the English one when that language is missing. */
    public String name(String language) {
        return localised(names, language);
    }

    /** The hint in this language (English when missing), or null when the place has no hint. */
    public String hint(String language) {
        return hint == null || hint.isEmpty() ? null : localised(hint, language);
    }

    static String localised(Map<String, String> texts, String language) {
        if (texts == null) {
            return "";
        }
        String text = texts.get(language);
        if (text == null || text.trim().isEmpty()) {
            text = texts.get("en");
        }
        return text == null ? "" : text;
    }

    @Override
    public String toString() {
        return id + " (" + type + ", " + floor + ")";
    }
}
