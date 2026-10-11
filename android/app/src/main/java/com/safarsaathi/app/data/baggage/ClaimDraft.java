package com.safarsaathi.app.data.baggage;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the traveller has filled in for one bag problem, saved on the phone after every change (ClaimStore), so
 * nothing is lost when the app closes in the baggage hall. Plain fields on purpose: it is written as JSON by Gson.
 * Dates are typed as DD/MM/YYYY and checked by {@link ClaimDeadline#parseDate}.
 */
public final class ClaimDraft {

    public String issue = BagIssue.DAMAGE.name();
    public String airline = "";
    public String flightNo = "";
    /** The 10-digit number on the bag tag sticker ("licence plate"). */
    public String bagTag = "";
    /** The Property Irregularity Report reference the airline's baggage counter gives, e.g. "BOMAI12345". */
    public String pirRef = "";
    /** DAMAGE / PILFERAGE: the day the bag was received. DELAY: the day the flight landed. */
    public String date = "";
    /** DELAY only: the day the late bag was finally delivered; empty while it is still missing. */
    public String deliveredDate = "";
    public String description = "";
    public List<EvidencePhoto> photos = new ArrayList<>();

    public BagIssue issue() {
        return BagIssue.fromName(issue);
    }

    public List<EvidencePhoto> photos(EvidencePhoto.Stage stage) {
        List<EvidencePhoto> result = new ArrayList<>();
        if (photos != null) {
            for (EvidencePhoto photo : photos) {
                if (photo.stage() == stage) {
                    result.add(photo);
                }
            }
        }
        return result;
    }

    /** A bag tag "licence plate" has exactly 10 digits (spaces are ignored). Empty = not filled in yet, not wrong. */
    public static boolean isValidBagTag(String tag) {
        String digits = tag == null ? "" : tag.replace(" ", "");
        return digits.isEmpty() || digits.matches("\\d{10}");
    }
}
