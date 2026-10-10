package com.safarsaathi.backend.watcher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.safarsaathi.backend.config.AppProperties;
import com.safarsaathi.backend.ripple.BlockOutcome;
import com.safarsaathi.backend.ripple.RippleBlock;
import com.safarsaathi.backend.ripple.RippleEngine;
import com.safarsaathi.backend.ripple.RippleLink;
import com.safarsaathi.backend.ripple.RippleResult;
import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.trip.BlockNotFoundException;
import com.safarsaathi.backend.trip.BlockRepository;
import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.BlockType;
import com.safarsaathi.backend.trip.DisruptionEvent;
import com.safarsaathi.backend.trip.DisruptionEventRepository;
import com.safarsaathi.backend.trip.Link;
import com.safarsaathi.backend.trip.LinkRepository;
import com.safarsaathi.backend.trip.Trip;
import com.safarsaathi.backend.trip.TripMapper;
import com.safarsaathi.backend.trip.TripNotFoundException;
import com.safarsaathi.backend.trip.TripRepository;

/**
 * Applies one disruption (a block delayed or cancelled) to a trip: runs the ripple engine and saves what it decided.
 * Used by the {@link FlightWatcher}, and through it by the demo buttons.
 *
 * <p>Everything happens in ONE database transaction ({@link #apply}): the new times and statuses of every affected
 * block, the {@link DisruptionEvent} row and the trip's new version are saved together, or (on any error) none of
 * them. So the app never sees a half-applied ripple.
 */
@Service
public class DisruptionService {

    /** The disruption_events.detail column holds at most 500 characters. */
    private static final int MAX_DETAIL_LENGTH = 500;

    private final TripRepository tripRepository;
    private final BlockRepository blockRepository;
    private final LinkRepository linkRepository;
    private final DisruptionEventRepository eventRepository;
    private final TripMapper mapper;
    private final Clock clock;
    private final RippleEngine rippleEngine;

    public DisruptionService(TripRepository tripRepository, BlockRepository blockRepository,
                             LinkRepository linkRepository, DisruptionEventRepository eventRepository,
                             TripMapper mapper, AppProperties properties, Clock clock) {
        this.tripRepository = tripRepository;
        this.blockRepository = blockRepository;
        this.linkRepository = linkRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.clock = clock;
        // The engine is plain Java with no state, so one shared copy is enough.
        this.rippleEngine = new RippleEngine(ZoneId.of(properties.zone()));
    }

    /**
     * The block, after checking that it can be disrupted: the trip exists (else 404), the block belongs to it
     * (else 404) and it is not DONE or CANCELLED already (else 409). The demo buttons call this BEFORE telling the
     * mock provider anything, so a wrong id never leaves a change waiting for some other block.
     */
    @Transactional(readOnly = true)
    public Block findBlockToChange(long tripId, long blockId) {
        if (!tripRepository.existsById(tripId)) {
            throw new TripNotFoundException(tripId);
        }
        Block block = blockRepository.findById(blockId)
                .filter(found -> found.getTripId() == tripId)
                .orElseThrow(() -> new BlockNotFoundException(tripId, blockId));
        checkCanChange(block);
        return block;
    }

    /**
     * Applies {@code change} to block {@code blockId} of trip {@code tripId}:
     * <ol>
     *   <li>load the trip's blocks and links, and run the ripple engine ("now" from the shared Clock);</li>
     *   <li>save the changed block (new times, or CANCELLED) and every downstream block's new status, plus the new
     *       times of the blocks the engine moved (AUTO_ADJUSTED). Blocks not downstream keep their status;</li>
     *   <li>store a {@link DisruptionEvent}, add 1 to the trip's version and set its updatedAt.</li>
     * </ol>
     *
     * @throws TripNotFoundException  (404) unknown trip
     * @throws BlockNotFoundException (404) unknown block, or a block of another trip
     * @throws ResponseStatusException (409) the block is already DONE or CANCELLED
     */
    @Transactional
    public DisruptionResult apply(long tripId, long blockId, FlightStatusChange change) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        List<Block> blocks = blockRepository.findByTripIdOrderByStartTimeAscIdAsc(tripId);
        List<Link> links = linkRepository.findByTripIdOrderByIdAsc(tripId);
        Map<Long, Block> blocksById = new HashMap<>();
        for (Block block : blocks) {
            blocksById.put(block.getId(), block);
        }
        Block changed = blocksById.get(blockId);
        if (changed == null) {
            throw new BlockNotFoundException(tripId, blockId);
        }
        checkCanChange(changed);

        // 1. Ask the engine. It only calculates; nothing is saved yet.
        Instant now = clock.instant();
        RippleResult ripple = rippleEngine.run(toRippleBlocks(blocks), toRippleLinks(links),
                change.toRippleChange(blockId), now);

        // 2. Copy its verdicts onto the blocks. outcomes[0] is the changed block itself: new times for a delay,
        //    status CANCELLED for a cancellation (its times stay, so the timeline still shows when it was).
        List<Block> updated = new ArrayList<>();
        for (BlockOutcome outcome : ripple.outcomes()) {
            Block block = blocksById.get(outcome.blockId());
            block.setStatus(outcome.status());
            if (outcome.moved()) {
                block.setStartTime(outcome.newStart());
                block.setEndTime(outcome.newEnd());
            }
            updated.add(block);
        }
        blockRepository.saveAll(updated);

        // 3. Remember what happened, and give the trip a new version so the app knows to refresh.
        Instant detectedAt = now.truncatedTo(ChronoUnit.SECONDS); // whole seconds, like every time in the API
        DisruptionEvent event = new DisruptionEvent();
        event.setTripId(tripId);
        event.setBlockId(blockId);
        event.setKind(change.kind());
        event.setDetail(shorten(change.detail()));
        event.setDetectedAt(detectedAt);
        eventRepository.save(event);

        trip.setVersion(trip.getVersion() + 1);
        trip.setUpdatedAt(detectedAt);
        tripRepository.save(trip);

        return toResult(trip, change, ripple, blocksById);
    }

    /** DONE and CANCELLED blocks are finished: nothing can delay or cancel them any more. */
    private static void checkCanChange(Block block) {
        if (block.getStatus() == BlockStatus.CANCELLED || block.getStatus() == BlockStatus.DONE) {
            String state = block.getStatus() == BlockStatus.CANCELLED ? "cancelled" : "done";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Block " + block.getId() + " is already " + state + ", so it cannot be delayed or cancelled");
        }
    }

    /** The trip's blocks as the ripple engine sees them (only the facts it needs). */
    private static List<RippleBlock> toRippleBlocks(List<Block> blocks) {
        return blocks.stream()
                .map(block -> new RippleBlock(block.getId(), block.getType() == BlockType.HOTEL,
                        block.getStartTime(), block.getEndTime(), block.getFlex(), block.isAnchor(),
                        block.getFreeChangeUntilMinutes(), block.getStatus()))
                .toList();
    }

    private static List<RippleLink> toRippleLinks(List<Link> links) {
        return links.stream()
                .map(link -> new RippleLink(link.getFromBlockId(), link.getToBlockId(), link.getMinGapMinutes(),
                        link.getTravelMinutes()))
                .toList();
    }

    private DisruptionResult toResult(Trip trip, FlightStatusChange change, RippleResult ripple,
                                      Map<Long, Block> blocksById) {
        List<DisruptionResult.Outcome> outcomes = ripple.outcomes().stream()
                .map(outcome -> new DisruptionResult.Outcome(
                        outcome.blockId(),
                        blocksById.get(outcome.blockId()).getTitle(),
                        outcome.status(),
                        mapper.toApiTime(outcome.newStart()), // null stays null
                        mapper.toApiTime(outcome.newEnd()),
                        outcome.minutesLate(),
                        outcome.reason()))
                .toList();
        return new DisruptionResult(trip.getId(), trip.getVersion(), ripple.changedBlockId(), change.kind(),
                outcomes, ripple.brokenLinks());
    }

    private static String shorten(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
