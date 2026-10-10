package com.safarsaathi.app.ui.trip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.databinding.ItemTripBlockBinding;

import java.util.function.LongConsumer;

/**
 * The Trip tab's booking card (res/layout/item_trip_block.xml), for other screens. The Home tab shows its
 * "Needs your decision" and "Changed for you" bookings with it, so a booking looks exactly the same on both tabs:
 * same icon, times, status chip, "Planned for ..." line and red outline.
 */
public final class BlockCards {

    private BlockCards() {
    }

    /**
     * Makes one filled-in card (not yet added to {@code parent}).
     *
     * @param onClick called with the block's id when the card is tapped
     */
    public static View create(LayoutInflater inflater, ViewGroup parent, BlockEntity block, LongConsumer onClick) {
        ItemTripBlockBinding binding = ItemTripBlockBinding.inflate(inflater, parent, false);
        TripTimelineAdapter.BlockHolder holder = new TripTimelineAdapter.BlockHolder(binding, onClick::accept);
        holder.bind(TripRow.block(block, TripTime.timeRange(block.startTime, block.endTime)));
        return binding.getRoot();
    }
}
