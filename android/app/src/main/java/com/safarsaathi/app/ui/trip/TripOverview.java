package com.safarsaathi.app.ui.trip;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.local.BlockEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The trip at a glance, for the Home tab, worked out ONLY from the saved plan (the same Room data the Trip tab
 * shows), so both tabs always agree. Plain Java, checked by TripOverviewTest.
 *
 * <p>Rules:
 * <ul>
 *   <li><b>Needs a decision</b>: at least one block has the server's status NEEDS_USER (the same blocks as the
 *       Trip tab's red banner, see {@link TripRowBuilder#needingDecision}).</li>
 *   <li><b>Changed</b>: otherwise, at least one block is AUTO_ADJUSTED or CANCELLED, or starts at another time
 *       than in the original plan ({@link #minutesMoved}).</li>
 *   <li><b>On track</b>: none of that.</li>
 *   <li><b>No bookings</b>: the trip has no blocks.</li>
 * </ul>
 * The main flight is the trip's first FLIGHT block; the next step is the first block that is not done or
 * cancelled and has not ended yet.
 */
public final class TripOverview {

    public enum State { NO_BOOKINGS, ON_TRACK, CHANGED, NEEDS_DECISION }

    /**
     * What a status chip shows for a block whose server status is OK but which starts later than first planned
     * (a delayed flight keeps status OK on the server: nothing for the traveller to do, but it IS late).
     * A screen-only value, never sent to or received from the server.
     */
    public static final String DISPLAY_DELAYED = "DELAYED";

    private static final Comparator<BlockEntity> BY_START_THEN_ID =
            Comparator.comparing((BlockEntity block) -> block.startTime).thenComparingLong(block -> block.id);

    public final State state;
    /** The trip's first flight, or null when it has none. */
    @Nullable
    public final BlockEntity flight;
    /** Blocks with status NEEDS_USER, in time order. */
    public final List<BlockEntity> needingDecision;
    /** Other blocks that changed (auto-adjusted, cancelled, or moved from the original plan), in time order. */
    public final List<BlockEntity> changed;
    /** How many of {@link #changed} were moved for the traveller (status AUTO_ADJUSTED). */
    public final int autoAdjustedCount;
    /** How many blocks are cancelled. */
    public final int cancelledCount;
    /** The next block that is not done or cancelled and has not ended yet; null when there is none. */
    @Nullable
    public final BlockEntity nextUp;

    private TripOverview(State state, @Nullable BlockEntity flight, List<BlockEntity> needingDecision,
                         List<BlockEntity> changed, int autoAdjustedCount, int cancelledCount,
                         @Nullable BlockEntity nextUp) {
        this.state = state;
        this.flight = flight;
        this.needingDecision = needingDecision;
        this.changed = changed;
        this.autoAdjustedCount = autoAdjustedCount;
        this.cancelledCount = cancelledCount;
        this.nextUp = nextUp;
    }

    /**
     * @param blocks the saved blocks of one trip (any order)
     * @param now    the current moment, for "next step"
     */
    public static TripOverview of(List<BlockEntity> blocks, Instant now) {
        List<BlockEntity> sorted = new ArrayList<>(blocks);
        sorted.sort(BY_START_THEN_ID);

        List<BlockEntity> needingDecision = TripRowBuilder.needingDecision(sorted);
        List<BlockEntity> changed = new ArrayList<>();
        int autoAdjusted = 0;
        int cancelled = 0;
        BlockEntity flight = null;
        BlockEntity nextUp = null;
        for (BlockEntity block : sorted) {
            boolean isAutoAdjusted = BlockEntity.STATUS_AUTO_ADJUSTED.equals(block.status);
            boolean isCancelled = BlockEntity.STATUS_CANCELLED.equals(block.status);
            boolean isDone = BlockEntity.STATUS_DONE.equals(block.status);
            if (isAutoAdjusted) {
                autoAdjusted++;
            }
            if (isCancelled) {
                cancelled++;
            }
            if (!BlockEntity.STATUS_NEEDS_USER.equals(block.status)
                    && (isAutoAdjusted || isCancelled || minutesMoved(block) != 0)) {
                changed.add(block);
            }
            if (flight == null && BlockEntity.TYPE_FLIGHT.equals(block.type)) {
                flight = block;
            }
            if (nextUp == null && !isDone && !isCancelled && block.endTime.isAfter(now)) {
                nextUp = block;
            }
        }

        State state;
        if (sorted.isEmpty()) {
            state = State.NO_BOOKINGS;
        } else if (!needingDecision.isEmpty()) {
            state = State.NEEDS_DECISION;
        } else if (!changed.isEmpty()) {
            state = State.CHANGED;
        } else {
            state = State.ON_TRACK;
        }
        return new TripOverview(state, flight, Collections.unmodifiableList(needingDecision),
                Collections.unmodifiableList(changed), autoAdjusted, cancelled, nextUp);
    }

    /**
     * Minutes between the block's original planned start and its start now: positive = later, negative =
     * earlier, 0 = unchanged OR original plan not known (see PlannedTimes).
     */
    public static long minutesMoved(BlockEntity block) {
        if (block.plannedStartTime == null) {
            return 0;
        }
        return Duration.between(block.plannedStartTime, block.startTime).toMinutes();
    }

    /**
     * The status a chip shows: the server's status, except {@link #DISPLAY_DELAYED} for an OK block that now
     * starts later than first planned. Used by Home and Trip alike.
     */
    public static String displayStatus(BlockEntity block) {
        if (BlockEntity.STATUS_OK.equals(block.status) && minutesMoved(block) > 0) {
            return DISPLAY_DELAYED;
        }
        return block.status;
    }
}
