package com.safarsaathi.backend.connection;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/trips/{tripId}/connections}: every change of planes in the trip, with its status and the reason in
 * plain words (docs/api.md). An empty list when the trip has no connection; 404 Problem Details for an unknown trip.
 * The rest of the connection endpoints (plan, ticket type, urgency, alternatives) come in phase C2.
 */
@RestController
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/api/trips/{tripId}/connections")
    public List<ConnectionResponse> connections(@PathVariable long tripId) {
        return connectionService.connectionsOf(tripId);
    }
}
