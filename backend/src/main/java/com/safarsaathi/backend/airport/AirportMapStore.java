package com.safarsaathi.backend.airport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.AirportMapValidator;

/**
 * The airport maps the backend serves, loaded ONCE at startup from the bundled mock-data folder
 * ({@code classpath:mock-data/airports/<AIRPORT>-<TERMINAL>/map.json} + {@code areas.geojson}; Maven copies the
 * repo-root mock-data folder into the jar, see pom.xml).
 *
 * <p>A map is fixed reference data, so it is not stored in the database: a new version ships with the code. Every map
 * is checked with routing-core's {@link AirportMapValidator}; a map with mistakes stops the backend at startup with a
 * clear list of what is wrong (like the demo trip seed file), instead of sending wrong routes to travellers.
 */
@Component
public class AirportMapStore {

    /** The maps that ship with the backend: the pilot, Mumbai Terminal 2 (CLAUDE.md). */
    static final List<String> BUNDLED = List.of("BOM-T2");

    private static final Logger log = LoggerFactory.getLogger(AirportMapStore.class);

    /** One map as the API sends it, plus the parsed model (the route finder uses it from phase G2). */
    public record LoadedMap(AirportMap map, ObjectNode response, String etag) {
    }

    private final Map<String, LoadedMap> maps = new HashMap<>();

    public AirportMapStore(ObjectMapper objectMapper) {
        for (String key : BUNDLED) {
            LoadedMap loaded = load(objectMapper, key);
            maps.put(key, loaded);
            log.info("Airport map {} v{} loaded: {} places, {} walkways, {} QR anchors ({}: {})", key,
                    loaded.map().version, loaded.map().nodes.size(), loaded.map().edges.size(),
                    loaded.map().anchors.size(), loaded.map().verified ? "verified" : "NOT verified", loaded.map().note);
        }
    }

    /** The map of one terminal, e.g. find("bom", "t2"); empty when we have none. Case does not matter. */
    public Optional<LoadedMap> find(String airport, String terminal) {
        return Optional.ofNullable(maps.get(key(airport, terminal)));
    }

    static String key(String airport, String terminal) {
        return airport.trim().toUpperCase(Locale.ROOT) + "-" + terminal.trim().toUpperCase(Locale.ROOT);
    }

    private static LoadedMap load(ObjectMapper objectMapper, String key) {
        String folder = "mock-data/airports/" + key + "/";
        JsonNode mapTree = readTree(objectMapper, folder + "map.json");
        AirportMap map;
        try {
            // FAIL_ON_UNKNOWN_PROPERTIES: a misspelt field name stops the backend with an error that names it.
            map = objectMapper.readerFor(AirportMap.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(mapTree);
        } catch (IOException e) {
            throw new IllegalStateException(folder + "map.json could not be read: " + e.getMessage(), e);
        }
        List<String> problems = AirportMapValidator.problems(map);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(folder + "map.json has mistakes:\n- " + String.join("\n- ", problems));
        }
        if (!key.equals(key(map.airport, map.terminal))) {
            throw new IllegalStateException(folder + "map.json says it is " + map.airport + " " + map.terminal);
        }
        JsonNode areas = readTree(objectMapper, folder + "areas.geojson");
        if (!"FeatureCollection".equals(areas.path("type").asText())) {
            throw new IllegalStateException(folder + "areas.geojson must be a GeoJSON FeatureCollection");
        }
        for (JsonNode feature : areas.path("features")) {
            String floor = feature.path("properties").path("floor").asText();
            if (map.floor(floor) == null) {
                throw new IllegalStateException(folder + "areas.geojson has an area on an unknown floor: " + floor);
            }
        }
        ObjectNode response = ((ObjectNode) mapTree).deepCopy();
        response.set("areas", areas);
        String etag = "\"" + key + "-v" + map.version + "\"";
        return new LoadedMap(map, response, etag);
    }

    private static JsonNode readTree(ObjectMapper objectMapper, String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("classpath:" + path + " not found. Maven copies it from the repo-root "
                    + "mock-data folder (see pom.xml): rebuild with ./mvnw clean package");
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path + ": " + e.getMessage(), e);
        }
    }
}
