package com.safarsaathi.app.ui.trip;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a trip's blocks and links into the rows of the timeline. Plain Java, checked by TripRowBuilderTest.
 *
 * <p>Rules:
 * <ul>
 *   <li>blocks in time order (start time, then id when two start together);</li>
 *   <li>a day header before the first block of each day (Indian time);</li>
 *   <li>a link row between two neighbouring blocks when a link joins them AND it takes time
 *       (travel or a minimum gap). Links of 0 minutes (e.g. landing -> bag claim) add no row.</li>
 * </ul>
 */
public final class TripRowBuilder {

    /** Time order: start time first, then id, so the order never changes between refreshes. */
    private static final Comparator<BlockEntity> BY_START_THEN_ID =
            Comparator.comparing((BlockEntity block) -> block.startTime)
                    .thenComparingLong(block -> block.id);

    private TripRowBuilder() {
    }

    public static List<TripRow> build(List<BlockEntity> blocks, List<LinkEntity> links) {
        List<BlockEntity> sorted = new ArrayList<>(blocks);
        sorted.sort(BY_START_THEN_ID);

        // Look up "the link from block A to block B" quickly. Key: "A>B".
        Map<String, LinkEntity> linkByPair = new HashMap<>();
        for (LinkEntity link : links) {
            linkByPair.putIfAbsent(pairKey(link.fromBlockId, link.toBlockId), link);
        }

        List<TripRow> rows = new ArrayList<>();
        LocalDate currentDay = null;
        for (int i = 0; i < sorted.size(); i++) {
            BlockEntity block = sorted.get(i);
            LocalDate day = TripTime.localDate(block.startTime);
            if (!day.equals(currentDay)) {
                rows.add(TripRow.dayHeader(day));
                currentDay = day;
            }
            rows.add(TripRow.block(block, TripTime.timeRange(block.startTime, block.endTime)));

            if (i + 1 < sorted.size()) {
                BlockEntity next = sorted.get(i + 1);
                LinkEntity link = linkByPair.get(pairKey(block.id, next.id));
                if (link != null && (link.travelMinutes > 0 || link.minGapMinutes > 0)) {
                    boolean planeChange = BlockEntity.TYPE_FLIGHT.equals(block.type)
                            && BlockEntity.TYPE_FLIGHT.equals(next.type);
                    rows.add(TripRow.link(link, planeChange));
                }
            }
        }
        return rows;
    }

    /** The blocks that wait for the traveller's decision (status NEEDS_USER), in time order. For the red banner. */
    public static List<BlockEntity> needingDecision(List<BlockEntity> blocks) {
        List<BlockEntity> result = new ArrayList<>();
        for (BlockEntity block : blocks) {
            if (BlockEntity.STATUS_NEEDS_USER.equals(block.status)) {
                result.add(block);
            }
        }
        result.sort(BY_START_THEN_ID);
        return result;
    }

    private static String pairKey(long fromBlockId, long toBlockId) {
        return fromBlockId + ">" + toBlockId;
    }
}
