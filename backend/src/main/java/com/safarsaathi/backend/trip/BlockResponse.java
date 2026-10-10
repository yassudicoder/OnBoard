package com.safarsaathi.backend.trip;

import java.time.OffsetDateTime;

/**
 * One block inside {@link TripResponse}. Same fields as the {@link Block} entity; times in Indian time (+05:30).
 * Fields that may be missing ({@code locationName}, {@code lat}, {@code lng}, {@code providerRef},
 * {@code freeChangeUntilMinutes}, {@code notes}) are written as {@code null}, never left out.
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
        String notes) {
}
