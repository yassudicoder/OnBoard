package com.safarsaathi.backend.trip;

/**
 * One link inside {@link TripResponse}: block {@code toBlockId} may start at the earliest
 * {@code minGapMinutes + travelMinutes} after block {@code fromBlockId} ends.
 */
public record LinkResponse(
        long id,
        long tripId,
        long fromBlockId,
        long toBlockId,
        int minGapMinutes,
        int travelMinutes,
        TravelMode travelMode) {
}
