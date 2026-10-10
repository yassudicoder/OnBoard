package com.safarsaathi.app.ui.trip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.safarsaathi.app.R;
import com.safarsaathi.app.databinding.ItemTripHeaderBinding;

import java.util.Objects;

/**
 * The first row of the timeline: trip title, summary and sync line. A separate adapter (joined to the
 * timeline with a ConcatAdapter in TripFragment), so the sync line can change every few seconds without
 * touching the booking rows. Shows nothing (0 rows) while there is no trip.
 */
final class TripHeaderAdapter extends RecyclerView.Adapter<TripHeaderAdapter.HeaderHolder> {

    /** What the header shows. Immutable; equal headers are not redrawn. */
    static final class Header {
        final String title;
        final String summary;
        final String syncLine;
        final boolean offline;

        Header(String title, String summary, String syncLine, boolean offline) {
            this.title = title;
            this.summary = summary;
            this.syncLine = syncLine;
            this.offline = offline;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Header)) {
                return false;
            }
            Header that = (Header) other;
            return offline == that.offline
                    && title.equals(that.title)
                    && summary.equals(that.summary)
                    && syncLine.equals(that.syncLine);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, summary, syncLine, offline);
        }
    }

    @Nullable
    private Header header;

    /** Shows a new header (null = hide it). Only tells the list about real changes. */
    void setHeader(@Nullable Header newHeader) {
        Header oldHeader = header;
        header = newHeader;
        if (oldHeader == null && newHeader != null) {
            notifyItemInserted(0);
        } else if (oldHeader != null && newHeader == null) {
            notifyItemRemoved(0);
        } else if (newHeader != null && !newHeader.equals(oldHeader)) {
            notifyItemChanged(0);
        }
    }

    @Override
    public int getItemCount() {
        return header == null ? 0 : 1;
    }

    @NonNull
    @Override
    public HeaderHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new HeaderHolder(ItemTripHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderHolder holder, int position) {
        if (header != null) {
            holder.bind(header);
        }
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemTripHeaderBinding binding;

        HeaderHolder(ItemTripHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Header header) {
            binding.headerTitle.setText(header.title);
            binding.headerSummary.setText(header.summary);
            binding.headerSync.setText(header.syncLine);
            binding.headerSync.setVisibility(header.syncLine.isEmpty() ? View.GONE : View.VISIBLE);
            // "Cloud off" icon in front of the text only when offline (0 = no icon). The icon tints itself
            // with the theme's colorControlNormal, so it matches light and dark mode.
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(binding.headerSync,
                    header.offline ? R.drawable.ic_offline_24 : 0, 0, 0, 0);
        }
    }
}
