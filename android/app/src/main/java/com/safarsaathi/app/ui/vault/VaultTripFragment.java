package com.safarsaathi.app.ui.vault;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.DocType;
import com.safarsaathi.app.data.vault.VaultRepository;
import com.safarsaathi.app.data.vault.VaultUnavailableException;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.TripDocumentRow;
import com.safarsaathi.app.data.vault.db.TripLegEntity;
import com.safarsaathi.app.data.vault.db.TripVaultEntity;
import com.safarsaathi.app.databinding.FragmentVaultTripBinding;
import com.safarsaathi.app.databinding.ItemVaultAddOptionBinding;
import com.safarsaathi.app.databinding.ItemVaultDocumentBinding;
import com.safarsaathi.app.databinding.ItemVaultSectionBinding;
import com.safarsaathi.app.databinding.SheetVaultAddDocumentBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One trip folder (layout: fragment_vault_trip.xml). Its documents grouped "By leg" (whole trip first, then each leg
 * in order) or "By type" (passport, visas, tickets, ...). Tapping a document opens it. "Add document" offers: scan
 * (Google's scanner, or the vault's own camera), import a PDF or photo, type it in, or reuse a document saved for
 * another trip.
 */
public class VaultTripFragment extends VaultScreen {

    static final String ARG_DOCUMENT_ID = "documentId";

    /** Inside a group: identity documents first (DocType order), then by name. */
    private static final Comparator<TravelDocumentEntity> DOCUMENT_ORDER =
            Comparator.<TravelDocumentEntity, DocType>comparing(document -> document.type)
                    .thenComparing(document -> document.title);

    @Nullable
    private FragmentVaultTripBinding binding;
    @Nullable
    private VaultRepository repository;
    private String tripId = "";
    @Nullable
    private TripVaultEntity trip;
    private List<TripLegEntity> legs = Collections.emptyList();
    private List<TripDocumentRow> documents = Collections.emptyList();
    private boolean leaving;

    public VaultTripFragment() {
        super(R.layout.fragment_vault_trip);
    }

    @Override
    protected void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState) {
        binding = FragmentVaultTripBinding.bind(content);
        ViewCompat.setAccessibilityHeading(binding.folderTitle, true);
        tripId = Objects.requireNonNull(requireArguments().getString(VaultHomeFragment.ARG_TRIP_ID));
        try {
            repository = vault().repository();
        } catch (VaultUnavailableException e) {
            leave(); // the vault home explains what happened
            return;
        }
        binding.folderGrouping.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                render();
            }
        });
        binding.folderDelete.setOnClickListener(button -> confirmDelete());
        binding.folderAdd.setOnClickListener(button -> showAddSheet());

        repository.observeTrip(tripId).observe(getViewLifecycleOwner(), newTrip -> {
            trip = newTrip;
            if (newTrip == null) {
                leave(); // deleted
                return;
            }
            renderHeader();
        });
        repository.observeLegs(tripId).observe(getViewLifecycleOwner(), newLegs -> {
            legs = newLegs == null ? Collections.emptyList() : newLegs;
            render();
        });
        repository.observeTripDocuments(tripId).observe(getViewLifecycleOwner(), rows -> {
            documents = rows == null ? Collections.emptyList() : rows;
            render();
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderHeader() {
        if (binding == null || trip == null) {
            return;
        }
        binding.folderTitle.setText(trip.title);
        binding.folderDates.setText(VaultText.dateRange(requireContext(), trip.startDate, trip.endDate));
        binding.folderSample.setVisibility(trip.sample ? View.VISIBLE : View.GONE);
    }

    private void render() {
        if (binding == null) {
            return;
        }
        binding.folderSections.removeAllViews();
        binding.folderEmpty.setVisibility(documents.isEmpty() ? View.VISIBLE : View.GONE);
        if (binding.folderGrouping.getCheckedButtonId() == R.id.folder_by_type) {
            renderByType();
        } else {
            renderByLeg();
        }
    }

    /** Whole trip first, then each leg in order. Legs without documents still show, so the journey is visible. */
    private void renderByLeg() {
        List<TravelDocumentEntity> wholeTrip = new ArrayList<>();
        Map<String, List<TravelDocumentEntity>> byLeg = new LinkedHashMap<>();
        for (TripDocumentRow row : documents) {
            if (row.legId == null) {
                wholeTrip.add(row.document);
            } else {
                byLeg.computeIfAbsent(row.legId, id -> new ArrayList<>()).add(row.document);
            }
        }
        if (!wholeTrip.isEmpty()) {
            addSection(getString(R.string.vault_whole_trip), getString(R.string.vault_whole_trip_hint));
            addDocuments(wholeTrip);
        }
        for (TripLegEntity leg : legs) {
            String when = VaultText.dateRange(requireContext(), leg.startDate, leg.endDate);
            addSection(leg.title, getString(R.string.vault_leg_subtitle,
                    getString(VaultText.legKindName(leg.kind)), when));
            List<TravelDocumentEntity> legDocuments = byLeg.get(leg.id);
            if (legDocuments == null || legDocuments.isEmpty()) {
                getLayoutInflater().inflate(R.layout.item_vault_empty_leg, binding.folderSections, true);
            } else {
                addDocuments(legDocuments);
            }
        }
    }

    /** One group per document type, in DocType order. A document filed under two legs is listed once. */
    private void renderByType() {
        Map<DocType, Map<String, TravelDocumentEntity>> byType = new LinkedHashMap<>();
        for (DocType type : DocType.values()) {
            byType.put(type, new LinkedHashMap<>());
        }
        for (TripDocumentRow row : documents) {
            Objects.requireNonNull(byType.get(row.document.type)).put(row.document.id, row.document);
        }
        for (Map.Entry<DocType, Map<String, TravelDocumentEntity>> group : byType.entrySet()) {
            if (!group.getValue().isEmpty()) {
                addSection(getString(VaultText.typeName(group.getKey())), null);
                addDocuments(new ArrayList<>(group.getValue().values()));
            }
        }
    }

    private void addSection(String title, @Nullable String subtitle) {
        if (binding == null) {
            return;
        }
        ItemVaultSectionBinding section = ItemVaultSectionBinding.inflate(getLayoutInflater(),
                binding.folderSections, true);
        section.sectionTitle.setText(title);
        ViewCompat.setAccessibilityHeading(section.sectionTitle, true);
        section.sectionSubtitle.setVisibility(subtitle == null ? View.GONE : View.VISIBLE);
        section.sectionSubtitle.setText(subtitle);
    }

    private void addDocuments(List<TravelDocumentEntity> group) {
        if (binding == null) {
            return;
        }
        List<TravelDocumentEntity> sorted = new ArrayList<>(group);
        sorted.sort(DOCUMENT_ORDER);
        LayoutInflater inflater = getLayoutInflater();
        for (TravelDocumentEntity document : sorted) {
            ItemVaultDocumentBinding row = ItemVaultDocumentBinding.inflate(inflater, binding.folderSections, false);
            row.documentTitle.setText(document.title);
            row.documentDetail.setText(VaultText.documentDetail(requireContext(), document));
            row.documentSample.setVisibility(document.sample ? View.VISIBLE : View.GONE);
            String documentId = document.id;
            row.getRoot().setOnClickListener(card -> {
                Bundle args = new Bundle();
                args.putString(ARG_DOCUMENT_ID, documentId);
                NavHostFragment.findNavController(this).navigate(R.id.vault_document, args);
            });
            binding.folderSections.addView(row.getRoot());
        }
    }

    private void confirmDelete() {
        if (repository == null) {
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vault_delete_trip_title)
                .setMessage(R.string.vault_delete_trip_body)
                .setPositiveButton(R.string.vault_delete, (shown, which) ->
                        repository.deleteTrip(tripId, new VaultRepository.Callback<Void>() {
                            @Override
                            public void onDone(Void nothing) {
                                say(R.string.vault_trip_deleted); // the trip observer then leaves the screen
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                if (hasView()) {
                                    say(R.string.vault_error_generic);
                                }
                            }
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        VaultWindow.secure(dialog);
        dialog.show();
    }

    // ============================================================================================
    // Adding documents (V2)
    // ============================================================================================

    /** Google's scanner screen; its answer comes back to {@link #onScanned}. */
    private final ActivityResultLauncher<IntentSenderRequest> scanner =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), this::onScanned);

    /** Android's file picker, limited to PDFs and pictures. */
    private final ActivityResultLauncher<String[]> importer =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImported);

    private void showAddSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        SheetVaultAddDocumentBinding choices = SheetVaultAddDocumentBinding.inflate(getLayoutInflater());
        option(choices.addScan, R.drawable.ic_document_24, R.string.vault_add_scan, R.string.vault_add_scan_hint,
                () -> {
                    sheet.dismiss();
                    startScan();
                });
        option(choices.addImport, R.drawable.ic_download_24, R.string.vault_add_import, R.string.vault_add_import_hint,
                () -> {
                    sheet.dismiss();
                    importer.launch(new String[] {ImportReader.MIME_PDF, "image/*"});
                });
        option(choices.addManual, R.drawable.ic_folder_24, R.string.vault_add_manual, R.string.vault_add_manual_hint,
                () -> {
                    sheet.dismiss();
                    CaptureViewModel.of(this).beginManual(tripId);
                    NavHostFragment.findNavController(this).navigate(R.id.vault_edit_document);
                });
        option(choices.addExisting, R.drawable.ic_id_card_24, R.string.vault_add_existing,
                R.string.vault_add_existing_hint, () -> {
                    sheet.dismiss();
                    chooseExistingDocument();
                });
        sheet.setContentView(choices.getRoot());
        VaultWindow.secure(sheet);
        sheet.show();
    }

    private static void option(ItemVaultAddOptionBinding row, int icon, int title, int hint, Runnable action) {
        row.optionIcon.setImageResource(icon);
        row.optionTitle.setText(title);
        row.optionHint.setText(hint);
        row.getRoot().setOnClickListener(view -> action.run());
    }

    /** Google's scanner if it can run, otherwise the vault's own camera. */
    private void startScan() {
        CaptureViewModel.of(this).begin(tripId, DocSource.SCAN);
        DocumentScanner.client().getStartScanIntent(requireActivity())
                .addOnSuccessListener(sender -> scanner.launch(new IntentSenderRequest.Builder(sender).build()))
                .addOnFailureListener(error -> {
                    if (hasView()) {
                        say(R.string.vault_scanner_unavailable);
                        NavHostFragment.findNavController(this).navigate(R.id.vault_camera);
                    }
                });
    }

    private void onScanned(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return; // the traveller left the scanner without scanning
        }
        GmsDocumentScanningResult scan = GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
        if (scan == null || scan.getPages() == null || scan.getPages().isEmpty()) {
            return;
        }
        List<Uri> pages = new ArrayList<>();
        for (GmsDocumentScanningResult.Page page : scan.getPages()) {
            pages.add(page.getImageUri());
        }
        CaptureViewModel capture = CaptureViewModel.of(this);
        capture.addScannedPages(requireContext(), pages, capture::analyse, error -> say(R.string.vault_import_failed));
        // The confirm screen shows "Reading the document" until the pages are read.
        NavHostFragment.findNavController(this).navigate(R.id.vault_edit_document);
    }

    private void onImported(@Nullable Uri file) {
        if (file == null) {
            return; // nothing picked
        }
        CaptureViewModel capture = CaptureViewModel.of(this);
        capture.begin(tripId, DocSource.IMPORT);
        capture.importFile(requireContext(), file, needsCrop -> {
            if (!hasView()) {
                return;
            }
            if (needsCrop) {
                NavHostFragment.findNavController(this).navigate(R.id.vault_crop);
            } else {
                capture.analyse();
                NavHostFragment.findNavController(this).navigate(R.id.vault_edit_document);
            }
        }, error -> say(error instanceof ImportReader.FileTooLargeException
                ? R.string.vault_import_too_large : R.string.vault_import_failed));
    }

    /** "From your other trips": a document already in the vault (e.g. the passport) is linked, not copied. */
    private void chooseExistingDocument() {
        if (repository == null) {
            return;
        }
        LiveData<List<TravelDocumentEntity>> saved = repository.observeDocumentsNotIn(tripId);
        saved.observe(getViewLifecycleOwner(), new Observer<List<TravelDocumentEntity>>() {
            @Override
            public void onChanged(List<TravelDocumentEntity> documents) {
                saved.removeObserver(this); // one answer is enough
                if (!hasView()) {
                    return;
                }
                if (documents == null || documents.isEmpty()) {
                    say(R.string.vault_add_existing_none);
                    return;
                }
                String[] names = new String[documents.size()];
                for (int i = 0; i < documents.size(); i++) {
                    names[i] = documents.get(i).title + " · " + getString(VaultText.typeName(documents.get(i).type));
                }
                AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.vault_add_existing_title)
                        .setItems(names, (shown, which) -> chooseLegFor(documents.get(which)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
                VaultWindow.secure(dialog);
                dialog.show();
            }
        });
    }

    private void chooseLegFor(TravelDocumentEntity document) {
        if (legs.isEmpty()) {
            link(document, null);
            return;
        }
        String[] places = new String[legs.size() + 1];
        places[0] = getString(R.string.vault_whole_trip);
        for (int i = 0; i < legs.size(); i++) {
            places[i + 1] = legs.get(i).title;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vault_add_where)
                .setItems(places, (shown, which) -> link(document, which == 0 ? null : legs.get(which - 1).id))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        VaultWindow.secure(dialog);
        dialog.show();
    }

    private void link(TravelDocumentEntity document, @Nullable String legId) {
        if (repository == null) {
            return;
        }
        repository.linkDocument(tripId, document.id, legId, new VaultRepository.Callback<Void>() {
            @Override
            public void onDone(Void nothing) {
                say(R.string.vault_add_linked);
            }

            @Override
            public void onError(@NonNull Exception error) {
                say(R.string.vault_error_generic);
            }
        });
    }

    /** Back to the vault home (once), e.g. after this trip was deleted. */
    private void leave() {
        if (leaving) {
            return;
        }
        leaving = true;
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.vault_trip) {
            navController.popBackStack();
        }
    }
}
