package com.safarsaathi.app.ui.trip;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.databinding.SheetBlockDetailsBinding;

import java.util.List;
import java.util.Locale;

/**
 * Details of one block, in plain words, as a sheet that slides up from the bottom
 * (layout: res/layout/sheet_block_details.xml). Opened by tapping a card or the red banner.
 *
 * <p>It shares TripFragment's ViewModel and watches the saved blocks, so if a delay changes this block
 * while the sheet is open, the sheet updates too. If the block disappears (e.g. demo data reloaded),
 * the sheet closes.
 */
public class BlockDetailsSheet extends BottomSheetDialogFragment {

    private static final String ARG_BLOCK_ID = "blockId";

    private SheetBlockDetailsBinding binding;

    /** Use this instead of the constructor: the block id survives screen rotation inside the arguments. */
    public static BlockDetailsSheet newInstance(long blockId) {
        Bundle args = new Bundle();
        args.putLong(ARG_BLOCK_ID, blockId);
        BlockDetailsSheet sheet = new BlockDetailsSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetBlockDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        long blockId = requireArguments().getLong(ARG_BLOCK_ID);
        // requireParentFragment() = the screen that opened this sheet (Trip or Home tab), so we get ITS ViewModel.
        TripViewModel viewModel = new ViewModelProvider(requireParentFragment(),
                TripViewModel.factory(AppConfig.DEMO_TRIP_ID)).get(TripViewModel.class);
        viewModel.getBlocks().observe(getViewLifecycleOwner(), blocks -> {
            BlockEntity block = find(blocks, blockId);
            if (block != null) {
                bind(block);
            } else if (blocks != null) {
                dismissAllowingStateLoss(); // the block is gone from the saved plan
            }
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void bind(BlockEntity block) {
        if (binding == null) {
            return;
        }
        binding.sheetIcon.setImageResource(TripUi.iconFor(block.type));
        binding.sheetIcon.setContentDescription(getString(TripUi.typeLabel(block.type)));
        binding.sheetTitle.setText(block.title);
        binding.sheetAnchor.setVisibility(block.anchor ? View.VISIBLE : View.GONE);

        TripUi.styleStatusChip(binding.sheetStatus, block);
        binding.sheetStatusExplanation.setText(TripUi.statusExplanation(TripOverview.displayStatus(block)));

        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String day = TripTime.dayHeader(TripTime.localDate(block.startTime), locale);
        String when = getString(R.string.sheet_when_value, day, TripTime.timeRange(block.startTime, block.endTime));
        String planned = TripUi.plannedText(requireContext(), block);
        binding.sheetWhen.setText(planned == null ? when : when + "\n" + planned);

        boolean hasLocation = block.locationName != null && !block.locationName.trim().isEmpty();
        binding.sheetWhereGroup.setVisibility(hasLocation ? View.VISIBLE : View.GONE);
        binding.sheetWhere.setText(hasLocation ? block.locationName : "");

        binding.sheetBooking.setText(TripUi.bookingText(requireContext(), block));
        binding.sheetFlex.setText(TripUi.flexText(block.flex));
        binding.sheetAnchorNote.setVisibility(block.anchor ? View.VISIBLE : View.GONE);
        binding.sheetChanges.setText(TripUi.changesText(requireContext(), block));

        boolean hasNotes = block.notes != null && !block.notes.trim().isEmpty();
        binding.sheetNotesGroup.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
        binding.sheetNotes.setText(hasNotes ? block.notes : "");
    }

    @Nullable
    private static BlockEntity find(@Nullable List<BlockEntity> blocks, long blockId) {
        if (blocks == null) {
            return null;
        }
        for (BlockEntity block : blocks) {
            if (block.id == blockId) {
                return block;
            }
        }
        return null;
    }
}
