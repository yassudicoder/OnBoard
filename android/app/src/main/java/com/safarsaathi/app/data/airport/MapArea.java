package com.safarsaathi.app.data.airport;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One outline from areas.geojson, e.g. "Pier C" or "Departures hall": only for drawing (routing never uses it).
 * Points are metres in the map's local frame, like the places (NOT longitude and latitude).
 */
public final class MapArea {

    public static final String KIND_LANDSIDE = "LANDSIDE";
    public static final String KIND_AIRSIDE = "AIRSIDE";
    public static final String KIND_PIER = "PIER";
    public static final String KIND_CHECKPOINT = "CHECKPOINT";

    public final String floor;
    /** LANDSIDE, AIRSIDE, PIER or CHECKPOINT: picks the fill colour. */
    public final String kind;
    public final Map<String, String> names;
    /** The outline, each point {x, y}. */
    public final List<double[]> points;

    public MapArea(String floor, String kind, Map<String, String> names, List<double[]> points) {
        this.floor = floor;
        this.kind = kind;
        this.names = names;
        this.points = Collections.unmodifiableList(points);
    }

    /** The name in this language, or the English one. */
    public String name(String language) {
        String name = names.get(language);
        if (name == null || name.trim().isEmpty()) {
            name = names.get("en");
        }
        return name == null ? "" : name;
    }
}
