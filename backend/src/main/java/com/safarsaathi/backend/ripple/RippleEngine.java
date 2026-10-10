package com.safarsaathi.backend.ripple;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

import com.safarsaathi.backend.trip.BlockStatus;
import com.safarsaathi.backend.trip.Flex;

/**
 * The Ripple engine (CLAUDE.md 4.1): one block of a trip moves or is cancelled (e.g. a flight delay), and the
 * engine works out what that means for every block that waits for it.
 *
 * <p>How it works:
 * <ol>
 *   <li>Put the blocks in trip order using the links (a "topological sort": a block always comes after every
 *       block it waits for). When two blocks could go either way, the earlier start goes first, then the lower id.</li>
 *   <li>Apply the change.</li>
 *   <li>Walk the blocks after the changed one, in that order. For each, find the earliest moment you can start it:
 *       the latest (end of the earlier block + gap + travel time) over all links into it. Then decide:
 *       on time = OK; a hotel = check-in window rule; free to move = AUTO_ADJUSTED (moved); else NEEDS_USER.</li>
 * </ol>
 * Lateness is passed on: a NEEDS_USER block keeps its times, but the blocks after it are judged as if it ends late
 * (its "would-be" end), so knock-on effects show. A block that waits for a cancelled one is NEEDS_USER, and so is
 * everything after it.
 *
 * <p>Plain Java: no Spring, no database. "Now" is passed in (never read from the system clock) and the same input
 * always gives the same result, so tests can pick any moment and expect exact answers.
 */
public final class RippleEngine {

    /** A hotel arrival after this local time gets a late-arrival note. */
    private static final LocalTime LATE_ARRIVAL_AFTER = LocalTime.of(23, 0);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_ON_DAY =
            DateTimeFormatter.ofPattern("HH:mm 'on' d MMM", Locale.ENGLISH);

    /** Tie-break for the trip order: earliest start first, then the lowest id. */
    private static final Comparator<RippleBlock> BY_START_THEN_ID =
            Comparator.comparing(RippleBlock::start).thenComparingLong(RippleBlock::id);

    private final ZoneId zone;

    /** @param zone the trip's time zone (Asia/Kolkata): used for the hotel's 23:00 / midnight rule and in messages */
    public RippleEngine(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /**
     * Works out what one change does to the rest of the trip. It changes nothing itself: the caller saves the result.
     *
     * @param blocks all blocks of the trip
     * @param links  all links of the trip
     * @param change what happened to one block
     * @param now    the current time, for the free-change windows
     * @throws IllegalArgumentException when the change or a link points at a block that is not in the list,
     *                                  a block id is listed twice, or the links go round in a loop
     */
    public RippleResult run(List<RippleBlock> blocks, List<RippleLink> links, RippleChange change, Instant now) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(now, "now");
        Map<Long, RippleBlock> blocksById = byId(blocks);
        List<RippleLink> sortedLinks = checkAndSort(links, blocksById);
        RippleBlock changed = blocksById.get(change.blockId());
        if (changed == null) {
            throw new IllegalArgumentException("Block " + change.blockId() + " is not part of this trip");
        }

        // 1. Trip order, and the blocks that wait (directly or through others) for the changed one.
        List<RippleBlock> tripOrder = topologicalOrder(blocksById, sortedLinks);
        Set<Long> after = blocksAfter(changed.id(), sortedLinks);

        // readyAt = when you are really done with a block: its end, plus any lateness found on the way.
        Map<Long, Instant> readyAt = new HashMap<>();
        // Blocks that are cancelled, or wait for a cancelled one: they have no usable end time.
        Set<Long> cutOff = new HashSet<>();
        for (RippleBlock block : blocksById.values()) {
            readyAt.put(block.id(), block.end());
            if (block.status() == BlockStatus.CANCELLED) {
                cutOff.add(block.id());
            }
        }

        // 2. Apply the change.
        List<BlockOutcome> outcomes = new ArrayList<>();
        List<BrokenLink> brokenLinks = new ArrayList<>();
        if (change.cancelled()) {
            cutOff.add(changed.id());
            outcomes.add(new BlockOutcome(changed.id(), BlockStatus.CANCELLED, null, null, 0, "Cancelled."));
        } else {
            readyAt.put(changed.id(), change.newEnd());
            outcomes.add(changedBlockOutcome(changed, change));
        }

        // 3. Walk the blocks after the change, in trip order.
        for (RippleBlock block : tripOrder) {
            if (!after.contains(block.id())
                    || block.status() == BlockStatus.DONE || block.status() == BlockStatus.CANCELLED) {
                continue; // not affected, already done or already cancelled: keeps its status
            }
            List<RippleLink> incoming = linksInto(block.id(), sortedLinks);

            if (incoming.stream().anyMatch(link -> cutOff.contains(link.fromBlockId()))) {
                cutOff.add(block.id()); // so the blocks after this one are flagged too
                outcomes.add(new BlockOutcome(block.id(), BlockStatus.NEEDS_USER, null, null, 0,
                        "Depends on a cancelled booking earlier in the trip, so you decide."));
                continue;
            }

            Instant earliestStart = earliestStart(incoming, readyAt);
            BlockOutcome outcome = decide(block, earliestStart, now);
            outcomes.add(outcome);

            // Pass the lateness on: the blocks after this one wait for its real (or would-be) end.
            if (earliestStart.isAfter(block.start())) {
                readyAt.put(block.id(), block.end().plus(Duration.between(block.start(), earliestStart)));
            }
            if (outcome.status() == BlockStatus.NEEDS_USER) {
                brokenLinks.addAll(brokenLinksInto(block, incoming, readyAt));
            }
        }
        return new RippleResult(changed.id(), outcomes, brokenLinks);
    }

    /** CLAUDE.md 4.1 steps 3 to 6 for one block that does not wait for a cancelled one. */
    private BlockOutcome decide(RippleBlock block, Instant earliestStart, Instant now) {
        if (!earliestStart.isAfter(block.start())) {
            return new BlockOutcome(block.id(), BlockStatus.OK, null, null, 0, "Still on time: no change needed.");
        }
        Duration late = Duration.between(block.start(), earliestStart);
        int minutesLate = roundUpToMinutes(late);
        if (block.hotel()) {
            return hotelCheckIn(block, earliestStart, late, minutesLate);
        }
        if (canMoveForFree(block, now)) {
            // Move the whole block: the start to the earliest possible moment, the end by the same amount.
            return new BlockOutcome(block.id(), BlockStatus.AUTO_ADJUSTED, earliestStart, block.end().plus(late),
                    minutesLate, "Moved from " + time(block.start(), block) + " to " + time(earliestStart, block)
                            + ", free of charge, to fit the new plan.");
        }
        return new BlockOutcome(block.id(), BlockStatus.NEEDS_USER, null, null, minutesLate,
                "The earliest you can make it is " + time(earliestStart, block) + ", " + minutesLate
                        + " minutes late. " + whyYouDecide(block));
    }

    /**
     * Hotel rule: check-in is a window, not a fixed time. Arriving later is fine until 23:00 local time on the
     * check-in day (OK). Until midnight the check-in moves and the hotel gets a late-arrival note (AUTO_ADJUSTED).
     * From midnight on the hotel may treat it as a no-show, so the traveller decides (NEEDS_USER).
     */
    private BlockOutcome hotelCheckIn(RippleBlock hotel, Instant arrival, Duration late, int minutesLate) {
        LocalDate checkInDay = hotel.start().atZone(zone).toLocalDate();
        Instant lateArrivalAfter = checkInDay.atTime(LATE_ARRIVAL_AFTER).atZone(zone).toInstant();
        Instant midnight = checkInDay.plusDays(1).atStartOfDay(zone).toInstant();
        String arrivalTime = time(arrival, hotel);

        if (!arrival.isAfter(lateArrivalAfter)) {
            return new BlockOutcome(hotel.id(), BlockStatus.OK, null, null, minutesLate,
                    "You now arrive around " + arrivalTime + ". Check-in is open until 23:00, so nothing changes.");
        }
        if (arrival.isBefore(midnight)) {
            return new BlockOutcome(hotel.id(), BlockStatus.AUTO_ADJUSTED, arrival, hotel.end().plus(late),
                    minutesLate, "You now arrive at " + arrivalTime
                            + ", after 23:00. Check-in moved to then, with a late-arrival note for the hotel.");
        }
        return new BlockOutcome(hotel.id(), BlockStatus.NEEDS_USER, null, null, minutesLate,
                "You now arrive at " + arrivalTime
                        + ", after midnight. The hotel may treat it as a no-show, so you decide.");
    }

    /** True when the block is AUTO_SHIFT, not the anchor, and "now" is still inside its free-change window. */
    private static boolean canMoveForFree(RippleBlock block, Instant now) {
        if (block.flex() != Flex.AUTO_SHIFT || block.anchor() || block.freeChangeUntilMinutes() == null) {
            return false;
        }
        Instant freeUntil = block.start().plus(Duration.ofMinutes(block.freeChangeUntilMinutes()));
        return !now.isAfter(freeUntil);
    }

    /** The second sentence of a NEEDS_USER reason: why the engine did not just move the block. */
    private static String whyYouDecide(RippleBlock block) {
        if (block.anchor()) {
            return "This is the main reason for your trip, so you decide.";
        }
        return switch (block.flex()) {
            case FIXED -> "It cannot be moved automatically, so you decide.";
            case ASK_USER -> "Moving it needs your OK.";
            case AUTO_SHIFT -> block.freeChangeUntilMinutes() == null
                    ? "Changing it is never free, so you decide."
                    : "The free-change time is over, so you decide.";
        };
    }

    /** The changed block itself: its new times. Its status stays as it was (the caller may pick another one). */
    private BlockOutcome changedBlockOutcome(RippleBlock block, RippleChange change) {
        Duration moved = Duration.between(block.start(), change.newStart());
        String when = "Now " + time(change.newStart(), block) + " to " + time(change.newEnd(), block);
        String reason;
        if (moved.isNegative()) {
            reason = when + ", " + roundUpToMinutes(moved.negated()) + " minutes earlier than planned.";
        } else if (moved.isZero()) {
            reason = when + ", as planned.";
        } else {
            reason = when + ", " + roundUpToMinutes(moved) + " minutes later than planned.";
        }
        int minutesLate = moved.isNegative() ? 0 : roundUpToMinutes(moved);
        return new BlockOutcome(block.id(), block.status(), change.newStart(), change.newEnd(), minutesLate, reason);
    }

    /** The earliest moment a block can start: the latest of what each link into it allows. */
    private static Instant earliestStart(List<RippleLink> incoming, Map<Long, Instant> readyAt) {
        Instant earliest = null; // never stays null: every block after the change has at least one link into it
        for (RippleLink link : incoming) {
            Instant canStart = canStartAfter(link, readyAt);
            if (earliest == null || canStart.isAfter(earliest)) {
                earliest = canStart;
            }
        }
        return earliest;
    }

    /** The links into a NEEDS_USER block whose timing no longer works, with how many minutes are missing. */
    private static List<BrokenLink> brokenLinksInto(RippleBlock block, List<RippleLink> incoming,
                                                    Map<Long, Instant> readyAt) {
        List<BrokenLink> broken = new ArrayList<>();
        for (RippleLink link : incoming) {
            Instant canStart = canStartAfter(link, readyAt);
            if (canStart.isAfter(block.start())) {
                int minutesShort = roundUpToMinutes(Duration.between(block.start(), canStart));
                broken.add(new BrokenLink(link.fromBlockId(), block.id(), minutesShort));
            }
        }
        return broken;
    }

    /** When the link's "to" block could start, judging by this one link: earlier block ready + gap + travel. */
    private static Instant canStartAfter(RippleLink link, Map<Long, Instant> readyAt) {
        return readyAt.get(link.fromBlockId()).plus(Duration.ofMinutes(link.totalMinutes()));
    }

    // ---- The graph: blocks are the dots, links are the arrows between them. ----

    private static Map<Long, RippleBlock> byId(List<RippleBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        Map<Long, RippleBlock> byId = new HashMap<>();
        for (RippleBlock block : blocks) {
            if (byId.put(block.id(), block) != null) {
                throw new IllegalArgumentException("Block " + block.id() + " is listed twice");
            }
        }
        return byId;
    }

    /**
     * Checks that every link joins two blocks of this trip, and sorts the links by block ids so the result does
     * not depend on the order the caller listed them in.
     */
    private static List<RippleLink> checkAndSort(List<RippleLink> links, Map<Long, RippleBlock> blocksById) {
        Objects.requireNonNull(links, "links");
        for (RippleLink link : links) {
            if (!blocksById.containsKey(link.fromBlockId()) || !blocksById.containsKey(link.toBlockId())) {
                throw new IllegalArgumentException("Link " + link.fromBlockId() + " -> " + link.toBlockId()
                        + " points at a block that is not part of this trip");
            }
        }
        List<RippleLink> sorted = new ArrayList<>(links);
        sorted.sort(Comparator.comparingLong(RippleLink::fromBlockId).thenComparingLong(RippleLink::toBlockId));
        return sorted;
    }

    /**
     * Kahn's algorithm: repeatedly take a block that waits for nothing that is still unplaced. If several can go
     * next, the earliest start (then the lowest id) goes first, so the order is always the same.
     */
    private static List<RippleBlock> topologicalOrder(Map<Long, RippleBlock> blocksById, List<RippleLink> links) {
        Map<Long, Integer> waitingFor = new HashMap<>(); // block id -> how many of its earlier blocks are unplaced
        for (Long id : blocksById.keySet()) {
            waitingFor.put(id, 0);
        }
        for (RippleLink link : links) {
            waitingFor.merge(link.toBlockId(), 1, Integer::sum);
        }

        PriorityQueue<RippleBlock> canGoNext = new PriorityQueue<>(BY_START_THEN_ID);
        for (RippleBlock block : blocksById.values()) {
            if (waitingFor.get(block.id()) == 0) {
                canGoNext.add(block);
            }
        }
        List<RippleBlock> order = new ArrayList<>();
        while (!canGoNext.isEmpty()) {
            RippleBlock next = canGoNext.poll();
            order.add(next);
            for (RippleLink link : links) {
                if (link.fromBlockId() == next.id() && waitingFor.merge(link.toBlockId(), -1, Integer::sum) == 0) {
                    canGoNext.add(blocksById.get(link.toBlockId()));
                }
            }
        }
        // Blocks in a loop (A waits for B, B waits for A) never get down to 0, so they are never placed.
        if (order.size() < blocksById.size()) {
            throw new IllegalArgumentException("The links go round in a loop, so the blocks have no order");
        }
        return order;
    }

    /** Every block that waits for the given block, directly or through other blocks (not the block itself). */
    private static Set<Long> blocksAfter(long blockId, List<RippleLink> links) {
        Set<Long> found = new HashSet<>();
        Deque<Long> toVisit = new ArrayDeque<>();
        toVisit.push(blockId);
        while (!toVisit.isEmpty()) {
            long current = toVisit.pop();
            for (RippleLink link : links) {
                if (link.fromBlockId() == current && found.add(link.toBlockId())) {
                    toVisit.push(link.toBlockId());
                }
            }
        }
        return found;
    }

    private static List<RippleLink> linksInto(long blockId, List<RippleLink> links) {
        return links.stream().filter(link -> link.toBlockId() == blockId).toList();
    }

    /** Whole minutes, rounded up, so 30 seconds late still counts as 1 minute late. For durations of 0 or more. */
    private static int roundUpToMinutes(Duration duration) {
        long minutes = duration.toMinutes();
        if (duration.compareTo(Duration.ofMinutes(minutes)) > 0) {
            minutes++;
        }
        return Math.toIntExact(minutes);
    }

    /** "14:30", or "00:40 on 12 Oct" when the time is no longer on the block's own day. */
    private String time(Instant instant, RippleBlock block) {
        ZonedDateTime local = instant.atZone(zone);
        LocalDate blockDay = block.start().atZone(zone).toLocalDate();
        return local.toLocalDate().equals(blockDay) ? TIME.format(local) : TIME_ON_DAY.format(local);
    }
}
