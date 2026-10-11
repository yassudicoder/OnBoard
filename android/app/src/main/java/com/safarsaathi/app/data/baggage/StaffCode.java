package com.safarsaathi.app.data.baggage;

import java.util.List;

/**
 * The text inside the "Show to staff" QR code (and on the claim pack): the bag problem in one short block that the
 * baggage counter can scan or read: issue, flight, tag, PIR, dates and each photo's fingerprint. Plain text, not a
 * format any airline system reads (we have no airline integration), so staff see it with any QR scanner.
 * Plain Java, unit tested.
 */
public final class StaffCode {

    private StaffCode() {
    }

    public static String text(ClaimDraft draft) {
        StringBuilder text = new StringBuilder("OnBoard bag report (made by the passenger)\n");
        text.append("Issue: ").append(issueLabel(draft.issue())).append('\n');
        line(text, "Airline", draft.airline);
        line(text, "Flight", draft.flightNo);
        line(text, "Bag tag", draft.bagTag.replace(" ", ""));
        line(text, "PIR", draft.pirRef);
        line(text, draft.issue() == BagIssue.DELAY ? "Landed" : "Received", draft.date);
        if (draft.issue() == BagIssue.DELAY) {
            line(text, "Delivered", draft.deliveredDate);
        }
        photos(text, "Before", draft.photos(EvidencePhoto.Stage.BEFORE));
        photos(text, "After", draft.photos(EvidencePhoto.Stage.AFTER));
        return text.toString().trim();
    }

    static String issueLabel(BagIssue issue) {
        switch (issue) {
            case DELAY:
                return "bag did not arrive";
            case PILFERAGE:
                return "item missing from bag";
            default:
                return "damaged bag";
        }
    }

    private static void line(StringBuilder text, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            text.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private static void photos(StringBuilder text, String label, List<EvidencePhoto> photos) {
        if (photos.isEmpty()) {
            return;
        }
        text.append(label).append(" photos (SHA-256, first 16):");
        for (EvidencePhoto photo : photos) {
            text.append(' ').append(photo.sha256 == null ? "?" : photo.sha256.substring(0, Math.min(16, photo.sha256.length())));
        }
        text.append('\n');
    }
}
