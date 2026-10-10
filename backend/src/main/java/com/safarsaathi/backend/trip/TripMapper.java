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
 * <p>It is also THE one place where a stored moment ({@link Instant}) becomes the time the API shows. Every endpoint
 * that returns a time should call {@link #toApiTime(Instant, String)} (a time at a place: in that place's local time,
 * e.g. landing in Doha {@code 2026-10-11T19:35:00+03:00}) or {@link #toApiTime(Instant)} (a time that belongs to no
 * place, such as when a trip was created: Indian time, e.g. {@code 2026-10-10T17:30:05+05:30}). Both cut to whole
 * seconds, so all times look the same.
 */
@Component
public class TripMapper {

    /** Asia/Kolkata (safarsaathi.zone), for times that belong to no place. India has no daylight saving time. */
    private final ZoneId zone;

    public TripMapper(AppProperties properties) {
        this.zone = ZoneId.of(properties.zone());
    }

    /**
     * A stored moment in Indian time, for times that belong to no place (createdAt, updatedAt), e.g.
     * {@code 2026-10-11T08:00:00+05:30}. null stays null.
     * Whole seconds only: the app never needs fractions, and the shorter text is easier to read and compare.
     */
    public OffsetDateTime toApiTime(Instant moment) {
        return toApiTime(moment, zone);
    }

    /**
     * A stored moment in the local time of a place, e.g. {@code toApiTime(landing, "Asia/Qatar")} =
     * {@code 2026-10-11T19:35:00+03:00}. null stays null.
     */
    public OffsetDateTime toApiTime(Instant moment, String zoneId) {
        return toApiTime(moment, ZoneId.of(zoneId));
    }

    private static OffsetDateTime toApiTime(Instant moment, ZoneId zoneId) {
        if (moment == null) {
            return null;
        }
        return moment.atZone(zoneId).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS);
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
                toApiTime(block.getStartTime(), block.getStartZone()),
                toApiTime(block.getEndTime(), block.getEndZone()),
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
                block.getNotes(),
                block.getStartZone(),
                block.getEndZone(),
                block.getType() == BlockType.FLIGHT ? toFlightResponse(block) : null);
    }

    /** The flight fields: departures in the departure airport's time, arrivals in the arrival airport's time. */
    private FlightResponse toFlightResponse(Block flight) {
        return new FlightResponse(
                flight.getFlightNo(),
                flight.getDepAirport(),
                flight.getArrAirport(),
                flight.getDepTerminal(),
                flight.getArrTerminal(),
                flight.getStartZone(),
                flight.getEndZone(),
                toApiTime(flight.getScheduledStart(), flight.getStartZone()),
                toApiTime(flight.getScheduledEnd(), flight.getEndZone()),
                toApiTime(flight.getStartTime(), flight.getStartZone()),
                toApiTime(flight.getEndTime(), flight.getEndZone()),
                flightStatus(flight));
    }

    /** See {@link FlightStatus}: cancelled, else the reported phase, else delayed or scheduled. */
    static FlightStatus flightStatus(Block flight) {
        if (flight.getStatus() == BlockStatus.CANCELLED) {
            return FlightStatus.CANCELLED;
        }
        if (flight.getFlightPhase() != null) {
            switch (flight.getFlightPhase()) {
                case BOARDING:
                    return FlightStatus.BOARDING;
                case IN_AIR:
                    return FlightStatus.IN_AIR;
                case LANDED:
                    return FlightStatus.LANDED;
                default:
                    break; // SCHEDULED: nothing reported, look at the times
            }
        }
        return flight.getStartTime().isAfter(flight.getScheduledStart())
                ? FlightStatus.DELAYED : FlightStatus.SCHEDULED;
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
