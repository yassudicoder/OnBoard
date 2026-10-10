package com.safarsaathi.backend.trip;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.safarsaathi.backend.config.AppProperties;

/**
 * Turns the database entities ({@link Trip}, {@link Block}, {@link Link}) into the API records
 * ({@link TripResponse}, {@link BlockResponse}, {@link LinkResponse}).
 *
 * <p>It is also THE one place where a stored moment ({@link Instant}) becomes the time the API shows:
 * {@link #toApiTime(Instant)}. Every endpoint that returns a time should call it, so all of them look the same:
 * Indian time with its offset, cut to whole seconds, e.g. {@code 2026-10-11T08:00:00+05:30}.
 */
@Component
public class TripMapper {

    /** Asia/Kolkata (safarsaathi.zone). India has no daylight saving time, so the offset is always +05:30. */
    private final ZoneId zone;

    public TripMapper(AppProperties properties) {
        this.zone = ZoneId.of(properties.zone());
    }

    /**
     * A stored moment as the API shows it, e.g. {@code 2026-10-11T08:00:00+05:30}. null stays null.
     * Whole seconds only: the app never needs fractions, and the shorter text is easier to read and compare.
     */
    public OffsetDateTime toApiTime(Instant moment) {
        if (moment == null) {
            return null;
        }
        return moment.atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS);
    }

    /** The whole trip. {@code blocks} and {@code links} must already be in API order (see {@link TripService}). */
    public TripResponse toResponse(Trip trip, List<Block> blocks, List<Link> links) {
        return new TripResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getPurpose(),
                trip.getPersona(),
                trip.getLanguage(),
                trip.getTravellers(),
                trip.getStatus(),
                trip.getVersion(),
                toApiTime(trip.getCreatedAt()),
                toApiTime(trip.getUpdatedAt()),
                blocks.stream().map(this::toResponse).toList(),
                links.stream().map(this::toResponse).toList());
    }

    public BlockResponse toResponse(Block block) {
        return new BlockResponse(
                block.getId(),
                block.getTripId(),
                block.getType(),
                block.getTitle(),
                toApiTime(block.getStartTime()),
                toApiTime(block.getEndTime()),
                block.getLocationName(),
                block.getLat(),
                block.getLng(),
                block.getProvider(),
                block.getProviderRef(),
                block.getFlex(),
                block.isAnchor(),
                block.getStatus(),
                block.getFreeChangeUntilMinutes(),
                block.getChangeFeeInr(),
                block.getNotes());
    }

    public LinkResponse toResponse(Link link) {
        return new LinkResponse(
                link.getId(),
                link.getTripId(),
                link.getFromBlockId(),
                link.getToBlockId(),
                link.getMinGapMinutes(),
                link.getTravelMinutes(),
                link.getTravelMode());
    }
}
