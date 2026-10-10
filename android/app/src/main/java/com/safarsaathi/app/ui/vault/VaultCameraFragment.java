package com.safarsaathi.app.ui.vault;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Size;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.safarsaathi.app.R;
import com.safarsaathi.app.databinding.FragmentVaultCameraBinding;

import java.util.concurrent.ExecutionException;

/**
 * The vault's own camera (layout: fragment_vault_camera.xml), used when Google's document scanner cannot run (no
 * Google Play services, its download not done yet while offline, or a phone with too little memory).
 *
 * <p>Each photo is taken straight into memory (CameraX "in-memory capture"), never saved to the gallery or a file,
 * then goes to the crop screen; "Done" reads all pages and opens the confirm screen. The screen is a vault screen,
 * so screenshots are blocked here too.
 */
public class VaultCameraFragment extends VaultScreen {

    /** Photo size asked for: sharp enough for small print, small enough for older phones' memory (about 5 MP). */
    private static final Size PHOTO_SIZE = new Size(2560, 1920);

    @Nullable
    private FragmentVaultCameraBinding binding;
    private CaptureViewModel capture;
    @Nullable
    private ImageCapture imageCapture;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), allowed -> {
                if (allowed) {
                    startCamera();
                } else {
                    showDenied();
                }
            });

    public VaultCameraFragment() {
        super(R.layout.fragment_vault_camera);
    }

    @Override
    protected void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState) {
        binding = FragmentVaultCameraBinding.bind(content);
        capture = CaptureViewModel.of(this);
        binding.cameraShutter.setOnClickListener(button -> takePhoto());
        binding.cameraDone.setOnClickListener(button -> finish());
        renderPageCount();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (savedInstanceState == null) {
            cameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            showDenied();
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        imageCapture = null;
        super.onDestroyView();
    }

    private void renderPageCount() {
        if (binding == null) {
            return;
        }
        int pages = capture.pageCount();
        binding.cameraPages.setText(getResources().getQuantityString(R.plurals.vault_camera_pages, pages, pages));
        binding.cameraDone.setEnabled(pages > 0);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            if (binding == null) {
                return;
            }
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());
                ResolutionSelector size = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(PHOTO_SIZE,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setResolutionSelector(size)
                        .build();
                provider.unbindAll();
                // Tied to this screen's lifecycle: the camera stops by itself when the screen goes away.
                provider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_BACK_CAMERA, preview,
                        imageCapture);
            } catch (ExecutionException | InterruptedException | IllegalArgumentException e) {
                // No usable back camera (or it is busy): explain instead of a black preview.
                showDenied();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void showDenied() {
        if (binding == null) {
            return;
        }
        binding.cameraDenied.setVisibility(View.VISIBLE);
        binding.cameraShutter.setEnabled(false);
    }

    private void takePhoto() {
        if (imageCapture == null || binding == null) {
            return;
        }
        binding.cameraShutter.setEnabled(false);
        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Bitmap photo;
                        int rotation;
                        try {
                            photo = image.toBitmap();
                            rotation = image.getImageInfo().getRotationDegrees();
                        } finally {
                            image.close();
                        }
                        if (binding == null) {
                            return;
                        }
                        binding.cameraShutter.setEnabled(true);
                        capture.setPendingCrop(upright(photo, rotation));
                        NavHostFragment.findNavController(VaultCameraFragment.this).navigate(R.id.vault_crop);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException error) {
                        if (binding != null) {
                            binding.cameraShutter.setEnabled(true);
                            say(R.string.vault_camera_failed);
                        }
                    }
                });
    }

    /** All pages taken: read them and go to the confirm screen (this camera screen is replaced). */
    private void finish() {
        capture.analyse();
        NavHostFragment.findNavController(this).navigate(R.id.vault_edit_document, null,
                new NavOptions.Builder().setPopUpTo(R.id.vault_camera, true).build());
    }

    /** The camera's sensor may be mounted sideways; turn the photo the way the phone was held. */
    private static Bitmap upright(Bitmap photo, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return photo;
        }
        Matrix turn = new Matrix();
        turn.postRotate(rotationDegrees);
        Bitmap rotated = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), turn, true);
        if (rotated != photo) {
            photo.recycle();
        }
        return rotated;
    }
}
