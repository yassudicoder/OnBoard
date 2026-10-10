package com.safarsaathi.backend.ripple;

/**
 * A link whose timing no longer works: block {@code toBlockId} starts {@code minutesShort} minutes before you can
 * get there from block {@code fromBlockId}. Example: the cab reaches the office at 12:50 but the meeting starts at
 * 12:00, so 50 minutes short.
 *
 * <p>Only timing problems are listed here. A block that waits for a cancelled booking shows up in the outcomes
 * (NEEDS_USER) instead, because "minutes short" has no meaning there.
 */
public record BrokenLink(long fromBlockId, long toBlockId, int minutesShort) {
}
