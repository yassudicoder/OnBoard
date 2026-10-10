package com.safarsaathi.app.ui.trip;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.databinding.ItemTripBlockBinding;
import com.safarsaathi.app.databinding.ItemTripDayBinding;
import com.safarsaathi.app.databinding.ItemTripLinkBinding;

import java.util.Locale;
import java.util.Objects;

/**
 * Draws the timeline rows built by {@link TripRowBuilder}: day headers, booking cards and link rows.
 *
 * <p>ListAdapter compares the old and new list (DiffUtil) and only redraws rows that changed, e.g. the
 * three blocks a flight delay moved, instead of the whole list.
 */
final class TripTimelineAdapter extends ListAdapter<TripRow, RecyclerView.ViewHolder> {

    /** Called when a booking card is tapped. */
    interface OnBlockClickListener {
        void onBlockClick(long blockId);
    }

    private static final DiffUtil.ItemCallback<TripRow> DIFF = new DiffUtil.ItemCallback<TripRow>() {
        @Override
        public boolean areItemsTheSame(@NonNull TripRow oldRow, @NonNull TripRow newRow) {
            return oldRow.key().equals(newRow.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull TripRow oldRow, @NonNull TripRow newRow) {
            return oldRow.equals(newRow);
        }
    };

    private final OnBlockClickListener clickListener;

    TripTimelineAdapter(OnBlockClickListener clickListener) {
        super(DIFF);
        this.clickListener = clickListener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).kind.ordinal();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TripRow.Kind.DAY_HEADER.ordinal()) {
            return new DayHolder(ItemTripDayBinding.inflate(inflater, parent, false));
        }
        if (viewType == TripRow.Kind.BLOCK.ordinal()) {
            return new BlockHolder(ItemTripBlockBinding.inflate(inflater, parent, false), clickListener);
        }
        return new LinkHolder(ItemTripLinkBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TripRow row = getItem(position);
        if (holder instanceof DayHolder) {
            ((DayHolder) holder).bind(row);
        } else if (holder instanceof BlockHolder) {
            ((BlockHolder) holder).bind(row);
        } else if (holder instanceof LinkHolder) {
            ((LinkHolder) holder).bind(row);
        }
    }

    // ---- Day header: "Sun, 11 Oct" ----

    static final class DayHolder extends RecyclerView.ViewHolder {
        private final ItemTripDayBinding binding;

        DayHolder(ItemTripDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // Screen readers can jump from heading to heading, i.e. from day to day.
            ViewCompat.setAccessibilityHeading(binding.getRoot(), true);
        }

        void bind(TripRow row) {
            Locale locale = binding.getRoot().getResources().getConfiguration().getLocales().get(0);
            binding.dayTitle.setText(TripTime.dayHeader(Objects.requireNonNull(row.day), locale));
        }
    }

    // ---- Booking card ----

    static final class BlockHolder extends RecyclerView.ViewHolder {
        private final ItemTripBlockBinding binding;
        private final OnBlockClickListener clickListener;
        // The card's normal outline, remembered so a recycled card can go back to it after being red.
        private final ColorStateList normalStrokeColor;
        private final int normalStrokeWidth;
        private final int alertStrokeWidth;
        private long blockId;

        BlockHolder(ItemTripBlockBinding binding, OnBlockClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
            normalStrokeColor = binding.blockCard.getStrokeColorStateList();
            normalStrokeWidth = binding.blockCard.getStrokeWidth();
            alertStrokeWidth = binding.getRoot().getResources().getDimensionPixelSize(R.dimen.trip_alert_stroke_width);
            binding.blockCard.setOnClickListener(view -> this.clickListener.onBlockClick(blockId));
        }

        void bind(TripRow row) {
            BlockEntity block = Objects.requireNonNull(row.block);
            Context context = binding.getRoot().getContext();
            blockId = block.id;

            binding.blockTime.setText(row.timeText);
            binding.blockTitle.setText(block.title);
            binding.blockIcon.setImageResource(TripUi.iconFor(block.type));
            binding.blockIcon.setContentDescription(context.getString(TripUi.typeLabel(block.type)));

            boolean hasLocation = block.locationName != null && !block.locationName.trim().isEmpty();
            binding.blockLocation.setText(hasLocation ? block.locationName : "");
            binding.blockLocation.setVisibility(hasLocation ? View.VISIBLE : View.GONE);

            // Cards are reused while scrolling, so every bind must set (or clear) every changing look.
            boolean cancelled = BlockEntity.STATUS_CANCELLED.equals(block.status);
            int paintFlags = binding.blockTitle.getPaintFlags();
            binding.blockTitle.setPaintFlags(cancelled
                    ? paintFlags | Paint.STRIKE_THRU_TEXT_FLAG
                    : paintFlags & ~Paint.STRIKE_THRU_TEXT_FLAG);

            TripUi.styleStatusChip(binding.blockStatus, block.status);
            binding.blockMoved.setVisibility(
                    BlockEntity.STATUS_AUTO_ADJUSTED.equals(block.status) ? View.VISIBLE : View.GONE);
            binding.blockAnchor.setVisibility(block.anchor ? View.VISIBLE : View.GONE);

            // A thick red outline makes a block that needs a decision easy to spot in the list.
            if (BlockEntity.STATUS_NEEDS_USER.equals(block.status)) {
                binding.blockCard.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.status_needs_user_container)));
                binding.blockCard.setStrokeWidth(alertStrokeWidth);
            } else {
                binding.blockCard.setStrokeColor(normalStrokeColor);
                binding.blockCard.setStrokeWidth(normalStrokeWidth);
            }
        }
    }

    // ---- Link row: "75 min by cab" ----

    static final class LinkHolder extends RecyclerView.ViewHolder {
        private final ItemTripLinkBinding binding;

        LinkHolder(ItemTripLinkBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TripRow row) {
            Context context = binding.getRoot().getContext();
            binding.linkText.setText(TripUi.linkText(context, Objects.requireNonNull(row.link), row.planeChange));
        }
    }
}
