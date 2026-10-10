package com.safarsaathi.app.ui.vault;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.capture.CropMath;
import com.safarsaathi.app.databinding.FragmentVaultCropBinding;

/**
 * Manual crop (layout: fragment_vault_crop.xml) for a photo from the vault's own camera or an imported photo: drag
 * the corners onto the document, then "Use this page". Camera photos go back to the camera for the next page;
 * an imported photo goes on to be read and confirmed.
 */
public class VaultCropFragment extends VaultScreen {

    /** Saved pages are at most this many pixels on their longer side (sharp, but not huge). */
    private static final int MAX_PAGE_SIDE = 2400;
    /** First guess for a camera photo: the document fills most of it. */
    private static final float CAMERA_INSET = 0.06f;

    @Nullable
    private FragmentVaultCropBinding binding;
    private CaptureViewModel capture;

    public VaultCropFragment() {
        super(R.layout.fragment_vault_crop);
    }

    @Override
    protected void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState) {
        binding = FragmentVaultCropBinding.bind(content);
        capture = CaptureViewModel.of(this);
        Bitmap photo = capture.pendingCrop();
        if (photo == null) {
            NavHostFragment.findNavController(this).popBackStack(); // nothing to crop (e.g. after the vault was reset)
            return;
        }
        float[] start = capture.source() == DocSource.SCAN
                ? CropMath.insetCorners(photo.getWidth(), photo.getHeight(), CAMERA_INSET)
                : CropMath.fullCorners(photo.getWidth(), photo.getHeight());
        float[] saved = savedInstanceState == null ? null : savedInstanceState.getFloatArray("corners");
        binding.cropView.setPhoto(photo, saved != null ? saved : start);
        binding.cropWhole.setOnClickListener(button ->
                binding.cropView.setCorners(CropMath.fullCorners(photo.getWidth(), photo.getHeight())));
        binding.cropUse.setOnClickListener(button -> usePage(photo));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putFloatArray("corners", binding.cropView.getCorners());
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void usePage(Bitmap photo) {
        if (binding == null) {
            return;
        }
        binding.cropUse.setEnabled(false);
        Bitmap page = CropView.warp(photo, binding.cropView.getCorners(), MAX_PAGE_SIDE);
        capture.addCroppedPage(page, () -> {
            if (!hasView()) {
                return; // the screen was closed meanwhile; the page is kept and shows up on the camera screen
            }
            capture.setPendingCrop(null); // the straightened page is kept; the photo is not needed any more
            NavController navController = NavHostFragment.findNavController(this);
            if (capture.source() == DocSource.SCAN) {
                navController.popBackStack(); // back to the camera for the next page
            } else {
                capture.analyse();
                // Replace this screen, so "back" from the confirm screen does not offer the same page again.
                navController.navigate(R.id.vault_edit_document, null,
                        new NavOptions.Builder().setPopUpTo(R.id.vault_crop, true).build());
            }
        }, error -> {
            if (binding != null) {
                binding.cropUse.setEnabled(true);
                say(R.string.vault_error_generic);
            }
        });
    }
}
