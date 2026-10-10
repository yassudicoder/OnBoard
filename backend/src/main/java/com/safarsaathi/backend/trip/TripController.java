package com.safarsaathi.backend.trip;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code /api/trips} endpoints. Phase 1: {@code GET /api/trips/{id}} (the Android app's only data source).
 *
 * <p>Errors need no code here. Each one becomes a Problem Details JSON answer, never a stack trace:
 * <ul>
 *   <li>unknown id, e.g. {@code /api/trips/99}: 404 with detail "Trip 99 not found" ({@link TripNotFoundException});</li>
 *   <li>id that is not a whole number, e.g. {@code /api/trips/abc}: 400. Spring cannot turn "abc" into a
 *       {@code long}, and Spring Boot's Problem Details handler answers that with 400 Bad Request.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /**
     * The full trip: blocks, links, statuses and version. Example: {@code GET /api/trips/1}.
     * The name in {@code @PathVariable("id")} is written out so it also works in IDEs that compile without
     * Maven's settings.
     */
    @GetMapping("/{id}")
    public TripResponse getTrip(@PathVariable("id") long id) {
        return tripService.getTrip(id);
    }
}
