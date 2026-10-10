package com.safarsaathi.app.data;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.data.remote.BlockDto;
import com.safarsaathi.app.data.remote.LinkDto;
import com.safarsaathi.app.data.remote.TripDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the server's JSON shape (TripDto) into the rows we save in Room (TripEntity, BlockEntity,
 * LinkEntity). Plain Java with no Android code, so TripMapperTest checks it on the laptop.
 *
 * <p>Fields the app cannot work without (ids, version, createdAt, block times) must be present; otherwise
 * {@link IllegalArgumentException} is thrown and the repository keeps the saved plan instead of
 * replacing it with broken data.
 */
public final class TripMapper {

    private TripMapper() {
    }

    /** One whole trip, ready to be saved in a single transaction (TripDao.replaceTrip). */
    public static final class MappedTrip {
        public final TripEntity trip;
        public final List<BlockEntity> blocks;
        public final List<LinkEntity> links;

        public MappedTrip(TripEntity trip, List<BlockEntity> blocks, List<LinkEntity> links) {
            this.trip = trip;
            this.blocks = blocks;
            this.links = links;
        }
    }

    /** @throws IllegalArgumentException when a required field is missing or a time cannot be read */
    public static MappedTrip fromDto(@Nullable TripDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("The server sent an empty trip");
        }
        TripEntity trip = new TripEntity();
        trip.id = required(dto.id, "trip id");
        trip.title = textOrEmpty(dto.title);
        trip.purpose = textOrEmpty(dto.purpose);
        trip.persona = textOrEmpty(dto.persona);
        trip.language = textOrEmpty(dto.language);
        trip.travellers = dto.travellers == null ? 1 : dto.travellers;
        trip.status = textOrEmpty(dto.status);
        trip.version = required(dto.version, "trip version");
        trip.createdAt = parseTime(dto.createdAt, "trip createdAt");
        // updatedAt is only for information, so a missing one is not worth refusing the whole trip.
        trip.updatedAt = dto.updatedAt == null ? trip.createdAt : parseTime(dto.updatedAt, "trip updatedAt");
        trip.lastSyncedAt = null; // the repository fills this in when it saves the trip

        List<BlockEntity> blocks = new ArrayList<>();
        for (BlockDto blockDto : listOrEmpty(dto.blocks)) {
            blocks.add(toBlock(blockDto, trip.id));
        }
        List<LinkEntity> links = new ArrayList<>();
        for (LinkDto linkDto : listOrEmpty(dto.links)) {
            links.add(toLink(linkDto, trip.id));
        }
        return new MappedTrip(trip, blocks, links);
    }

    private static BlockEntity toBlock(BlockDto dto, long tripId) {
        if (dto == null) {
            throw new IllegalArgumentException("The server sent an empty block");
        }
        BlockEntity block = new BlockEntity();
        block.id = required(dto.id, "block id");
        String what = "block " + block.id;
        block.tripId = dto.tripId == null ? tripId : dto.tripId;
        block.type = textOrEmpty(dto.type);
        block.title = textOrEmpty(dto.title);
        block.startTime = parseTime(dto.startTime, what + " startTime");
        block.endTime = parseTime(dto.endTime, what + " endTime");
        block.locationName = dto.locationName;
        block.lat = dto.lat;
        block.lng = dto.lng;
        block.provider = textOrEmpty(dto.provider);
        block.providerRef = dto.providerRef;
        block.flex = textOrEmpty(dto.flex);
        block.anchor = Boolean.TRUE.equals(dto.anchor);
        block.status = textOrEmpty(dto.status);
        block.freeChangeUntilMinutes = dto.freeChangeUntilMinutes;
        block.changeFeeInr = dto.changeFeeInr == null ? 0 : dto.changeFeeInr;
        block.notes = dto.notes;
        return block;
    }

    private static LinkEntity toLink(LinkDto dto, long tripId) {
        if (dto == null) {
            throw new IllegalArgumentException("The server sent an empty link");
        }
        LinkEntity link = new LinkEntity();
        link.id = required(dto.id, "link id");
        link.tripId = dto.tripId == null ? tripId : dto.tripId;
        link.fromBlockId = required(dto.fromBlockId, "link " + link.id + " fromBlockId");
        link.toBlockId = required(dto.toBlockId, "link " + link.id + " toBlockId");
        link.minGapMinutes = dto.minGapMinutes == null ? 0 : dto.minGapMinutes;
        link.travelMinutes = dto.travelMinutes == null ? 0 : dto.travelMinutes;
        link.travelMode = dto.travelMode == null ? LinkEntity.MODE_NONE : dto.travelMode;
        return link;
    }

    /**
     * Reads an ISO-8601 time with an offset, as the backend sends it ("2026-10-11T08:00:00+05:30";
     * "...Z" works too) and returns the exact moment.
     */
    static Instant parseTime(@Nullable String text, String what) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(what + " is missing");
        }
        try {
            return OffsetDateTime.parse(text.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(what + " is not a valid time: " + text, e);
        }
    }

    private static <T> T required(@Nullable T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException(what + " is missing");
        }
        return value;
    }

    private static String textOrEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }

    private static <T> List<T> listOrEmpty(@Nullable List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
