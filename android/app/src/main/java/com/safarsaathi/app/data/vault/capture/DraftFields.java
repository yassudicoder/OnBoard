package com.safarsaathi.app.data.vault.capture;

import com.safarsaathi.app.data.vault.DocType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which fields the confirm screen shows for each kind of document. A hotel booking has no passport number, a
 * boarding pass no expiry date; showing only what is printed on that kind of document keeps the form short.
 * Fields that are hidden are cleared before saving, so a changed document type never leaves stale values behind.
 */
public final class DraftFields {

    public enum Field {
        HOLDER, NUMBER, ISSUING_COUNTRY, ISSUE_DATE, EXPIRY_DATE, VALID_FROM, VALID_TO, COVERS_COUNTRIES, ENTRIES,
        FLIGHT, BOOKING_REF, PLACE_NAME, ADDRESS
    }

    private DraftFields() {
    }

    public static Set<Field> fieldsFor(DocType type) {
        switch (type) {
            case PASSPORT:
            case ID_CARD:
                return EnumSet.of(Field.HOLDER, Field.NUMBER, Field.ISSUING_COUNTRY, Field.ISSUE_DATE,
                        Field.EXPIRY_DATE);
            case VISA:
            case E_VISA:
            case TRANSIT_VISA:
                return EnumSet.of(Field.HOLDER, Field.NUMBER, Field.ISSUING_COUNTRY, Field.ISSUE_DATE,
                        Field.VALID_FROM, Field.VALID_TO, Field.COVERS_COUNTRIES, Field.ENTRIES);
            case ENTRY_FORM:
                return EnumSet.of(Field.HOLDER, Field.NUMBER, Field.ISSUING_COUNTRY, Field.VALID_FROM,
                        Field.VALID_TO, Field.COVERS_COUNTRIES);
            case TICKET:
                return EnumSet.of(Field.HOLDER, Field.NUMBER, Field.FLIGHT, Field.VALID_FROM, Field.BOOKING_REF);
            case BOARDING_PASS:
                return EnumSet.of(Field.HOLDER, Field.FLIGHT, Field.VALID_FROM, Field.BOOKING_REF);
            case HOTEL:
                return EnumSet.of(Field.HOLDER, Field.PLACE_NAME, Field.ADDRESS, Field.VALID_FROM, Field.VALID_TO,
                        Field.BOOKING_REF);
            case INSURANCE:
                return EnumSet.of(Field.HOLDER, Field.NUMBER, Field.VALID_FROM, Field.VALID_TO);
            case VACCINATION_CERT:
                return EnumSet.of(Field.HOLDER, Field.NUMBER, Field.ISSUING_COUNTRY, Field.ISSUE_DATE,
                        Field.VALID_FROM, Field.VALID_TO);
            case OTHER:
            default:
                return EnumSet.allOf(Field.class);
        }
    }

    /**
     * Clears every field the type does not use. For a single-day document (ticket, boarding pass) the last day is
     * set to the flight day.
     */
    public static void keepOnlyFieldsOf(DocumentDraft draft) {
        if (draft.type == null) {
            return;
        }
        Set<Field> shown = fieldsFor(draft.type);
        if (!shown.contains(Field.HOLDER)) {
            draft.holderName = null;
        }
        if (!shown.contains(Field.NUMBER)) {
            draft.number = null;
        }
        if (!shown.contains(Field.ISSUING_COUNTRY)) {
            draft.issuingCountry = null;
        }
        if (!shown.contains(Field.ISSUE_DATE)) {
            draft.issueDate = null;
        }
        if (!shown.contains(Field.EXPIRY_DATE)) {
            draft.expiryDate = null;
        }
        if (!shown.contains(Field.VALID_FROM)) {
            draft.validFrom = null;
        }
        if (!shown.contains(Field.VALID_TO)) {
            draft.validTo = draft.type == DocType.TICKET || draft.type == DocType.BOARDING_PASS
                    ? draft.validFrom : null;
        }
        if (!shown.contains(Field.COVERS_COUNTRIES)) {
            draft.coversCountries = null;
        }
        if (!shown.contains(Field.ENTRIES)) {
            draft.entries = null;
        }
        if (!shown.contains(Field.FLIGHT)) {
            draft.flightNo = null;
        }
        if (!shown.contains(Field.BOOKING_REF)) {
            draft.bookingRef = null;
        }
        if (!shown.contains(Field.PLACE_NAME)) {
            draft.placeName = null;
        }
        if (!shown.contains(Field.ADDRESS)) {
            draft.address = null;
        }
    }
}
