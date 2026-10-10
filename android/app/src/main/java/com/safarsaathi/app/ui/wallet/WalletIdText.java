package com.safarsaathi.app.ui.wallet;

import android.content.Context;

import androidx.annotation.ColorRes;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.local.IdDocumentEntity;
import com.safarsaathi.app.data.wallet.IdDocuments;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The words for a saved visa, shared by the Wallet screen and the PDF so both always say the same thing:
 * "Tourist · Multiple entry · 1 Oct 2026 to 31 Dec 2027" and "Valid until 31 Dec 2027" / "Expires in 12 days" /
 * "Expired on 1 Jan 2026".
 */
final class WalletIdText {

    private WalletIdText() {
    }

    static String visaDetail(Context context, IdDocumentEntity visa) {
        List<String> parts = new ArrayList<>();
        if (visa.visaType != null && !visa.visaType.isEmpty()) {
            parts.add(visa.visaType);
        }
        parts.add(context.getString(IdDocuments.ENTRIES_MULTIPLE.equals(visa.entries)
                ? R.string.ids_entries_multiple : R.string.ids_entries_single));
        LocalDate from = date(visa.validFrom);
        LocalDate until = date(visa.validUntil);
        if (from != null && until != null) {
            parts.add(context.getString(R.string.ids_validity, TravelPackPdf.shortDate(from),
                    TravelPackPdf.shortDate(until)));
        }
        return String.join(" · ", parts);
    }

    static String visaStatus(Context context, IdDocumentEntity visa, LocalDate today) {
        LocalDate from = date(visa.validFrom);
        LocalDate until = date(visa.validUntil);
        if (from == null || until == null) {
            return "";
        }
        switch (IdDocuments.visaStatus(from, until, today)) {
            case EXPIRED:
                return context.getString(R.string.ids_status_expired, TravelPackPdf.shortDate(until));
            case NOT_YET_VALID:
                return context.getString(R.string.ids_status_not_yet, TravelPackPdf.shortDate(from));
            case EXPIRES_SOON: {
                int days = (int) IdDocuments.daysLeft(until, today);
                return days == 0 ? context.getString(R.string.ids_status_expires_today)
                        : context.getResources().getQuantityString(R.plurals.ids_status_expires_soon, days, days);
            }
            case VALID:
            default:
                return context.getString(R.string.ids_status_valid, TravelPackPdf.shortDate(until));
        }
    }

    /** Chip colours: green valid, amber expires soon or not yet valid, red expired. Text always says it too. */
    @ColorRes
    static int[] visaStatusColors(IdDocumentEntity visa, LocalDate today) {
        LocalDate from = date(visa.validFrom);
        LocalDate until = date(visa.validUntil);
        if (from == null || until == null) {
            return new int[] {R.color.status_done_container, R.color.status_done_on_container};
        }
        switch (IdDocuments.visaStatus(from, until, today)) {
            case EXPIRED:
                return new int[] {R.color.status_needs_user_container, R.color.status_needs_user_on_container};
            case EXPIRES_SOON:
            case NOT_YET_VALID:
                return new int[] {R.color.status_adjusted_container, R.color.status_adjusted_on_container};
            case VALID:
            default:
                return new int[] {R.color.status_ok_container, R.color.status_ok_on_container};
        }
    }

    private static LocalDate date(String iso) {
        try {
            return iso == null ? null : LocalDate.parse(iso);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
