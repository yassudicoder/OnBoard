package com.safarsaathi.backend.trip;

import java.time.OffsetDateTime;

/**
 * One block inside {@link TripResponse}. Same fields as the {@link Block} entity, plus (since phase C1):
 * {@code startZone} / {@code endZone} and, for a FLIGHT block, {@code flight} (null for other blocks).
 *
 * <p>Times are in the local time of the place they belong to: {@code startTime} in {@code startZone},
 * {@code endTime} in {@code endZone}. For the Indian demo trips that is +05:30 as before; a flight landing in Doha
 * ends at e.g. {@code 2026-10-11T19:35:00+03:00}. Fields that may be missing ({@code locationName}, {@code lat},
 * {@code lng}, {@code providerRef}, {@code freeChangeUntilMinutes}, {@code notes}, {@code flight}) are written as
 * {@code null}, never left out.
 */
public record BlockResponse(
        long id,
        long tripId,
        BlockType type,
        String title,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String locationName,
        Double lat,
        Double lng,
        BlockProvider provider,
        String providerRef,
        Flex flex,
        boolean anchor,
        BlockStatus status,
        Integer freeChangeUntilMinutes,
        int changeFeeInr,
        String notes,
        String startZone,
        String endZone,
        FlightResponse flight) {
}
