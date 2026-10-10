package com.safarsaathi.app.ui.trip;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.SyncState;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.LinkEntity;
import com.safarsaathi.app.data.local.TripEntity;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Turns trip values (statuses, types, minutes, rupees) into what the screen shows: icons, colours and
 * texts from strings.xml. Shared by the timeline, the header and the block details sheet, so the same
 * status always looks and reads the same.
 *
 * <p>Enum-like values arrive as Strings ("NEEDS_USER"); unknown ones get a neutral look instead of a crash.
 * (A Java switch on Strings is fine. Only R.id values must not be used in a switch, see CLAUDE.md.)
 */
public final class TripUi {

    /** Indian digit grouping: 2,500 and 1,00,000. */
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private TripUi() {
    }

    @DrawableRes
    public static int iconFor(String type) {
        switch (type) {
            case "FLIGHT": return R.drawable.ic_block_flight_24;
            case "BAG_CLAIM": return R.drawable.ic_block_bag_claim_24;
            case "CAB": return R.drawable.ic_block_cab_24;
            case "HOTEL": return R.drawable.ic_block_hotel_24;
            case "MEETING": return R.drawable.ic_block_meeting_24;
            case "EVENT": return R.drawable.ic_block_event_24;
            case "RESTAURANT": return R.drawable.ic_block_restaurant_24;
            case "TRAIN": return R.drawable.ic_block_train_24;
            default: return R.drawable.ic_block_other_24;
        }
    }

    /** "Flight", "Cab", ... (also the icon's content description for screen readers). */
    @StringRes
    public static int typeLabel(String type) {
        switch (type) {
            case "FLIGHT": return R.string.type_flight;
            case "BAG_CLAIM": return R.string.type_bag_claim;
            case "CAB": return R.string.type_cab;
            case "HOTEL": return R.string.type_hotel;
            case "MEETING": return R.string.type_meeting;
            case "EVENT": return R.string.type_event;
            case "RESTAURANT": return R.string.type_restaurant;
            case "TRAIN": return R.string.type_train;
            default: return R.string.type_other;
        }
    }

    @StringRes
    public static int purposeLabel(String purpose) {
        switch (purpose) {
            case "BUSINESS": return R.string.purpose_business;
            case "WEDDING": return R.string.purpose_wedding;
            case "HOLIDAY": return R.string.purpose_holiday;
            case "PILGRIMAGE": return R.string.purpose_pilgrimage;
            case "MEDICAL": return R.string.purpose_medical;
            case "EXAM": return R.string.purpose_exam;
            default: return R.string.purpose_other;
        }
    }

    /** Short chip text: "On track", "Auto-adjusted", "Delayed", "Needs you", ... (from TripOverview.displayStatus). */
    @StringRes
    public static int statusLabel(String status) {
        switch (status) {
            case BlockEntity.STATUS_OK: return R.string.status_ok;
            case BlockEntity.STATUS_AUTO_ADJUSTED: return R.string.status_auto_adjusted;
            case TripOverview.DISPLAY_DELAYED: return R.string.status_delayed;
            case BlockEntity.STATUS_NEEDS_USER: return R.string.status_needs_user;
            case BlockEntity.STATUS_DONE: return R.string.status_done;
            case BlockEntity.STATUS_CANCELLED: return R.string.status_cancelled;
            default: return R.string.status_unknown;
        }
    }

    /** One plain sentence about the status, for the details sheet. */
    @StringRes
    public static int statusExplanation(String status) {
        switch (status) {
            case BlockEntity.STATUS_OK: return R.string.sheet_status_ok;
            case BlockEntity.STATUS_AUTO_ADJUSTED: return R.string.sheet_status_auto_adjusted;
            case TripOverview.DISPLAY_DELAYED: return R.string.sheet_status_delayed;
            case BlockEntity.STATUS_NEEDS_USER: return R.string.sheet_status_needs_user;
            case BlockEntity.STATUS_DONE: return R.string.sheet_status_done;
            case BlockEntity.STATUS_CANCELLED: return R.string.sheet_status_cancelled;
            default: return R.string.sheet_status_unknown;
        }
    }

    /**
     * Chip fill colour: green, amber (auto-adjusted or delayed: it changed, nothing to do), red, or grey (done,
     * cancelled, unknown). The chip's text always says the status too, so colour is never the only signal.
     */
    @ColorRes
    public static int statusContainerColor(String status) {
        switch (status) {
            case BlockEntity.STATUS_OK: return R.color.status_ok_container;
            case BlockEntity.STATUS_AUTO_ADJUSTED:
            case TripOverview.DISPLAY_DELAYED: return R.color.status_adjusted_container;
            case BlockEntity.STATUS_NEEDS_USER: return R.color.status_needs_user_container;
            default: return R.color.status_done_container;
        }
    }

    /** Text colour that goes with {@link #statusContainerColor}. */
    @ColorRes
    public static int statusOnContainerColor(String status) {
        switch (status) {
            case BlockEntity.STATUS_OK: return R.color.status_ok_on_container;
            case BlockEntity.STATUS_AUTO_ADJUSTED:
            case TripOverview.DISPLAY_DELAYED: return R.color.status_adjusted_on_container;
            case BlockEntity.STATUS_NEEDS_USER: return R.color.status_needs_user_on_container;
            default: return R.color.status_done_on_container;
        }
    }

    /** Gives a chip TextView (background: bg_status_chip) the text and colours of a status. */
    public static void styleStatusChip(TextView chip, String status) {
        Context context = chip.getContext();
        chip.setText(statusLabel(status));
        chip.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, statusContainerColor(status))));
        chip.setTextColor(ContextCompat.getColor(context, statusOnContainerColor(status)));
    }

    /** The chip of one block, with the same rule on every screen (TripOverview.displayStatus). */
    public static void styleStatusChip(TextView chip, BlockEntity block) {
        styleStatusChip(chip, TripOverview.displayStatus(block));
    }

    /**
     * "Planned for 08:00 · 2 hours later" (or "... earlier") when the block moved from the original plan;
     * null when it did not move or the original plan is not known (see PlannedTimes).
     */
    @Nullable
    public static String plannedText(Context context, BlockEntity block) {
        long moved = TripOverview.minutesMoved(block);
        if (moved == 0 || block.plannedStartTime == null) {
            return null;
        }
        String planned = TripTime.time(block.plannedStartTime);
        String howMuch = duration(context, (int) Math.abs(moved));
        return context.getString(moved > 0 ? R.string.planned_later : R.string.planned_earlier, planned, howMuch);
    }

    /** "30 min", "75 min", "2 hours", "24 hours". Whole hours from 2 hours up; minutes below that. */
    public static String duration(Context context, int minutes) {
        if (minutes >= 120 && minutes % 60 == 0) {
            int hours = minutes / 60;
            return context.getResources().getQuantityString(R.plurals.duration_hours, hours, hours);
        }
        return context.getString(R.string.duration_minutes, minutes);
    }

    /** Link row text: "75 min by cab", "at least 75 min to change planes", or both joined with " · ". */
    static String linkText(Context context, LinkEntity link, boolean planeChange) {
        String travel = null;
        if (link.travelMinutes > 0) {
            String time = duration(context, link.travelMinutes);
            switch (link.travelMode) {
                case LinkEntity.MODE_CAB:
                    travel = context.getString(R.string.link_travel_cab, time);
                    break;
                case LinkEntity.MODE_METRO:
                    travel = context.getString(R.string.link_travel_metro, time);
                    break;
                case LinkEntity.MODE_WALK:
                    travel = context.getString(R.string.link_travel_walk, time);
                    break;
                default:
                    travel = context.getString(R.string.link_travel_other, time);
                    break;
            }
        }
        String gap = null;
        if (link.minGapMinutes > 0) {
            String time = duration(context, link.minGapMinutes);
            gap = context.getString(planeChange ? R.string.link_gap_change_planes : R.string.link_gap, time);
        }
        if (travel != null && gap != null) {
            return context.getString(R.string.link_two_parts, travel, gap);
        }
        return travel != null ? travel : (gap != null ? gap : "");
    }

    /**
     * One-line summary under the trip title, e.g. "Business trip · Sun, 11 Oct · 6 bookings" (Trip and Home tabs).
     *
     * @param blocksInTimeOrder the trip's blocks, first one first (as TripDao returns them)
     */
    public static String tripSummary(Context context, TripEntity trip, List<BlockEntity> blocksInTimeOrder) {
        String purpose = context.getString(purposeLabel(trip.purpose));
        if (blocksInTimeOrder.isEmpty()) {
            return purpose;
        }
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        String firstDay = TripTime.dayHeader(TripTime.localDate(blocksInTimeOrder.get(0).startTime), locale);
        int count = blocksInTimeOrder.size();
        String bookings = context.getResources().getQuantityString(R.plurals.trip_bookings, count, count);
        return context.getString(R.string.trip_summary, purpose, firstDay, bookings);
    }

    /** Sync line under the trip title, e.g. "Updated 17:42" or "Offline · showing your saved plan from 17:42". */
    public static String syncLine(Context context, SyncState state, @Nullable Instant savedAt) {
        switch (state.kind) {
            case REFRESHING:
                return context.getString(R.string.trip_sync_refreshing);
            case UP_TO_DATE:
                return context.getString(R.string.trip_sync_updated, TripTime.time(nonNull(state.lastSyncedAt, savedAt)));
            case OFFLINE: {
                Instant when = state.lastSyncedAt != null ? state.lastSyncedAt : savedAt;
                boolean noConnection = state.problem == SyncState.Problem.NO_CONNECTION;
                if (when == null) {
                    return context.getString(noConnection
                            ? R.string.trip_sync_offline_no_time : R.string.trip_sync_error_no_time);
                }
                return context.getString(noConnection ? R.string.trip_sync_offline : R.string.trip_sync_error,
                        TripTime.time(when));
            }
            case IDLE:
            default:
                return savedAt == null ? "" : context.getString(R.string.trip_sync_saved, TripTime.time(savedAt));
        }
    }

    /** Explanation for the empty state after a failed first load. */
    @StringRes
    public static int emptyStateMessage(SyncState.Problem problem) {
        switch (problem) {
            case NOT_FOUND: return R.string.trip_empty_error_not_found;
            case SERVER_ERROR: return R.string.trip_empty_error_server;
            case BAD_DATA: return R.string.trip_empty_error_bad_data;
            case NO_CONNECTION:
            case NONE:
            default:
                return R.string.trip_empty_error_network;
        }
    }

    // ---- Block details sheet ----

    /** "MockAir (demo airline) · booking ref K7Q2ZM", or just the provider when there is no reference. */
    public static String bookingText(Context context, BlockEntity block) {
        String provider = context.getString(providerLabel(block.provider));
        if (block.providerRef == null || block.providerRef.trim().isEmpty()) {
            return provider;
        }
        return context.getString(R.string.sheet_booking_with_ref, provider, block.providerRef);
    }

    @StringRes
    private static int providerLabel(String provider) {
        switch (provider) {
            case "MOCK_AIR": return R.string.provider_mock_air;
            case "MOCK_STAY": return R.string.provider_mock_stay;
            case "MOCK_CAB": return R.string.provider_mock_cab;
            case "MOCK_TABLE": return R.string.provider_mock_table;
            case "GOOGLE_CALENDAR": return R.string.provider_google_calendar;
            default: return R.string.provider_none;
        }
    }

    @StringRes
    static int flexText(String flex) {
        switch (flex) {
            case "FIXED": return R.string.flex_fixed;
            case "AUTO_SHIFT": return R.string.flex_auto_shift;
            case "ASK_USER": return R.string.flex_ask_user;
            default: return R.string.flex_unknown;
        }
    }

    /**
     * The free-change window and the fee in one paragraph, e.g.
     * "Free to change until 30 min before it starts. After that, a change costs ₹200."
     */
    static String changesText(Context context, BlockEntity block) {
        Integer window = block.freeChangeUntilMinutes;
        String rule;
        if (window == null) {
            rule = context.getString(R.string.free_change_none);
        } else if (window < 0) {
            rule = context.getString(R.string.free_change_before_start, duration(context, -window));
        } else if (window == 0) {
            rule = context.getString(R.string.free_change_until_start);
        } else {
            rule = context.getString(R.string.free_change_after_start, duration(context, window));
        }

        String fee;
        if (block.changeFeeInr <= 0) {
            fee = context.getString(R.string.fee_none);
        } else {
            String rupees = NumberFormat.getIntegerInstance(INDIA).format(block.changeFeeInr);
            fee = context.getString(window == null ? R.string.fee_always : R.string.fee_after_window, rupees);
        }
        return context.getString(R.string.sheet_changes_value, rule, fee);
    }

    @NonNull
    private static Instant nonNull(@Nullable Instant first, @Nullable Instant second) {
        if (first != null) {
            return first;
        }
        return second != null ? second : Instant.now();
    }
}
