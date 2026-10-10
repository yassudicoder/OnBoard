package com.safarsaathi.backend.ripple;

/**
 * "Block {@code toBlockId} can start at the earliest {@code minGapMinutes + travelMinutes} after block
 * {@code fromBlockId} ends." The ripple engine's copy of a trip's {@code Link}.
 *
 * @param fromBlockId   the earlier block
 * @param toBlockId     the block that waits for it
 * @param minGapMinutes extra minutes needed in between, e.g. 75 for a minimum flight connection time
 * @param travelMinutes minutes to get from the first block's place to the second one's
 */
public record RippleLink(long fromBlockId, long toBlockId, int minGapMinutes, int travelMinutes) {

    /** All the minutes needed between the end of the first block and the start of the second. */
    public int totalMinutes() {
        return minGapMinutes + travelMinutes;
    }
}
