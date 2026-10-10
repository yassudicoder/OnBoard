package com.safarsaathi.app.ui.wallet;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.WalletRepository;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.DocumentEntity;
import com.safarsaathi.app.data.local.IdDocumentEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.data.wallet.BcbpParser;
import com.safarsaathi.app.data.wallet.BoardingPass;
import com.safarsaathi.app.data.wallet.IdDocuments;
import com.safarsaathi.app.databinding.DialogAddIdBinding;
import com.safarsaathi.app.databinding.DialogPasteCodeBinding;
import com.safarsaathi.app.databinding.FragmentWalletBinding;
import com.safarsaathi.app.databinding.ItemBoardingPassBinding;
import com.safarsaathi.app.databinding.ItemIdDocumentBinding;
import com.safarsaathi.app.databinding.ItemWalletBookingBinding;
import com.safarsaathi.app.ui.trip.BlockDetailsSheet;
import com.safarsaathi.app.ui.trip.TripTime;
import com.safarsaathi.app.ui.trip.TripUi;
import com.safarsaathi.app.ui.trip.TripViewModel;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wallet tab, the offline locker (layout: res/layout/fragment_wallet.xml). Everything it shows is stored on the
 * phone, so it opens with no internet:
 * <ul>
 *   <li><b>Boarding passes</b>: the traveller picks a photo of the barcode (ML Kit reads it on the phone) or pastes
 *       the barcode's text. {@link BcbpParser} must be able to read it before it is saved, so only real boarding
 *       pass codes end up here. Each pass says whether its booking reference belongs to this trip.</li>
 *   <li><b>ID documents</b>: Aadhaar (ONLY the last 4 digits), PAN (always shown masked) and visas with their
 *       validity. Phone-only; rules in {@link IdDocuments}.</li>
 *   <li><b>Booking references</b>: every booking of the saved trip that has a reference (PNR, hotel, cab, table),
 *       from the same Room data as the Trip tab. Tap one for its details.</li>
 *   <li><b>Download PDF</b>: all of the above as one file the traveller saves where they like
 *       ({@link TravelPackPdf}), e.g. in Downloads, to print or share.</li>
 * </ul>
 */
public class WalletFragment extends Fragment {

    private static final String TAG = "SafarSaathi";
    private static final String DETAILS_SHEET_TAG = "block-details";

    /** "Scan photo": Android's photo picker (needs no permission), then ML Kit reads the barcode in the photo. */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::scanPhoto);

    /** "Download PDF": Android's "save as" screen (needs no permission); the PDF is written to the chosen file. */
    private final ActivityResultLauncher<String> savePdf =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), this::writePdf);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Only valid between onViewCreated and onDestroyView.
    private FragmentWalletBinding binding;

    private WalletRepository wallet;
    private List<DocumentEntity> passes = Collections.emptyList();
    private List<BlockEntity> blocks = Collections.emptyList();
    private boolean blocksLoaded;
    private List<IdDocumentEntity> ids = Collections.emptyList();
    @Nullable
    private TripEntity trip;

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public WalletFragment() {
        super(R.layout.fragment_wallet);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentWalletBinding.bind(view);
        wallet = ServiceLocator.get().getWalletRepository();
        // The same saved trip as Home and Trip. The Wallet never asks the server: it only shows what is saved.
        TripViewModel tripViewModel = new ViewModelProvider(this, TripViewModel.factory(AppConfig.DEMO_TRIP_ID))
                .get(TripViewModel.class);

        ViewCompat.setAccessibilityHeading(binding.walletTitle, true);
        ViewCompat.setAccessibilityHeading(binding.walletPassesTitle, true);
        ViewCompat.setAccessibilityHeading(binding.walletBookingsTitle, true);
        ViewCompat.setAccessibilityHeading(binding.walletIdsTitle, true);
        binding.walletDownload.setOnClickListener(button ->
                savePdf.launch(TravelPackPdf.fileName(requireContext(), blocks)));
        binding.walletAddId.setOnClickListener(button -> chooseIdKind());
        binding.walletScan.setOnClickListener(button -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.walletPaste.setOnClickListener(button -> showPasteDialog());

        LifecycleOwner owner = getViewLifecycleOwner();
        wallet.observeBoardingPasses(AppConfig.DEMO_TRIP_ID).observe(owner, newPasses -> {
            passes = newPasses == null ? Collections.emptyList() : newPasses;
            renderPasses();
        });
        tripViewModel.getBlocks().observe(owner, newBlocks -> {
            blocksLoaded = true;
            blocks = newBlocks == null ? Collections.emptyList() : newBlocks;
            renderBookings();
            renderPasses(); // "matches your trip's booking" depends on the trip's bookings
        });
        tripViewModel.getTrip().observe(owner, newTrip -> trip = newTrip);
        wallet.observeIdDocuments().observe(owner, newIds -> {
            ids = newIds == null ? Collections.emptyList() : newIds;
            renderIds();
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---- Boarding passes ----

    private void renderPasses() {
        if (binding == null) {
            return;
        }
        binding.walletPassesList.removeAllViews();
        LocalDate today = LocalDate.now(AppConfig.TRIP_ZONE);
        Set<String> tripFlightRefs = tripFlightRefs();
        int shown = 0;
        for (DocumentEntity document : passes) {
            try {
                BoardingPass pass = BcbpParser.parse(document.rawData, today);
                for (BoardingPass.Leg leg : pass.legs) {
                    binding.walletPassesList.addView(passCard(pass, leg, document.id, tripFlightRefs));
                    shown++;
                }
            } catch (BcbpParser.InvalidBoardingPassException e) {
                // Cannot happen for passes saved by this screen (they were read before saving); skip, do not crash.
                Log.w(TAG, "Saved boarding pass " + document.id + " is not readable: " + e.getMessage());
            }
        }
        binding.walletPassesEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private View passCard(BoardingPass pass, BoardingPass.Leg leg, long documentId, Set<String> tripFlightRefs) {
        Context context = requireContext();
        ItemBoardingPassBinding card =
                ItemBoardingPassBinding.inflate(getLayoutInflater(), binding.walletPassesList, false);
        card.passFlight.setText(leg.flight());
        card.passDate.setText(TripTime.dayHeader(leg.date, locale()));
        card.passFrom.setText(leg.from);
        card.passTo.setText(leg.to);
        card.passPassenger.setText(pass.passengerName());
        card.passSeat.setText(leg.seat.isEmpty() || "0".equals(leg.seat) ? getString(R.string.wallet_seat_none) : leg.seat);
        card.passPnr.setText(leg.pnr);

        boolean matches = tripFlightRefs.contains(leg.pnr);
        card.passMatch.setText(matches ? getString(R.string.wallet_match, leg.pnr) : getString(R.string.wallet_no_match));
        card.passMatch.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                matches ? R.color.status_ok_container : R.color.status_done_container)));
        card.passMatch.setTextColor(ContextCompat.getColor(context,
                matches ? R.color.status_ok_on_container : R.color.status_done_on_container));

        card.passRemove.setOnClickListener(button -> {
            wallet.deleteDocument(documentId);
            showMessage(getString(R.string.wallet_removed));
        });
        return card.getRoot();
    }

    /** The trip's flight booking references (PNRs), to tell whether a boarding pass belongs to this trip. */
    private Set<String> tripFlightRefs() {
        Set<String> refs = new HashSet<>();
        for (BlockEntity block : blocks) {
            if (BlockEntity.TYPE_FLIGHT.equals(block.type) && block.providerRef != null) {
                refs.add(block.providerRef.trim());
            }
        }
        return refs;
    }

    /** Opens the photo picker's result: reads every barcode in the photo and saves the first boarding pass. */
    private void scanPhoto(@Nullable Uri photo) {
        if (photo == null || !isAdded()) {
            return; // the traveller closed the picker
        }
        InputImage image;
        try {
            image = InputImage.fromFilePath(requireContext(), photo);
        } catch (IOException e) {
            showMessage(getString(R.string.wallet_photo_failed));
            return;
        }
        // Boarding passes use PDF417 (most airlines), Aztec or QR; Data Matrix is allowed too.
        BarcodeScanner scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC, Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_DATA_MATRIX)
                .build());
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!isAdded()) {
                        return;
                    }
                    String firstProblem = null;
                    for (Barcode barcode : barcodes) {
                        String text = barcode.getRawValue();
                        if (text == null) {
                            continue;
                        }
                        String problem = saveIfBoardingPass(text);
                        if (problem == null) {
                            return; // saved (or already saved)
                        }
                        if (firstProblem == null) {
                            firstProblem = problem;
                        }
                    }
                    showMessage(firstProblem != null ? firstProblem : getString(R.string.wallet_no_barcode));
                })
                .addOnFailureListener(error -> {
                    if (isAdded()) {
                        showMessage(getString(R.string.wallet_photo_failed));
                    }
                })
                .addOnCompleteListener(task -> scanner.close());
    }

    private void showPasteDialog() {
        DialogPasteCodeBinding dialogBinding = DialogPasteCodeBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wallet_paste_title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.wallet_save, null) // set below, so a wrong code keeps the dialog open
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            CharSequence typed = dialogBinding.pasteInput.getText();
            String problem = saveIfBoardingPass(typed == null ? "" : typed.toString());
            if (problem == null) {
                dialog.dismiss();
            } else {
                dialogBinding.pasteLayout.setError(problem);
            }
        }));
        dialog.show();
    }

    /**
     * Saves the text if it is a boarding pass code.
     *
     * @return null when saved (or already saved), otherwise why not, in plain words
     */
    @Nullable
    private String saveIfBoardingPass(String text) {
        // A barcode is one line: drop line breaks (e.g. from pasting) and spaces in front. Spaces INSIDE matter.
        String code = text.replace("\r", "").replace("\n", "").replaceFirst("^\\s+", "");
        try {
            BcbpParser.parse(code, LocalDate.now(AppConfig.TRIP_ZONE));
        } catch (BcbpParser.InvalidBoardingPassException e) {
            return e.getMessage();
        }
        for (DocumentEntity saved : passes) {
            if (saved.rawData.equals(code)) {
                showMessage(getString(R.string.wallet_already_saved));
                return null;
            }
        }
        wallet.addBoardingPass(AppConfig.DEMO_TRIP_ID, code);
        showMessage(getString(R.string.wallet_saved));
        return null;
    }

    // ---- ID documents ----

    private void renderIds() {
        if (binding == null) {
            return;
        }
        binding.walletIdsList.removeAllViews();
        Context context = requireContext();
        LocalDate today = LocalDate.now(AppConfig.TRIP_ZONE);
        for (IdDocumentEntity id : ids) {
            ItemIdDocumentBinding row = ItemIdDocumentBinding.inflate(getLayoutInflater(), binding.walletIdsList, false);
            row.idHolder.setText(id.holderName);
            row.idDetail.setVisibility(View.GONE);
            row.idStatus.setVisibility(View.GONE);
            if (IdDocuments.KIND_AADHAAR.equals(id.kind)) {
                row.idTitle.setText(R.string.ids_title_aadhaar);
                row.idNumber.setText(IdDocuments.maskAadhaar(id.number));
            } else if (IdDocuments.KIND_PAN.equals(id.kind)) {
                row.idTitle.setText(R.string.ids_title_pan);
                row.idNumber.setText(IdDocuments.maskPan(id.number));
            } else {
                row.idTitle.setText(getString(R.string.ids_title_visa, id.country == null ? "" : id.country));
                row.idNumber.setText(id.number);
                row.idDetail.setText(WalletIdText.visaDetail(context, id));
                row.idDetail.setVisibility(View.VISIBLE);
                String status = WalletIdText.visaStatus(context, id, today);
                if (!status.isEmpty()) {
                    int[] colors = WalletIdText.visaStatusColors(id, today);
                    row.idStatus.setText(status);
                    row.idStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, colors[0])));
                    row.idStatus.setTextColor(ContextCompat.getColor(context, colors[1]));
                    row.idStatus.setVisibility(View.VISIBLE);
                }
            }
            long documentId = id.id;
            row.idRemove.setOnClickListener(button -> {
                wallet.deleteIdDocument(documentId);
                showMessage(getString(R.string.ids_removed));
            });
            binding.walletIdsList.addView(row.getRoot());
        }
    }

    /** First question: which kind of ID. */
    private void chooseIdKind() {
        String[] kinds = {getString(R.string.ids_kind_aadhaar), getString(R.string.ids_kind_pan),
                getString(R.string.ids_kind_visa)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ids_choose_kind)
                .setItems(kinds, (dialog, which) -> showAddIdDialog(which == 0 ? IdDocuments.KIND_AADHAAR
                        : which == 1 ? IdDocuments.KIND_PAN : IdDocuments.KIND_VISA))
                .show();
    }

    /** The form for one kind of ID: only its fields are shown; mistakes keep the dialog open with a message. */
    private void showAddIdDialog(String kind) {
        DialogAddIdBinding form = DialogAddIdBinding.inflate(getLayoutInflater());
        boolean aadhaar = IdDocuments.KIND_AADHAAR.equals(kind);
        boolean pan = IdDocuments.KIND_PAN.equals(kind);
        boolean visa = IdDocuments.KIND_VISA.equals(kind);
        form.idAadhaarLayout.setVisibility(aadhaar ? View.VISIBLE : View.GONE);
        form.idPanLayout.setVisibility(pan ? View.VISIBLE : View.GONE);
        int visaFields = visa ? View.VISIBLE : View.GONE;
        form.idCountryLayout.setVisibility(visaFields);
        form.idVisaTypeLayout.setVisibility(visaFields);
        form.idVisaNumberLayout.setVisibility(visaFields);
        form.idValidFromLayout.setVisibility(visaFields);
        form.idValidUntilLayout.setVisibility(visaFields);
        form.idMultipleEntry.setVisibility(visaFields);
        form.idName.setText(getString(R.string.sample_traveller_name));

        int titleRes = aadhaar ? R.string.ids_add_aadhaar : pan ? R.string.ids_add_pan : R.string.ids_add_visa;
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setView(form.getRoot())
                .setPositiveButton(R.string.ids_save, null) // set below, so mistakes keep the dialog open
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            if (saveId(kind, form)) {
                dialog.dismiss();
                showMessage(getString(R.string.ids_saved));
            }
        }));
        dialog.show();
    }

    /** Checks the form; saves and returns true when it is complete, otherwise shows what to fix. */
    private boolean saveId(String kind, DialogAddIdBinding form) {
        String name = text(form.idName.getText());
        form.idNameLayout.setError(name.isEmpty() ? getString(R.string.ids_error_name) : null);
        boolean ok = !name.isEmpty();
        if (IdDocuments.KIND_AADHAAR.equals(kind)) {
            String last4 = text(form.idAadhaarLast4.getText());
            boolean valid = IdDocuments.isValidAadhaarLast4(last4);
            form.idAadhaarLayout.setError(valid ? null : getString(R.string.ids_error_aadhaar));
            if (ok && valid) {
                wallet.addAadhaar(name, last4);
                return true;
            }
            return false;
        }
        if (IdDocuments.KIND_PAN.equals(kind)) {
            String pan = IdDocuments.normalisePan(text(form.idPan.getText()));
            boolean valid = IdDocuments.isValidPan(pan);
            form.idPanLayout.setError(valid ? null : getString(R.string.ids_error_pan));
            if (ok && valid) {
                wallet.addPan(name, pan);
                return true;
            }
            return false;
        }
        String country = text(form.idCountry.getText());
        String number = text(form.idVisaNumber.getText());
        LocalDate from = IdDocuments.parseDate(text(form.idValidFrom.getText()));
        LocalDate until = IdDocuments.parseDate(text(form.idValidUntil.getText()));
        form.idCountryLayout.setError(country.isEmpty() ? getString(R.string.ids_error_country) : null);
        form.idVisaNumberLayout.setError(number.isEmpty() ? getString(R.string.ids_error_visa_number) : null);
        form.idValidFromLayout.setError(from == null ? getString(R.string.ids_error_date) : null);
        if (until == null) {
            form.idValidUntilLayout.setError(getString(R.string.ids_error_date));
        } else if (from != null && until.isBefore(from)) {
            form.idValidUntilLayout.setError(getString(R.string.ids_error_until_before_from));
        } else {
            form.idValidUntilLayout.setError(null);
        }
        if (!ok || country.isEmpty() || number.isEmpty() || from == null || until == null || until.isBefore(from)) {
            return false;
        }
        wallet.addVisa(name, country, text(form.idVisaType.getText()), number, from, until,
                form.idMultipleEntry.isChecked());
        return true;
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    // ---- Download PDF ----

    /** Writes the PDF to the file the traveller chose, on a background thread, then offers to open it. */
    private void writePdf(@Nullable Uri file) {
        if (file == null || !isAdded()) {
            return; // the traveller closed the "save as" screen
        }
        Context appContext = requireContext().getApplicationContext();
        TravelPackPdf.Content content = pdfContent();
        showMessage(getString(R.string.wallet_pdf_working));
        ServiceLocator.get().getIoExecutor().execute(() -> {
            boolean saved;
            try (OutputStream out = appContext.getContentResolver().openOutputStream(file)) {
                if (out == null) {
                    throw new IOException("No output stream for " + file);
                }
                TravelPackPdf.write(appContext, content, out);
                saved = true;
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Could not write the PDF", e);
                saved = false;
            }
            boolean ok = saved;
            mainHandler.post(() -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (ok) {
                    showPdfSaved(file);
                } else {
                    showMessage(getString(R.string.wallet_pdf_failed));
                }
            });
        });
    }

    /** Everything the PDF shows, copied now on the main thread (the lists may change while it is written). */
    private TravelPackPdf.Content pdfContent() {
        LocalDate today = LocalDate.now(AppConfig.TRIP_ZONE);
        List<TravelPackPdf.SavedPass> readable = new ArrayList<>();
        for (DocumentEntity document : passes) {
            try {
                readable.add(new TravelPackPdf.SavedPass(BcbpParser.parse(document.rawData, today), document.rawData));
            } catch (BcbpParser.InvalidBoardingPassException e) {
                Log.w(TAG, "Saved boarding pass " + document.id + " left out of the PDF: " + e.getMessage());
            }
        }
        return new TravelPackPdf.Content(getString(R.string.sample_traveller_name), trip,
                new ArrayList<>(blocks), readable, new ArrayList<>(ids), Instant.now());
    }

    private void showPdfSaved(Uri file) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), R.string.wallet_pdf_saved, Snackbar.LENGTH_LONG)
                .setAction(R.string.wallet_pdf_open, button -> openPdf(file));
        View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            snackbar.setAnchorView(bottomNav);
        }
        snackbar.show();
    }

    private void openPdf(Uri file) {
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(file, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(view);
        } catch (ActivityNotFoundException e) {
            showMessage(getString(R.string.wallet_pdf_no_viewer));
        }
    }

    // ---- Booking references ----

    private void renderBookings() {
        if (binding == null) {
            return;
        }
        binding.walletBookingsList.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        Context context = requireContext();
        int shown = 0;
        for (BlockEntity block : blocks) { // already in time order (TripDao)
            if (block.providerRef == null || block.providerRef.trim().isEmpty()) {
                continue; // nothing booked, e.g. waiting for bags
            }
            ItemWalletBookingBinding row =
                    ItemWalletBookingBinding.inflate(inflater, binding.walletBookingsList, false);
            row.bookingIcon.setImageResource(TripUi.iconFor(block.type));
            row.bookingIcon.setContentDescription(getString(TripUi.typeLabel(block.type)));
            row.bookingTitle.setText(block.title);
            row.bookingRef.setText(TripUi.bookingText(context, block));
            row.bookingWhen.setText(getString(R.string.sheet_when_value,
                    TripTime.dayHeader(TripTime.localDate(block.startTime), locale()),
                    TripTime.timeRange(block.startTime, block.endTime)));
            long blockId = block.id;
            row.getRoot().setOnClickListener(card -> showBlockDetails(blockId));
            binding.walletBookingsList.addView(row.getRoot());
            shown++;
        }
        binding.walletBookingsEmpty.setVisibility(blocksLoaded && shown == 0 ? View.VISIBLE : View.GONE);
    }

    /** The same details sheet as on the Trip tab (it reads this screen's TripViewModel). */
    private void showBlockDetails(long blockId) {
        FragmentManager fragments = getChildFragmentManager();
        if (fragments.isStateSaved() || fragments.findFragmentByTag(DETAILS_SHEET_TAG) != null) {
            return;
        }
        BlockDetailsSheet.newInstance(blockId).show(fragments, DETAILS_SHEET_TAG);
    }

    // ---- Helpers ----

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    /** A short message at the bottom, above the tab bar. */
    private void showMessage(String message) {
        if (binding == null) {
            return;
        }
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            snackbar.setAnchorView(bottomNav);
        }
        snackbar.show();
    }
}
