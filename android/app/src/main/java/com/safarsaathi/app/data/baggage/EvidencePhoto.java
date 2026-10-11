package com.safarsaathi.app.data.baggage;

import androidx.annotation.Nullable;

/**
 * One photo of the bag, kept on this phone (no_backup/bag-evidence/). Saved as part of a {@link ClaimDraft} (Gson),
 * so the fields are plain values.
 *
 * <p>Honest times: {@code addedAt} is when OnBoard stored the photo (the phone's clock). {@code takenAt} is the time
 * written inside the photo file by the camera (EXIF), which anyone could change, so the app always calls it "time in
 * the photo file". Neither is a server timestamp: the signed server receipt comes with Baggage Shield B1/B2.
 */
public final class EvidencePhoto {

    /** BEFORE = taken before handing the bag over; AFTER = taken on arrival. */
    public enum Stage { BEFORE, AFTER }

    /** File name inside no_backup/bag-evidence/ (a random UUID + ".jpg"). */
    public String fileName;
    public String stage;
    /** SHA-256 of the stored file, 64 lowercase hex characters. */
    public String sha256;
    /** When OnBoard stored it, ISO-8601 instant. */
    public String addedAt;
    /** The camera's own time from the file (EXIF, local time "2026-10-11T06:48"), or null when the file has none. */
    @Nullable
    public String takenAt;
    /** CAMERA (taken in OnBoard just now) or GALLERY (picked from the phone's photos). */
    public String origin;

    public EvidencePhoto() {
        // for Gson
    }

    public EvidencePhoto(String fileName, Stage stage, String sha256, String addedAt, @Nullable String takenAt,
                         String origin) {
        this.fileName = fileName;
        this.stage = stage.name();
        this.sha256 = sha256;
        this.addedAt = addedAt;
        this.takenAt = takenAt;
        this.origin = origin;
    }

    public Stage stage() {
        return Stage.AFTER.name().equals(stage) ? Stage.AFTER : Stage.BEFORE;
    }

    /** The first 12 characters of the fingerprint, enough to compare by eye on screen. */
    public String shortHash() {
        return sha256 == null ? "" : sha256.substring(0, Math.min(12, sha256.length()));
    }
}
