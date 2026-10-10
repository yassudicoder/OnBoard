package com.safarsaathi.app.data.airport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.safarsaathi.routing.map.AirportMap;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a terminal map in the server's shape (map.json + {@code "areas"} from areas.geojson) into routing-core's
 * {@link AirportMap} plus the {@link MapArea} outlines the screen draws. Plain Java (Gson only), so MapJsonTest checks
 * it on the laptop with the real mock-data files.
 */
public final class MapJson {

    private static final Gson GSON = new Gson();
    private static final Type NAMES = new TypeToken<Map<String, String>>() { }.getType();

    private MapJson() {
    }

    /** A parsed map: the routing model and the outlines. */
    public static final class MapData {
        public final AirportMap map;
        public final List<MapArea> areas;

        public MapData(AirportMap map, List<MapArea> areas) {
            this.map = map;
            this.areas = Collections.unmodifiableList(areas);
        }

        /** The outlines on one floor. */
        public List<MapArea> areasOnFloor(String floorId) {
            List<MapArea> result = new ArrayList<>();
            for (MapArea area : areas) {
                if (floorId.equals(area.floor)) {
                    result.add(area);
                }
            }
            return result;
        }
    }

    /**
     * Reads the server's JSON.
     *
     * @throws IllegalArgumentException when it is not a map at all
     */
    public static MapData parse(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a map: " + e.getMessage(), e);
        }
        AirportMap map = GSON.fromJson(root, AirportMap.class);
        if (map == null || map.nodes == null || map.nodes.isEmpty()) {
            throw new IllegalArgumentException("The map has no places");
        }
        List<MapArea> areas = new ArrayList<>();
        JsonElement areasElement = root.get("areas");
        if (areasElement != null && areasElement.isJsonObject()) {
            JsonArray features = areasElement.getAsJsonObject().getAsJsonArray("features");
            if (features != null) {
                for (JsonElement feature : features) {
                    MapArea area = readArea(feature.getAsJsonObject());
                    if (area != null) {
                        areas.add(area);
                    }
                }
            }
        }
        return new MapData(map, areas);
    }

    /** The bundled files (map.json + areas.geojson) as ONE JSON in the server's shape. */
    public static String combine(String mapJson, String areasGeoJson) {
        JsonObject root = JsonParser.parseString(mapJson).getAsJsonObject();
        root.add("areas", JsonParser.parseString(areasGeoJson));
        return root.toString();
    }

    /** One GeoJSON polygon feature; null for anything else (only polygons are drawn). */
    private static MapArea readArea(JsonObject feature) {
        JsonObject properties = feature.getAsJsonObject("properties");
        JsonObject geometry = feature.getAsJsonObject("geometry");
        if (properties == null || geometry == null || !"Polygon".equals(text(geometry, "type"))) {
            return null;
        }
        JsonArray outerRing = geometry.getAsJsonArray("coordinates").get(0).getAsJsonArray();
        List<double[]> points = new ArrayList<>();
        for (JsonElement point : outerRing) {
            JsonArray xy = point.getAsJsonArray();
            points.add(new double[] {xy.get(0).getAsDouble(), xy.get(1).getAsDouble()});
        }
        Map<String, String> names = properties.has("names")
                ? GSON.fromJson(properties.get("names"), NAMES) : new HashMap<>();
        return new MapArea(text(properties, "floor"), text(properties, "kind"), names, points);
    }

    private static String text(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
