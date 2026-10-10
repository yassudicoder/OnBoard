package com.safarsaathi.backend.trip;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The JSON body of {@code GET /api/trips/{id}}: the trip with all its blocks and links.
 *
 * <p>Why a separate record instead of returning the {@link Trip} entity? The entity is the database's shape; this is
 * the API's shape, which the Android app depends on. Keeping them apart means a new database column never changes the
 * JSON by accident. Jackson writes the fields in this order, nulls included ({@code "notes": null}).
 *
 * <p>Times are {@link OffsetDateTime} in Indian time, e.g. {@code "2026-10-11T08:00:00+05:30"}; {@link TripMapper}
 * builds this record and does that conversion.
 *
 * @param version starts at 1 and goes up by 1 on every plan change. The app replaces its offline copy only when this
 *                number is higher than its own (or when {@code createdAt} differs, i.e. the demo data was reloaded).
 * @param blocks  earliest start first; blocks that start at the same moment in id order
 * @param links   in id order
 */
public record TripResponse(
        long id,
        String title,
        Purpose purpose,
        Persona persona,
        String language,
        int travellers,
        TripStatus status,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<BlockResponse> blocks,
        List<LinkResponse> links) {
}
