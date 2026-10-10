package com.safarsaathi.app.ui.vault;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.DocumentNumbers;
import com.safarsaathi.app.data.vault.VaultRepository;
import com.safarsaathi.app.data.vault.VaultUnavailableException;
import com.safarsaathi.app.data.vault.crypto.EncryptedFileStore;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.VaultFileEntity;
import com.safarsaathi.app.databinding.FragmentVaultDocumentBinding;
import com.safarsaathi.app.databinding.ItemVaultFieldBinding;
import com.safarsaathi.app.databinding.ItemVaultPageBinding;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One document (layout: fragment_vault_document.xml): its key details, with the number hidden except for its last
 * 2 characters, and its pages. Each page is decrypted in memory only, checked against its SHA-256 fingerprint, and
 * shown; nothing decrypted is ever written to the phone's storage.
 *
 * <p>The later "viewer" phase adds zoom, page swipe, present mode, PDFs and showing the full number after a
 * fingerprint check.
 */
public class VaultDocumentFragment extends VaultScreen {

    /** Pages are shrunk to about this width before showing, so big scans do not use too much memory. */
    private static final int MAX_PAGE_WIDTH_PX = 1200;

    @Nullable
    private FragmentVaultDocumentBinding binding;
    @Nullable
    private VaultRepository repository;
    private String documentId = "";
    @Nullable
    private TravelDocumentEntity document;
    private int tripCount;
    private boolean leaving;

    public VaultDocumentFragment() {
        super(R.layout.fragment_vault_document);
    }

    @Override
    protected void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState) {
        binding = FragmentVaultDocumentBinding.bind(content);
        ViewCompat.setAccessibilityHeading(binding.docTitle, true);
        ViewCompat.setAccessibilityHeading(binding.docPagesTitle, true);
        documentId = Objects.requireNonNull(requireArguments().getString(VaultTripFragment.ARG_DOCUMENT_ID));
        try {
            repository = vault().repository();
        } catch (VaultUnavailableException e) {
            leave();
            return;
        }
        binding.docDelete.setOnClickListener(button -> confirmDelete());

        repository.observeDocument(documentId).observe(getViewLifecycleOwner(), newDocument -> {
            document = newDocument;
            if (newDocument == null) {
                leave(); // deleted
                return;
            }
            renderDocument();
        });
        repository.observeTripCountOf(documentId).observe(getViewLifecycleOwner(), count -> {
            tripCount = count == null ? 0 : count;
            renderTypeLine();
        });
        repository.observeFiles(documentId).observe(getViewLifecycleOwner(),
                files -> renderPages(files == null ? Collections.emptyList() : files));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderDocument() {
        if (binding == null || document == null || repository == null) {
            return;
        }
        binding.docTitle.setText(document.title);
        binding.docSample.setVisibility(document.sample ? View.VISIBLE : View.GONE);
        binding.docSampleNote.setVisibility(document.sample ? View.VISIBLE : View.GONE);
        renderTypeLine();
        binding.docConfirmed.setText(document.fieldsConfirmed ? R.string.vault_doc_confirmed
                : R.string.vault_doc_unconfirmed);

        binding.docFields.removeAllViews();
        addField(R.string.vault_doc_holder, document.holderName);
        if (document.numberEncrypted != null) {
            ItemVaultFieldBinding number = addField(R.string.vault_doc_number, "");
            TravelDocumentEntity shown = document;
            repository.readNumber(shown, new VaultRepository.Callback<String>() {
                @Override
                public void onDone(String plain) {
                    if (binding == null || plain == null) {
                        return;
                    }
                    number.fieldValue.setText(DocumentNumbers.mask(plain));
                    // Screen readers would otherwise read out "bullet, bullet, ...".
                    String end = DocumentNumbers.visibleEnd(plain);
                    number.fieldValue.setContentDescription(end.isEmpty()
                            ? getString(R.string.vault_doc_number_hidden)
                            : getString(R.string.vault_doc_number_hidden_end, spaced(end)));
                }

                @Override
                public void onError(@NonNull Exception error) {
                    if (binding != null) {
                        number.fieldValue.setText(R.string.vault_doc_file_unreadable);
                    }
                }
            });
        }
        if (document.issuingCountry != null) {
            addField(R.string.vault_doc_issuing, VaultText.countryName(document.issuingCountry));
        }
        if (document.coversCountries != null) {
            addField(R.string.vault_doc_countries, VaultText.countryNames(document.coversCountries));
        }
        if (document.issueDate != null) {
            addField(R.string.vault_doc_issued, VaultText.date(document.issueDate));
        }
        if (document.expiryDate != null) {
            addField(R.string.vault_doc_expires, VaultText.date(document.expiryDate));
        }
        if (document.flightNo != null) {
            String day = document.validFrom == null ? "" : VaultText.date(document.validFrom);
            addField(R.string.vault_doc_flight, day.isEmpty() ? document.flightNo
                    : getString(R.string.vault_doc_flight_value, document.flightNo, day));
        } else if (document.validFrom != null || document.validTo != null) {
            addField(R.string.vault_doc_valid, VaultText.dateRange(requireContext(), document.validFrom,
                    document.validTo));
        }
        addField(R.string.vault_doc_booking, document.bookingRef);
    }

    private void renderTypeLine() {
        if (binding == null || document == null) {
            return;
        }
        String type = getString(VaultText.typeName(document.type));
        binding.docType.setText(tripCount == 0 ? type : type + " · "
                + getResources().getQuantityString(R.plurals.vault_doc_used_in, tripCount, tripCount));
    }

    /** Adds one label/value row; does nothing for a missing value (never shows an empty or made-up value). */
    @Nullable
    private ItemVaultFieldBinding addField(@StringRes int label, @Nullable String value) {
        if (binding == null || value == null) {
            return null;
        }
        ItemVaultFieldBinding field = ItemVaultFieldBinding.inflate(getLayoutInflater(), binding.docFields, true);
        field.fieldLabel.setText(label);
        field.fieldValue.setText(value);
        return field;
    }

    private void renderPages(List<VaultFileEntity> files) {
        if (binding == null || repository == null) {
            return;
        }
        binding.docPages.removeAllViews();
        binding.docPagesTitle.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < files.size(); i++) {
            VaultFileEntity file = files.get(i);
            ItemVaultPageBinding page = ItemVaultPageBinding.inflate(inflater, binding.docPages, true);
            page.pageLabel.setText(getString(R.string.vault_doc_page, i + 1, files.size()));
            page.pageImage.setContentDescription(getString(R.string.vault_doc_page, i + 1, files.size()));
            if (!file.mimeType.startsWith("image/")) {
                page.pageImage.setVisibility(View.GONE);
                page.pageCheck.setText(R.string.vault_doc_file_pdf);
                continue;
            }
            repository.readFile(file, new VaultRepository.Callback<byte[]>() {
                @Override
                public void onDone(byte[] bytes) {
                    if (binding == null) {
                        return;
                    }
                    Bitmap bitmap = decode(bytes);
                    if (bitmap == null) {
                        page.pageCheck.setText(R.string.vault_doc_file_unreadable);
                        return;
                    }
                    page.pageImage.setImageBitmap(bitmap);
                    page.pageCheck.setText(R.string.vault_doc_file_ok);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    if (binding == null) {
                        return;
                    }
                    page.pageImage.setVisibility(View.GONE);
                    page.pageCheck.setText(error instanceof EncryptedFileStore.IntegrityException
                            ? R.string.vault_doc_file_changed : R.string.vault_doc_file_unreadable);
                }
            });
        }
    }

    /** Decodes a JPEG/PNG, shrunk by powers of 2 until it is at most about MAX_PAGE_WIDTH_PX wide. */
    @Nullable
    private static Bitmap decode(byte[] bytes) {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, size);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (size.outWidth / (options.inSampleSize * 2) >= MAX_PAGE_WIDTH_PX) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private void confirmDelete() {
        if (repository == null) {
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vault_delete_document_title)
                .setMessage(R.string.vault_delete_document_body)
                .setPositiveButton(R.string.vault_delete, (shown, which) ->
                        repository.deleteDocument(documentId, new VaultRepository.Callback<Void>() {
                            @Override
                            public void onDone(Void nothing) {
                                say(R.string.vault_document_deleted); // the document observer then leaves
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

    /** "01" becomes "0 1", so a screen reader says the characters one by one. */
    private static String spaced(String text) {
        return String.join(" ", text.split(""));
    }

    private void leave() {
        if (leaving) {
            return;
        }
        leaving = true;
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.vault_document) {
            navController.popBackStack();
        }
    }
}
