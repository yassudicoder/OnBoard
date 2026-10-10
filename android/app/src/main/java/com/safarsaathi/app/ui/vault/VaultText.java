package com.safarsaathi.app.ui.vault;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.vault.DocType;
import com.safarsaathi.app.data.vault.LegKind;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;

/** The words the vault screens use for dates, document types, legs and countries. */
final class VaultText {

    private VaultText() {
    }

    /** "14 Mar 2029", with month names in the phone's language. */
    static String date(LocalDate date) {
        return DateTimeFormatter.ofPattern("d MMM uuuu", Locale.getDefault()).format(date);
    }

    /** "11 Nov 2026 to 18 Nov 2026", one date when both are the same day, "No dates yet" when both are missing. */
    static String dateRange(Context context, @Nullable LocalDate from, @Nullable LocalDate to) {
        if (from == null && to == null) {
            return context.getString(R.string.vault_no_dates);
        }
        if (from == null || to == null || from.equals(to)) {
            return date(from != null ? from : to);
        }
        return context.getString(R.string.vault_dates, date(from), date(to));
    }

    @StringRes
    static int typeName(DocType type) {
        switch (type) {
            case PASSPORT: return R.string.vault_type_passport;
            case ID_CARD: return R.string.vault_type_id_card;
            case VISA: return R.string.vault_type_visa;
            case E_VISA: return R.string.vault_type_e_visa;
            case TRANSIT_VISA: return R.string.vault_type_transit_visa;
            case ENTRY_FORM: return R.string.vault_type_entry_form;
            case TICKET: return R.string.vault_type_ticket;
            case BOARDING_PASS: return R.string.vault_type_boarding_pass;
            case HOTEL: return R.string.vault_type_hotel;
            case INSURANCE: return R.string.vault_type_insurance;
            case VACCINATION_CERT: return R.string.vault_type_vaccination;
            case OTHER:
            default:
                return R.string.vault_type_other;
        }
    }

    @StringRes
    static int legKindName(LegKind kind) {
        switch (kind) {
            case TRANSIT: return R.string.vault_leg_transit;
            case STAY: return R.string.vault_leg_stay;
            case FLIGHT:
            default:
                return R.string.vault_leg_flight;
        }
    }

    /**
     * The second line of a document in a folder: what it is, plus its flight and dates. Never the number.
     * "Ticket · ZZ 205 · 12 Nov 2026", "Passport · Expires 14 Mar 2029", "Hotel booking · 12 Nov 2026 to 14 Nov 2026".
     */
    static String documentDetail(Context context, TravelDocumentEntity document) {
        List<String> parts = new ArrayList<>();
        parts.add(context.getString(typeName(document.type)));
        if (document.flightNo != null) {
            parts.add(document.flightNo);
        }
        if (document.validFrom != null || document.validTo != null) {
            parts.add(dateRange(context, document.validFrom, document.validTo));
        } else if (document.expiryDate != null) {
            parts.add(context.getString(R.string.vault_detail_expires, date(document.expiryDate)));
        }
        return String.join(" · ", parts);
    }

    /**
     * Country name from its 2-letter code, in the phone's language ("RW" becomes "Rwanda"). Uses the phone's own
     * country list, so no names are typed in by us. Unknown codes are shown as they are.
     */
    static String countryName(String iso2) {
        try {
            String name = new Locale.Builder().setRegion(iso2).build().getDisplayCountry();
            return name == null || name.isEmpty() ? iso2 : name;
        } catch (IllformedLocaleException e) {
            return iso2;
        }
    }

    /** "CD" becomes "Congo - Kinshasa"; "RW,UG" becomes "Rwanda, Uganda". */
    static String countryNames(String commaSeparatedIso2) {
        List<String> names = new ArrayList<>();
        for (String code : commaSeparatedIso2.split(",")) {
            if (!code.trim().isEmpty()) {
                names.add(countryName(code.trim()));
            }
        }
        return String.join(", ", names);
    }
}
