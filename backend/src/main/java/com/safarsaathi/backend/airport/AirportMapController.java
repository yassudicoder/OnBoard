package com.safarsaathi.backend.airport;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code GET /api/airports/{code}/{terminal}/map}: one terminal's walking map for the app (docs/api.md).
 *
 * <p>The answer is map.json (header, floors, places, walkways, QR anchors) plus {@code "areas"}: the hall and pier
 * outlines from areas.geojson. Example: {@code GET /api/airports/BOM/T2/map}. Case does not matter (bom/t2 works).
 *
 * <p>Caching: the answer carries an ETag with the map version, e.g. {@code "BOM-T2-v1"}. When the app sends it back
 * in {@code If-None-Match}, Spring answers {@code 304 Not Modified} with no body, so an unchanged map is never
 * downloaded twice. An unknown airport or terminal is a 404 Problem Details answer.
 */
@RestController
@RequestMapping("/api/airports")
public class AirportMapController {

    /** How long a browser or proxy may reuse the answer without asking again. */
    static final Duration CACHE_FOR = Duration.ofMinutes(5);

    private final AirportMapStore store;

    public AirportMapController(AirportMapStore store) {
        this.store = store;
    }

    @GetMapping("/{code}/{terminal}/map")
    public ResponseEntity<JsonNode> map(@PathVariable("code") String code, @PathVariable("terminal") String terminal) {
        AirportMapStore.LoadedMap loaded = store.find(code, terminal)
                .orElseThrow(() -> new AirportMapNotFoundException(code, terminal));
        return ResponseEntity.ok()
                .eTag(loaded.etag())
                .cacheControl(CacheControl.maxAge(CACHE_FOR))
                .body(loaded.response());
    }
}
