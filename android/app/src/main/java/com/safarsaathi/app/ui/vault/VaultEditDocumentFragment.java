package com.safarsaathi.app.ui.vault;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputLayout;
import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.DocType;
import com.safarsaathi.app.data.vault.Entries;
import com.safarsaathi.app.data.vault.VaultRepository;
import com.safarsaathi.app.data.vault.VaultUnavailableException;
import com.safarsaathi.app.data.vault.capture.CountryCodes;
import com.safarsaathi.app.data.vault.capture.DocumentDraft;
import com.safarsaathi.app.data.vault.capture.DraftFields;
import com.safarsaathi.app.data.vault.capture.ReadNote;
import com.safarsaathi.app.data.vault.db.TravellerEntity;
import com.safarsaathi.app.data.vault.db.TripLegEntity;
import com.safarsaathi.app.data.wallet.IdDocuments;
import com.safarsaathi.app.databinding.FragmentVaultEditDocumentBinding;
import com.safarsaathi.app.databinding.ItemVaultNoteBinding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The confirm screen (layout: fragment_vault_edit_document.xml): every field of the document being added, ready to
 * correct. Pre-filled from the MRZ or a boarding pass barcode when the phone could read one ({@link CaptureViewModel}
 * holds the draft and the pages), empty for "Type it in". The traveller also picks the part of the trip and whose
 * document it is. When anything was filled in automatically, saving needs an explicit "I compared these details"
 * tick: the app never stores read-out fields the traveller has not looked at.
 */
public class VaultEditDocumentFragment extends VaultScreen {

    private static final Pattern CODE_IN_BRACKETS = Pattern.compile(".*\\(([A-Za-z]{2})\\)\\s*$");
    private static final String KEY_NEW = "new";
    private static final String KEY_NOBODY = "none";
    private static final String KEY_TRAVELLER = "t:";

    @Nullable
    private FragmentVaultEditDocumentBinding binding;
    private CaptureViewModel capture;
    @Nullable
    private VaultRepository repository;
    private List<TripLegEntity> legs = Collections.emptyList();
    private List<TravellerEntity> tripTravellers = Collections.emptyList();
    private List<TravellerEntity> allTravellers = Collections.emptyList();
    private final List<String> travellerKeys = new ArrayList<>();
    /** The title the screen suggested last; replaced when the type changes, unless the traveller edited it. */
    private String autoTitle = "";
    private boolean saving;
    /** Saved or cancelled: the screen is closing, so an emptied capture is expected, not an error. */
    private boolean closing;

    public VaultEditDocumentFragment() {
        super(R.layout.fragment_vault_edit_document);
    }

    @Override
    protected void onContentCreated(@NonNull View content, @Nullable Bundle savedInstanceState) {
        binding = FragmentVaultEditDocumentBinding.bind(content);
        ViewCompat.setAccessibilityHeading(binding.editHeading, true);
        capture = CaptureViewModel.of(this);
        try {
            repository = vault().repository();
        } catch (VaultUnavailableException e) {
            leave();
            return;
        }
        setUpDropdowns();
        binding.editSave.setOnClickListener(button -> save());
        binding.editCancel.setOnClickListener(button -> cancel());
        // A number read from an MRZ is re-checked against its printed check digit while the traveller corrects it.
        binding.editNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                renderNumberCheck();
            }
        });
        binding.editConfirm.setOnCheckedChangeListener((box, checked) -> {
            if (checked && binding != null) {
                binding.editConfirmError.setVisibility(View.GONE);
            }
        });
        // System back = cancel: the half-added pages are forgotten.
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        cancel();
                    }
                });

        String tripId = capture.tripId();
        repository.observeLegs(tripId).observe(getViewLifecycleOwner(), newLegs -> {
            legs = newLegs == null ? Collections.emptyList() : newLegs;
            renderLegs();
        });
        repository.observeTravellersOf(tripId).observe(getViewLifecycleOwner(), people -> {
            tripTravellers = people == null ? Collections.emptyList() : people;
            renderTravellers();
        });
        repository.observeTravellers().observe(getViewLifecycleOwner(), people -> {
            allTravellers = people == null ? Collections.emptyList() : people;
            renderTravellers();
        });
        capture.stage().observe(getViewLifecycleOwner(), this::renderStage);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ============================================================================================
    // Showing the draft
    // ============================================================================================

    private void renderStage(CaptureViewModel.Stage stage) {
        if (binding == null) {
            return;
        }
        DocumentDraft draft = capture.draft();
        boolean ready = stage == CaptureViewModel.Stage.READY && draft != null;
        binding.editReading.setVisibility(stage == CaptureViewModel.Stage.WORKING ? View.VISIBLE : View.GONE);
        binding.editForm.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (stage == CaptureViewModel.Stage.IDLE && draft == null && !saving && !closing) {
            // Nothing to confirm (e.g. Android closed the app while the pages were only in memory): start again.
            say(R.string.vault_edit_start_again);
            leave();
            return;
        }
        if (!ready) {
            return;
        }
        binding.editIntro.setText(draft.readAutomatically ? R.string.vault_edit_intro_read
                : R.string.vault_edit_intro_manual);
        renderNotes(draft);
        int pages = capture.pageCount();
        binding.editPages.setText(pages == 0 ? getString(R.string.vault_edit_no_pages)
                : getResources().getQuantityString(R.plurals.vault_edit_pages, pages, pages));
        binding.editConfirm.setVisibility(draft.readAutomatically ? View.VISIBLE : View.GONE);
        if (!capture.formFilled) {
            fillForm(draft);
            capture.formFilled = true;
        }
        applyType(draft.type);
        renderLegs();
        renderTravellers();
        renderNumberCheck();
    }

    /**
     * Under the number: "does not match the check digit" (an error) or "matches the check digit printed on the
     * document" (a confirmation), or the normal hint when there is no check digit to compare with.
     */
    private void renderNumberCheck() {
        DocumentDraft draft = capture.draft();
        if (binding == null || draft == null) {
            return;
        }
        Boolean matches = draft.numberMatchesCheckDigit(text(binding.editNumber.getText()));
        if (matches == null) {
            binding.editNumberLayout.setError(null);
            binding.editNumberLayout.setHelperText(getString(R.string.vault_edit_number_helper));
        } else if (matches) {
            binding.editNumberLayout.setError(null);
            binding.editNumberLayout.setHelperText(getString(R.string.vault_edit_number_check_ok));
        } else {
            binding.editNumberLayout.setError(getString(R.string.vault_edit_number_check_failed));
        }
    }

    private void renderNotes(DocumentDraft draft) {
        if (binding == null) {
            return;
        }
        binding.editNotes.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (ReadNote note : draft.notes) {
            String text = noteText(note);
            if (text == null) {
                continue;
            }
            ItemVaultNoteBinding row = ItemVaultNoteBinding.inflate(inflater, binding.editNotes, true);
            row.noteIcon.setImageResource(note.isWarning() ? R.drawable.ic_warning_24 : R.drawable.ic_check_circle_24);
            row.noteText.setText(text);
        }
    }

    @Nullable
    private String noteText(ReadNote note) {
        switch (note.kind) {
            case MRZ_READ: return getString(R.string.vault_note_mrz_read);
            case BARCODE_READ: return getString(R.string.vault_note_barcode_read);
            case NOTHING_READ: return getString(R.string.vault_note_nothing_read);
            case COUNTRY_NOT_RECOGNISED: return getString(R.string.vault_note_country_unknown, note.detail);
            case CHECK_COVERED_COUNTRIES: return getString(R.string.vault_note_check_covers);
            case MRZ_CHECK_FAILED:
                if (note.field == null) {
                    return getString(R.string.vault_note_check_failed_whole);
                }
                switch (note.field) {
                    case NUMBER: return getString(R.string.vault_note_check_failed_number);
                    case BIRTH_DATE: return getString(R.string.vault_note_check_failed_birth);
                    case EXPIRY_DATE: return getString(R.string.vault_note_check_failed_expiry);
                    case PERSONAL_NUMBER: return getString(R.string.vault_note_check_failed_personal);
                    case WHOLE_ZONE:
                    default: return getString(R.string.vault_note_check_failed_whole);
                }
            default:
                return null;
        }
    }

    /** Copies the draft into the form (once; after a rotation the fields keep what was typed). */
    private void fillForm(DocumentDraft draft) {
        if (binding == null) {
            return;
        }
        if (draft.type != null) {
            binding.editType.setText(getString(VaultText.typeName(draft.type)), false);
        }
        binding.editTitle.setText(draft.title);
        binding.editHolder.setText(draft.holderName);
        binding.editNumber.setText(draft.number);
        binding.editCountry.setText(draft.issuingCountry == null ? null : countryLabel(draft.issuingCountry), false);
        binding.editIssue.setText(formatDate(draft.issueDate));
        binding.editExpiry.setText(formatDate(draft.expiryDate));
        binding.editFrom.setText(formatDate(draft.validFrom));
        binding.editTo.setText(formatDate(draft.validTo));
        binding.editCovers.setText(draft.coversCountries == null ? null : draft.coversCountries.replace(",", ", "));
        if (draft.entries != null) {
            binding.editEntries.setText(getString(entriesName(draft.entries)), false);
        }
        binding.editFlight.setText(draft.flightNo);
        binding.editBooking.setText(draft.bookingRef);
        binding.editPlace.setText(draft.placeName);
        binding.editAddress.setText(draft.address);
        if (draft.title == null && draft.type != null) {
            autoTitle = defaultTitle(draft.type);
            binding.editTitle.setText(autoTitle);
        }
    }

    // ============================================================================================
    // Dropdowns
    // ============================================================================================

    private void setUpDropdowns() {
        if (binding == null) {
            return;
        }
        Context context = requireContext();
        List<String> types = new ArrayList<>();
        for (DocType type : DocType.values()) {
            types.add(getString(VaultText.typeName(type)));
        }
        binding.editType.setAdapter(new AllItemsAdapter(context, types));
        binding.editType.setOnItemClickListener((parent, view, position, id) -> {
            DocType type = DocType.values()[position];
            DocumentDraft draft = capture.draft();
            if (draft != null) {
                draft.type = type;
            }
            binding.editTypeLayout.setError(null);
            String title = text(binding.editTitle.getText());
            if (title.isEmpty() || title.equals(autoTitle)) {
                autoTitle = defaultTitle(type);
                binding.editTitle.setText(autoTitle);
            }
            applyType(type);
        });

        List<String> entries = new ArrayList<>();
        for (Entries value : Entries.values()) {
            entries.add(getString(entriesName(value)));
        }
        entries.add(getString(R.string.vault_entries_unknown));
        binding.editEntries.setAdapter(new AllItemsAdapter(context, entries));

        // Countries: every country the phone knows, by name, with its code ("India (IN)"); typing filters the list.
        List<String> countries = new ArrayList<>();
        for (String code : Locale.getISOCountries()) {
            countries.add(countryLabel(code));
        }
        Collections.sort(countries);
        binding.editCountry.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, countries));

        binding.editLeg.setOnItemClickListener((parent, view, position, id) ->
                capture.chosenLegId = position == 0 ? null : legs.get(position - 1).id);
        binding.editTraveller.setOnItemClickListener((parent, view, position, id) ->
                capture.chosenTravellerKey = travellerKeys.get(position));
    }

    /** Shows only the fields this kind of document has, with labels that fit it. */
    private void applyType(@Nullable DocType type) {
        if (binding == null) {
            return;
        }
        Set<DraftFields.Field> shown = type == null ? Collections.emptySet() : DraftFields.fieldsFor(type);
        show(binding.editHolderLayout, shown.contains(DraftFields.Field.HOLDER));
        show(binding.editNumberLayout, shown.contains(DraftFields.Field.NUMBER));
        show(binding.editCountryLayout, shown.contains(DraftFields.Field.ISSUING_COUNTRY));
        show(binding.editIssueLayout, shown.contains(DraftFields.Field.ISSUE_DATE));
        show(binding.editExpiryLayout, shown.contains(DraftFields.Field.EXPIRY_DATE));
        show(binding.editFromLayout, shown.contains(DraftFields.Field.VALID_FROM));
        show(binding.editToLayout, shown.contains(DraftFields.Field.VALID_TO));
        show(binding.editCoversLayout, shown.contains(DraftFields.Field.COVERS_COUNTRIES));
        show(binding.editEntriesLayout, shown.contains(DraftFields.Field.ENTRIES));
        show(binding.editFlightLayout, shown.contains(DraftFields.Field.FLIGHT));
        show(binding.editBookingLayout, shown.contains(DraftFields.Field.BOOKING_REF));
        show(binding.editPlaceLayout, shown.contains(DraftFields.Field.PLACE_NAME));
        show(binding.editAddressLayout, shown.contains(DraftFields.Field.ADDRESS));
        if (type == null) {
            return;
        }
        binding.editNumberLayout.setHint(numberLabel(type));
        int[] dateLabels = dateLabels(type);
        binding.editFromLayout.setHint(dateLabels[0]);
        binding.editToLayout.setHint(dateLabels[1]);
    }

    private void renderLegs() {
        if (binding == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.vault_whole_trip));
        for (TripLegEntity leg : legs) {
            names.add(leg.title);
        }
        binding.editLeg.setAdapter(new AllItemsAdapter(requireContext(), names));
        if (!capture.legDefaultApplied && !legs.isEmpty()) {
            capture.chosenLegId = matchingLeg();
            capture.legDefaultApplied = true;
        }
        int index = 0;
        for (int i = 0; i < legs.size(); i++) {
            if (legs.get(i).id.equals(capture.chosenLegId)) {
                index = i + 1;
            }
        }
        binding.editLeg.setText(names.get(index), false);
        show(binding.editLegLayout, !legs.isEmpty());
    }

    /** A boarding pass or ticket "DEL → KGL" goes under the flight leg from DEL to KGL, if the trip has one. */
    @Nullable
    private String matchingLeg() {
        DocumentDraft draft = capture.draft();
        if (draft == null || draft.titleHint == null || !draft.titleHint.contains("→")) {
            return null;
        }
        String[] route = draft.titleHint.split("→");
        for (TripLegEntity leg : legs) {
            if (route[0].trim().equals(leg.fromCode) && route[1].trim().equals(leg.toCode)) {
                return leg.id;
            }
        }
        return null;
    }

    private void renderTravellers() {
        if (binding == null) {
            return;
        }
        List<TravellerEntity> people = new ArrayList<>(tripTravellers);
        for (TravellerEntity other : allTravellers) {
            if (!containsId(people, other.id)) {
                people.add(other);
            }
        }
        List<String> labels = new ArrayList<>();
        travellerKeys.clear();
        for (TravellerEntity person : people) {
            labels.add(person.name);
            travellerKeys.add(KEY_TRAVELLER + person.id);
        }
        labels.add(getString(R.string.vault_edit_traveller_new));
        travellerKeys.add(KEY_NEW);
        labels.add(getString(R.string.vault_edit_traveller_nobody));
        travellerKeys.add(KEY_NOBODY);
        binding.editTraveller.setAdapter(new AllItemsAdapter(requireContext(), labels));

        if (capture.chosenTravellerKey == null || !travellerKeys.contains(capture.chosenTravellerKey)) {
            capture.chosenTravellerKey = defaultTraveller(people);
        }
        binding.editTraveller.setText(labels.get(travellerKeys.indexOf(capture.chosenTravellerKey)), false);
    }

    /** The person whose name is on the document; otherwise a new person for an identity document, else nobody. */
    private String defaultTraveller(List<TravellerEntity> people) {
        DocumentDraft draft = capture.draft();
        String holder = draft == null ? null : draft.holderName;
        if (holder != null) {
            for (TravellerEntity person : people) {
                if (sameName(person.name, holder)) {
                    return KEY_TRAVELLER + person.id;
                }
            }
        }
        if (draft != null && (draft.type == DocType.PASSPORT || draft.type == DocType.ID_CARD)) {
            return KEY_NEW;
        }
        if (tripTravellers.size() == 1) {
            return KEY_TRAVELLER + tripTravellers.get(0).id;
        }
        return holder != null ? KEY_NEW : KEY_NOBODY;
    }

    // ============================================================================================
    // Saving
    // ============================================================================================

    private void save() {
        DocumentDraft read = capture.draft();
        if (binding == null || repository == null || read == null || saving) {
            return;
        }
        DocumentDraft draft = new DocumentDraft();
        draft.source = read.source;
        draft.readAutomatically = read.readAutomatically;
        draft.type = read.type;
        draft.title = text(binding.editTitle.getText());
        draft.holderName = emptyToNull(text(binding.editHolder.getText()).toUpperCase(Locale.ROOT));
        draft.number = emptyToNull(text(binding.editNumber.getText()));
        draft.flightNo = emptyToNull(text(binding.editFlight.getText()).toUpperCase(Locale.ROOT));
        draft.bookingRef = emptyToNull(text(binding.editBooking.getText()).toUpperCase(Locale.ROOT));
        draft.coversCountries = emptyToNull(text(binding.editCovers.getText()));
        draft.placeName = emptyToNull(text(binding.editPlace.getText()));
        draft.address = emptyToNull(text(binding.editAddress.getText()));
        draft.entries = chosenEntries();

        boolean formOk = true;
        String countryText = text(binding.editCountry.getText());
        draft.issuingCountry = countryText.isEmpty() ? null : parseCountry(countryText);
        boolean countryBad = !countryText.isEmpty() && draft.issuingCountry == null
                && binding.editCountryLayout.getVisibility() == View.VISIBLE;
        binding.editCountryLayout.setError(countryBad ? getString(R.string.vault_edit_error_country) : null);
        formOk &= !countryBad;
        draft.issueDate = date(binding.editIssueLayout, text(binding.editIssue.getText()));
        draft.expiryDate = date(binding.editExpiryLayout, text(binding.editExpiry.getText()));
        draft.validFrom = date(binding.editFromLayout, text(binding.editFrom.getText()));
        draft.validTo = date(binding.editToLayout, text(binding.editTo.getText()));
        formOk &= binding.editIssueLayout.getError() == null && binding.editExpiryLayout.getError() == null
                && binding.editFromLayout.getError() == null && binding.editToLayout.getError() == null;

        DraftFields.keepOnlyFieldsOf(draft);
        Set<DocumentDraft.Problem> problems = draft.problems();
        binding.editTypeLayout.setError(problems.contains(DocumentDraft.Problem.TYPE_MISSING)
                ? getString(R.string.vault_edit_error_type) : null);
        binding.editTitleLayout.setError(problems.contains(DocumentDraft.Problem.TITLE_MISSING)
                ? getString(R.string.vault_edit_error_title) : null);
        if (problems.contains(DocumentDraft.Problem.COVERED_COUNTRY_NOT_A_COUNTRY)) {
            binding.editCoversLayout.setError(getString(R.string.vault_edit_error_covers));
        } else {
            binding.editCoversLayout.setError(null);
        }
        if (problems.contains(DocumentDraft.Problem.EXPIRY_BEFORE_ISSUE)) {
            binding.editExpiryLayout.setError(getString(R.string.vault_edit_error_expiry));
        }
        if (problems.contains(DocumentDraft.Problem.VALID_TO_BEFORE_VALID_FROM)) {
            binding.editToLayout.setError(getString(R.string.vault_edit_error_to));
        }
        boolean confirmed = !draft.readAutomatically || binding.editConfirm.isChecked();
        binding.editConfirmError.setVisibility(confirmed ? View.GONE : View.VISIBLE);
        if (!formOk || !problems.isEmpty() || !confirmed) {
            return;
        }

        saving = true;
        binding.editSave.setEnabled(false);
        repository.addDocument(capture.tripId(), capture.chosenLegId, draft, travellerChoice(draft, read),
                capture.pages(), new VaultRepository.Callback<String>() {
                    @Override
                    public void onDone(String documentId) {
                        closing = true;
                        capture.clear();
                        say(R.string.vault_edit_saved);
                        if (hasView()) {
                            backToFolder();
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        saving = false;
                        if (binding != null) {
                            binding.editSave.setEnabled(true);
                            say(R.string.vault_error_generic);
                        }
                    }
                });
    }

    private VaultRepository.TravellerChoice travellerChoice(DocumentDraft draft, DocumentDraft read) {
        String key = capture.chosenTravellerKey == null ? KEY_NOBODY : capture.chosenTravellerKey;
        if (key.startsWith(KEY_TRAVELLER)) {
            return VaultRepository.TravellerChoice.existing(key.substring(KEY_TRAVELLER.length()));
        }
        if (KEY_NEW.equals(key) && draft.holderName != null) {
            // Nationality: from the MRZ when it was read; for a typed-in passport, the country that issued it.
            String nationality = read.nationality != null ? read.nationality
                    : draft.type == DocType.PASSPORT ? draft.issuingCountry : null;
            return VaultRepository.TravellerChoice.newTraveller(draft.holderName, nationality, read.birthDate);
        }
        return VaultRepository.TravellerChoice.nobody();
    }

    private void cancel() {
        closing = true;
        capture.clear();
        backToFolder();
    }

    private void backToFolder() {
        NavHostFragment.findNavController(this).popBackStack(R.id.vault_trip, false);
    }

    private void leave() {
        closing = true;
        NavHostFragment.findNavController(this).popBackStack(R.id.vault_trip, false);
    }

    // ============================================================================================
    // Helpers
    // ============================================================================================

    private String defaultTitle(DocType type) {
        String typeName = getString(VaultText.typeName(type));
        DocumentDraft draft = capture.draft();
        if (draft == null) {
            return typeName;
        }
        if ((type == DocType.BOARDING_PASS || type == DocType.TICKET) && draft.titleHint != null
                && draft.titleHint.contains("→")) {
            return getString(R.string.vault_edit_title_route, typeName, draft.titleHint);
        }
        String country = binding == null ? draft.issuingCountry : parseCountry(text(binding.editCountry.getText()));
        if (country == null) {
            country = draft.issuingCountry;
        }
        return country == null ? typeName
                : getString(R.string.vault_edit_title_country, typeName, VaultText.countryName(country));
    }

    @StringRes
    private static int numberLabel(DocType type) {
        switch (type) {
            case PASSPORT: return R.string.vault_edit_number_passport;
            case VISA: case E_VISA: case TRANSIT_VISA: return R.string.vault_edit_number_visa;
            case ID_CARD: return R.string.vault_edit_number_id;
            case TICKET: return R.string.vault_edit_number_ticket;
            case INSURANCE: return R.string.vault_edit_number_insurance;
            case VACCINATION_CERT: return R.string.vault_edit_number_certificate;
            case ENTRY_FORM: return R.string.vault_edit_number_reference;
            default: return R.string.vault_edit_number_other;
        }
    }

    /** Labels for the first/last day fields: a hotel has check-in/out, an insurance a cover period, etc. */
    private static int[] dateLabels(DocType type) {
        switch (type) {
            case HOTEL:
                return new int[] {R.string.vault_edit_from_hotel, R.string.vault_edit_to_hotel};
            case INSURANCE:
                return new int[] {R.string.vault_edit_from_insurance, R.string.vault_edit_to_insurance};
            case TICKET:
            case BOARDING_PASS:
                return new int[] {R.string.vault_edit_from_flight, R.string.vault_edit_to_valid};
            default:
                return new int[] {R.string.vault_edit_from_valid, R.string.vault_edit_to_valid};
        }
    }

    @StringRes
    private static int entriesName(Entries entries) {
        switch (entries) {
            case SINGLE: return R.string.vault_entries_single;
            case DOUBLE: return R.string.vault_entries_double;
            case MULTIPLE:
            default: return R.string.vault_entries_multiple;
        }
    }

    @Nullable
    private Entries chosenEntries() {
        if (binding == null) {
            return null;
        }
        String chosen = text(binding.editEntries.getText());
        for (Entries value : Entries.values()) {
            if (getString(entriesName(value)).equals(chosen)) {
                return value;
            }
        }
        return null; // "Not shown" or empty
    }

    /** "India (IN)", the country's name in the phone's language. */
    private static String countryLabel(String code) {
        return VaultText.countryName(code) + " (" + code + ")";
    }

    /** "India (IN)", "IN" or "India" to "IN"; null when it is not a country. */
    @Nullable
    private static String parseCountry(String text) {
        String trimmed = text.trim();
        Matcher bracket = CODE_IN_BRACKETS.matcher(trimmed);
        String code = bracket.matches() ? bracket.group(1) : trimmed;
        if (code != null && code.length() == 2 && CountryCodes.isCountry(code)) {
            return code.toUpperCase(Locale.ROOT);
        }
        for (String iso : Locale.getISOCountries()) {
            if (VaultText.countryName(iso).equalsIgnoreCase(trimmed)) {
                return iso;
            }
        }
        return null;
    }

    /** A DD/MM/YYYY date from a visible field; shows an error and answers null when it is not a real date. */
    @Nullable
    private LocalDate date(TextInputLayout layout, String text) {
        if (layout.getVisibility() != View.VISIBLE || text.isEmpty()) {
            layout.setError(null);
            return null;
        }
        LocalDate date = IdDocuments.parseDate(text);
        layout.setError(date == null ? getString(R.string.vault_new_trip_date_error) : null);
        return date;
    }

    private static String formatDate(@Nullable LocalDate date) {
        return date == null ? null : IdDocuments.INPUT_DATE.format(date);
    }

    /** Names match when they are the same words in any case and spacing ("Paramvir  Singh" = "PARAMVIR SINGH"). */
    private static boolean sameName(String a, String b) {
        return a.trim().replaceAll("\\s+", " ").equalsIgnoreCase(b.trim().replaceAll("\\s+", " "));
    }

    private static boolean containsId(List<TravellerEntity> people, String id) {
        for (TravellerEntity person : people) {
            if (person.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Nullable
    private static String emptyToNull(String text) {
        return text.isEmpty() ? null : text;
    }

    /**
     * A dropdown list that always shows all its choices. (A normal ArrayAdapter filters by the text already in the
     * field, so after a choice or a screen rotation it would list only that one choice.)
     */
    private static final class AllItemsAdapter extends ArrayAdapter<String> {
        private final List<String> items;

        AllItemsAdapter(Context context, List<String> items) {
            super(context, android.R.layout.simple_list_item_1, new ArrayList<>(items));
            this.items = new ArrayList<>(items);
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = items;
                    results.count = items.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }
}
