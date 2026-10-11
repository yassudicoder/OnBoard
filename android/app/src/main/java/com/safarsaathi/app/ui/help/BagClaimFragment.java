package com.safarsaathi.app.ui.help;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.baggage.BagIssue;
import com.safarsaathi.app.data.baggage.BaggageFacts;
import com.safarsaathi.app.data.baggage.ClaimDeadline;
import com.safarsaathi.app.data.baggage.ClaimDraft;
import com.safarsaathi.app.data.baggage.ClaimStore;
import com.safarsaathi.app.data.baggage.EvidenceHash;
import com.safarsaathi.app.data.baggage.EvidencePhoto;
import com.safarsaathi.app.data.baggage.StaffCode;
import com.safarsaathi.app.databinding.FragmentBagClaimBinding;
import com.safarsaathi.app.ui.navigate.NavigateFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One bag problem, start to finish (Baggage Shield B3, phone only): report it before leaving the baggage hall (with
 * the way to the airline's baggage services on the T2 map and a QR for staff), photos before and after with SHA-256
 * fingerprints, the details, the written-complaint deadline with a calendar reminder, the claim pack PDF, and where to
 * go if the airline doesn't help. Everything is saved on the phone as it is typed ({@link ClaimStore}).
 *
 * <p>What the phone cannot prove, it says: photo times are the phone's clock or the time inside the file, and there is
 * no server signature until B1/B2. The app never decides whether the bag is damaged or what anyone owes.
 */
public class BagClaimFragment extends Fragment {

    public static final String ARG_ISSUE = "issue";
    /** The Airline baggage services place on the Mumbai T2 map (mock-data/airports/BOM-T2/map.json). */
    static final String BAGGAGE_SERVICES_NODE = "baggage_services";

    private static final String TAG = "BagClaim";
    private static final DateTimeFormatter TYPED = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter EXIF_TIME = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FragmentBagClaimBinding binding;
    private ClaimStore store;
    private BaggageFacts facts;
    private ClaimDraft draft;
    private BagIssue issue;
    /** Which row the next photo goes into (camera or gallery). */
    private EvidencePhoto.Stage pendingStage = EvidencePhoto.Stage.AFTER;
    @Nullable
    private File pendingCameraFile;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    showMessage(getString(R.string.claim_camera_denied));
                }
            });

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                File file = pendingCameraFile;
                pendingCameraFile = null;
                if (Boolean.TRUE.equals(saved) && file != null && file.length() > 0) {
                    addPhoto(() -> new FileInputStream(file), "CAMERA", file);
                } else if (file != null && !file.delete()) {
                    file.deleteOnExit();
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    Context app = requireContext().getApplicationContext();
                    addPhoto(() -> app.getContentResolver().openInputStream(uri), "GALLERY", null);
                }
            });

    private final ActivityResultLauncher<String> savePdf =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), this::writePdf);

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public BagClaimFragment() {
        super(R.layout.fragment_bag_claim);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentBagClaimBinding.bind(view);
        issue = BagIssue.fromName(getArguments() == null ? null : getArguments().getString(ARG_ISSUE));
        store = new ClaimStore(requireContext());
        facts = loadFacts(requireContext());
        draft = store.load(issue);

        binding.claimTitle.setText(issueTitle());
        binding.claimPirAdvice.setText(facts.pir.advice);
        binding.claimPirSource.setText(getString(R.string.claim_rule_source, facts.pir.sourceNote, facts.pir.lastChecked));
        binding.claimShowWay.setOnClickListener(button -> showTheWay());
        binding.claimShowStaff.setOnClickListener(button -> showStaffQr());

        binding.claimBeforeTake.setOnClickListener(button -> takePhoto(EvidencePhoto.Stage.BEFORE));
        binding.claimBeforeChoose.setOnClickListener(button -> choosePhoto(EvidencePhoto.Stage.BEFORE));
        binding.claimAfterTake.setOnClickListener(button -> takePhoto(EvidencePhoto.Stage.AFTER));
        binding.claimAfterChoose.setOnClickListener(button -> choosePhoto(EvidencePhoto.Stage.AFTER));

        bindDetails();
        binding.claimAddReminder.setOnClickListener(button -> addReminder());
        binding.claimSavePdf.setOnClickListener(button ->
                savePdf.launch("OnBoard-bag-claim-" + LocalDate.now(AppConfig.TRIP_ZONE) + ".pdf"));
        binding.claimEmail.setOnClickListener(button -> emailAirline());
        binding.claimStartAgain.setOnClickListener(button -> confirmStartAgain());
        renderEscalation();
        renderPhotos();
        renderDeadline();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private String issueTitle() {
        if (issue == BagIssue.DELAY) {
            return getString(R.string.claim_title_delay);
        }
        return getString(issue == BagIssue.PILFERAGE ? R.string.claim_title_pilferage : R.string.claim_title_damage);
    }

    static BaggageFacts loadFacts(Context context) {
        try (Reader reader = new InputStreamReader(context.getAssets().open(BaggageFacts.ASSET), StandardCharsets.UTF_8)) {
            return BaggageFacts.parse(reader);
        } catch (IOException | RuntimeException e) {
            // Shipped inside the app and checked by BaggageFactsTest, so this means a broken build.
            throw new IllegalStateException("Cannot read " + BaggageFacts.ASSET, e);
        }
    }

    // ---- 1 · Report before leaving the hall ----

    /** Opens the Navigate tab with the route to the airline's baggage services counter. */
    private void showTheWay() {
        NavigateFragment.requestDirections(BAGGAGE_SERVICES_NODE);
        BottomNavigationView tabs = requireActivity().findViewById(R.id.bottom_nav);
        if (tabs != null) {
            tabs.setSelectedItemId(R.id.navigation_navigate);
        }
    }

    private void showStaffQr() {
        String text = StaffCode.text(draft);
        ImageView image = new ImageView(requireContext());
        int size = Math.round(getResources().getDisplayMetrics().density * 260);
        Bitmap qr = qrBitmap(text, size);
        image.setImageBitmap(qr);
        image.setContentDescription(text);
        int padding = Math.round(getResources().getDisplayMetrics().density * 16);
        image.setPadding(padding, padding, padding, padding);
        image.setBackgroundColor(Color.WHITE);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.claim_qr_title)
                .setMessage(getString(R.string.claim_qr_body))
                .setView(image)
                .setPositiveButton(R.string.claim_close, null)
                .show();
    }

    @Nullable
    static Bitmap qrBitmap(String text, int size) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.RGB_565);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }

    // ---- 2 · Photos ----

    private void takePhoto(EvidencePhoto.Stage stage) {
        pendingStage = stage;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            // The app declares CAMERA (Document Vault), so Android requires it before opening the camera app too.
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "bag-camera");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Cannot create " + dir);
            }
            pendingCameraFile = new File(dir, UUID.randomUUID() + ".jpg");
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".bagphotos", pendingCameraFile);
            takePicture.launch(uri);
        } catch (IOException | IllegalArgumentException | ActivityNotFoundException e) {
            Log.w(TAG, "Camera not available", e);
            pendingCameraFile = null;
            showMessage(getString(R.string.claim_camera_denied));
        }
    }

    private void choosePhoto(EvidencePhoto.Stage stage) {
        pendingStage = stage;
        pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private interface Source {
        InputStream open() throws IOException;
    }

    /** Copies the photo into no_backup/bag-evidence/, fingerprints the stored file, reads its own time, saves. */
    private void addPhoto(Source source, String origin, @Nullable File deleteAfter) {
        EvidencePhoto.Stage stage = pendingStage;
        showMessage(getString(R.string.claim_photo_adding));
        ServiceLocator.get().getIoExecutor().execute(() -> {
            EvidencePhoto photo = null;
            try {
                String name = UUID.randomUUID() + ".jpg";
                File target = new File(store.photoDir(), name);
                try (InputStream in = source.open(); OutputStream out = new FileOutputStream(target)) {
                    if (in == null) {
                        throw new IOException("No input stream");
                    }
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                String sha;
                try (InputStream in = new FileInputStream(target)) {
                    sha = EvidenceHash.sha256(in);
                }
                photo = new EvidencePhoto(name, stage, sha, Instant.now().toString(), exifTime(target), origin);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Could not add the photo", e);
            } finally {
                if (deleteAfter != null && !deleteAfter.delete()) {
                    deleteAfter.deleteOnExit();
                }
            }
            EvidencePhoto added = photo;
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                if (added == null) {
                    showMessage(getString(R.string.claim_photo_failed));
                    return;
                }
                draft.photos.add(added);
                store.save(draft);
                renderPhotos();
            });
        });
    }

    /** The camera's own time inside the file ("2026-10-11T06:48"), or null. Anyone can edit it: shown as "not proven". */
    @Nullable
    private static String exifTime(File file) {
        try {
            String value = new ExifInterface(file.getAbsolutePath()).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (value == null) {
                return null;
            }
            return LocalDateTime.parse(value.trim(), EXIF_TIME).toString();
        } catch (IOException | DateTimeParseException e) {
            return null;
        }
    }

    private void renderPhotos() {
        renderPhotoRow(binding.claimBeforeList, binding.claimBeforeEmpty, draft.photos(EvidencePhoto.Stage.BEFORE));
        renderPhotoRow(binding.claimAfterList, binding.claimAfterEmpty, draft.photos(EvidencePhoto.Stage.AFTER));
    }

    private void renderPhotoRow(LinearLayout row, TextView empty, List<EvidencePhoto> photos) {
        row.removeAllViews();
        empty.setVisibility(photos.isEmpty() ? View.VISIBLE : View.GONE);
        float density = getResources().getDisplayMetrics().density;
        int thumb = Math.round(132 * density);
        for (EvidencePhoto photo : photos) {
            LinearLayout cell = new LinearLayout(requireContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(thumb, LinearLayout.LayoutParams.WRAP_CONTENT);
            cellParams.setMarginEnd(Math.round(8 * density));
            cell.setLayoutParams(cellParams);

            ImageView image = new ImageView(requireContext());
            image.setLayoutParams(new LinearLayout.LayoutParams(thumb, thumb));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(decodeThumbnail(store.photoFile(photo), thumb));
            image.setContentDescription(getString(R.string.claim_photo_caption, addedTime(photo), photo.shortHash()));
            cell.addView(image);

            TextView caption = new TextView(requireContext());
            caption.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            caption.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant));
            String text = getString(R.string.claim_photo_caption, addedTime(photo), photo.shortHash());
            if (photo.takenAt != null) {
                text += "\n" + getString(R.string.claim_photo_taken, takenTime(photo));
            }
            caption.setText(text);
            cell.addView(caption);

            cell.setOnLongClickListener(v -> {
                confirmRemove(photo);
                return true;
            });
            row.addView(cell);
        }
    }

    @Nullable
    static Bitmap decodeThumbnail(File file, int size) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    static String addedTime(EvidencePhoto photo) {
        try {
            return SHORT_TIME.format(Instant.parse(photo.addedAt).atZone(AppConfig.TRIP_ZONE));
        } catch (DateTimeParseException | NullPointerException e) {
            return "?";
        }
    }

    static String takenTime(EvidencePhoto photo) {
        try {
            return SHORT_TIME.format(LocalDateTime.parse(photo.takenAt));
        } catch (DateTimeParseException | NullPointerException e) {
            return "?";
        }
    }

    private void confirmRemove(EvidencePhoto photo) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.claim_photo_remove_title)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.claim_photo_remove, (dialog, which) -> {
                    draft.photos.remove(photo);
                    store.save(draft);
                    File file = store.photoFile(photo);
                    if (file.exists() && !file.delete()) {
                        file.deleteOnExit();
                    }
                    renderPhotos();
                })
                .show();
    }

    // ---- 3 · Details ----

    /** Shows the draft's values in the fields (the watchers then save them back unchanged). */
    private void fillDetails() {
        binding.claimAirline.setText(draft.airline);
        binding.claimFlight.setText(draft.flightNo);
        binding.claimTag.setText(draft.bagTag);
        binding.claimPir.setText(draft.pirRef);
        binding.claimDate.setText(draft.date);
        binding.claimDelivered.setText(draft.deliveredDate);
        binding.claimDescription.setText(draft.description);
    }

    private void bindDetails() {
        boolean delay = issue == BagIssue.DELAY;
        binding.claimDateLayout.setHint(delay ? R.string.claim_date_landed : R.string.claim_date_received);
        binding.claimDeliveredLayout.setVisibility(delay ? View.VISIBLE : View.GONE);
        fillDetails();

        watch(binding.claimAirline, value -> draft.airline = value);
        watch(binding.claimFlight, value -> draft.flightNo = value);
        watch(binding.claimTag, value -> {
            draft.bagTag = value;
            binding.claimTagLayout.setError(ClaimDraft.isValidBagTag(value) ? null : getString(R.string.claim_bag_tag_error));
        });
        watch(binding.claimPir, value -> draft.pirRef = value);
        watch(binding.claimDate, value -> {
            draft.date = value;
            binding.claimDateLayout.setError(dateError(value));
        });
        watch(binding.claimDelivered, value -> {
            draft.deliveredDate = value;
            binding.claimDeliveredLayout.setError(dateError(value));
        });
        watch(binding.claimDescription, value -> draft.description = value);
        binding.claimDateLayout.setEndIconOnClickListener(icon ->
                binding.claimDate.setText(TYPED.format(LocalDate.now(AppConfig.TRIP_ZONE))));
        binding.claimDeliveredLayout.setEndIconOnClickListener(icon ->
                binding.claimDelivered.setText(TYPED.format(LocalDate.now(AppConfig.TRIP_ZONE))));
    }

    @Nullable
    private String dateError(String value) {
        boolean bad = !value.trim().isEmpty() && ClaimDeadline.parseDate(value) == null;
        return bad ? getString(R.string.claim_date_error) : null;
    }

    private void watch(EditText field, Consumer<String> onChange) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (binding == null) {
                    return;
                }
                onChange.accept(s.toString());
                store.save(draft);
                renderDeadline();
            }
        });
    }

    // ---- 4 · Deadline ----

    @Nullable
    private ClaimDeadline deadline() {
        return ClaimDeadline.of(draft, facts.deadlines, LocalDate.now(AppConfig.TRIP_ZONE));
    }

    private void renderDeadline() {
        ClaimDeadline deadline = deadline();
        BaggageFacts.Deadlines rules = facts.deadlines;
        binding.claimDeadlineSource.setText(getString(R.string.claim_rule_source, rules.rule, rules.lastChecked)
                + "\n" + rules.appliesTo + "\n" + rules.sourceUrl + "\n" + rules.sourceNote);
        String note = null;
        if (issue == BagIssue.PILFERAGE) {
            note = getString(R.string.claim_pilferage_note);
        } else if (issue == BagIssue.DELAY && (deadline == null || deadline.kind == ClaimDeadline.Kind.LOST_FROM)) {
            note = getString(R.string.claim_delay_note);
        }
        binding.claimDeadlineNote.setText(note);
        binding.claimDeadlineNote.setVisibility(note == null ? View.GONE : View.VISIBLE);

        if (deadline == null) {
            binding.claimDeadlineText.setText(R.string.claim_deadline_missing);
            binding.claimDeadlineDays.setVisibility(View.GONE);
            binding.claimAddReminder.setEnabled(false);
            return;
        }
        String date = LONG_DATE.format(deadline.date);
        binding.claimDeadlineText.setText(deadline.kind == ClaimDeadline.Kind.COMPLAIN_BY
                ? getString(R.string.claim_complain_by, date) : getString(R.string.claim_lost_from, date));
        binding.claimDeadlineDays.setVisibility(View.VISIBLE);
        binding.claimDeadlineDays.setText(daysText(deadline.daysLeft));
        boolean urgent = deadline.daysLeft <= 2;
        int fill = MaterialColors.getColor(binding.claimDeadlineDays, urgent
                ? androidx.appcompat.R.attr.colorError : com.google.android.material.R.attr.colorSecondaryContainer);
        int ink = MaterialColors.getColor(binding.claimDeadlineDays, urgent
                ? com.google.android.material.R.attr.colorOnError
                : com.google.android.material.R.attr.colorOnSecondaryContainer);
        binding.claimDeadlineDays.setBackgroundTintList(android.content.res.ColorStateList.valueOf(fill));
        binding.claimDeadlineDays.setTextColor(ink);
        binding.claimAddReminder.setEnabled(deadline.daysLeft >= 0);
    }

    private String daysText(long days) {
        if (days == 0) {
            return getString(R.string.claim_days_today);
        }
        if (days > 0) {
            return getResources().getQuantityString(R.plurals.claim_days_left, (int) days, (int) days);
        }
        int passed = (int) -days;
        return getResources().getQuantityString(R.plurals.claim_days_passed, passed, passed);
    }

    /** Opens the phone's calendar with an all-day event on the deadline; the traveller saves it there. */
    private void addReminder() {
        ClaimDeadline deadline = deadline();
        if (deadline == null) {
            return;
        }
        long start = deadline.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String pir = draft.pirRef.trim().isEmpty() ? "-" : draft.pirRef.trim();
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, getString(R.string.claim_reminder_title))
                .putExtra(CalendarContract.Events.DESCRIPTION, getString(R.string.claim_reminder_body, issueTitle(), pir))
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 24L * 60 * 60 * 1000);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showMessage(getString(R.string.claim_no_calendar));
        }
    }

    // ---- 5 · Claim pack ----

    private void writePdf(@Nullable Uri file) {
        if (file == null || binding == null) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        Map<String, File> photoFiles = new HashMap<>();
        for (EvidencePhoto photo : draft.photos) {
            photoFiles.put(photo.fileName, store.photoFile(photo));
        }
        ClaimPackPdf.Content content = new ClaimPackPdf.Content(issueTitle(), draft, facts, deadline(),
                StaffCode.text(draft), photoFiles, Instant.now());
        showMessage(getString(R.string.claim_pdf_working));
        ServiceLocator.get().getIoExecutor().execute(() -> {
            boolean saved;
            try (OutputStream out = app.getContentResolver().openOutputStream(file)) {
                if (out == null) {
                    throw new IOException("No output stream for " + file);
                }
                ClaimPackPdf.write(content, out);
                saved = true;
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Could not write the claim pack", e);
                saved = false;
            }
            boolean ok = saved;
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                if (!ok) {
                    showMessage(getString(R.string.claim_pdf_failed));
                    return;
                }
                Snackbar snackbar = Snackbar.make(binding.getRoot(), R.string.claim_pdf_saved, Snackbar.LENGTH_LONG)
                        .setAction(R.string.claim_pdf_open, button -> openPdf(file));
                anchor(snackbar).show();
            });
        });
    }

    private void openPdf(Uri file) {
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(file, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(view);
        } catch (ActivityNotFoundException e) {
            showMessage(getString(R.string.claim_pdf_no_viewer));
        }
    }

    // ---- 6 · Escalation ----

    private void renderEscalation() {
        LinearLayout list = binding.claimEscalationList;
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (BaggageFacts.Step step : facts.escalation) {
            View row = inflater.inflate(R.layout.item_claim_step, list, false);
            ((TextView) row.findViewById(R.id.step_title)).setText(step.title);
            ((TextView) row.findViewById(R.id.step_detail)).setText(step.detail);
            TextView source = row.findViewById(R.id.step_source);
            String sourceText = step.url.isEmpty() ? step.sourceNote
                    : step.url + (step.sourceNote.isEmpty() ? "" : "\n" + step.sourceNote);
            source.setText(sourceText);
            source.setVisibility(sourceText.isEmpty() ? View.GONE : View.VISIBLE);
            if (!step.url.isEmpty()) {
                row.setOnClickListener(v -> openLink(step.url));
            }
            list.addView(row);
        }
    }

    private void emailAirline() {
        String flight = draft.flightNo.trim().isEmpty() ? "-" : draft.flightNo.trim();
        String subject = getString(R.string.claim_email_subject, issueTitle(), flight);
        String body = "Dear Customer Relations team,\n\nI am writing about my checked bag (" + issueTitle().toLowerCase(Locale.ENGLISH)
                + ").\n\n" + StaffCode.text(draft) + "\n\n" + draft.description.trim()
                + "\n\nPlease find my claim pack (PDF) attached. Please confirm receipt and tell me the next steps."
                + "\n\nRegards";
        Uri uri = Uri.parse("mailto:?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body));
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, uri));
        } catch (ActivityNotFoundException e) {
            showMessage(getString(R.string.claim_no_mail));
        }
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            showMessage(getString(R.string.claim_open_link_failed));
        }
    }

    private void confirmStartAgain() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.claim_start_again_title)
                .setMessage(R.string.claim_start_again_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.claim_start_again, (dialog, which) -> {
                    store.clear(issue);
                    draft = store.load(issue);
                    fillDetails();
                    renderPhotos();
                    renderDeadline();
                })
                .show();
    }

    // ---- Messages ----

    private void showMessage(String text) {
        if (binding != null) {
            anchor(Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG)).show();
        }
    }

    private Snackbar anchor(Snackbar snackbar) {
        View tabs = requireActivity().findViewById(R.id.bottom_nav);
        if (tabs != null && tabs.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(tabs);
        }
        return snackbar;
    }
}
