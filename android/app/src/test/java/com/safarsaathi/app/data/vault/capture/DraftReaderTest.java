package com.safarsaathi.app.data.vault.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.DocType;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Unit tests for turning read text and barcodes into a pre-filled draft ({@link DraftReader}), country codes
 * ({@link CountryCodes}) and the checks before saving ({@link DocumentDraft#problems()}).
 */
public class DraftReaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    /** A real-format boarding pass code (IATA BCBP) for flight IJ 512 on day 284 (11 Oct), seat 14A. */
    private static final String BOARDING_PASS = "M1SHARMA/ADITI        EK7Q2ZM BOMBLRIJ 0512 284Y014A0001 100";

    @Test
    public void passportMrz_preFillsThePassport_andTheTraveller() {
        List<String> mrz = MrzWriter.passport("IND", "SINGH", "PARAMVIR", "S0000001", "IND",
                LocalDate.of(1990, 5, 14), 'M', LocalDate.of(2029, 3, 14), "");
        String text = "REPUBLIC OF INDIA\n" + mrz.get(0) + "\n" + mrz.get(1);

        DocumentDraft draft = DraftReader.read(text, Collections.emptyList(), DocSource.SCAN, TODAY);

        assertEquals(DocType.PASSPORT, draft.type);
        assertEquals("PARAMVIR SINGH", draft.holderName);
        assertEquals("S0000001", draft.number);
        assertEquals("IN", draft.issuingCountry);
        assertEquals(LocalDate.of(2029, 3, 14), draft.expiryDate);
        assertNull("A passport's issue date is not in the MRZ", draft.issueDate);
        assertEquals("IN", draft.nationality);
        assertEquals(LocalDate.of(1990, 5, 14), draft.birthDate);
        assertEquals(DocSource.SCAN, draft.source);
        assertTrue(draft.readAutomatically);
        assertEquals(1, draft.notes.size());
        assertEquals(ReadNote.Kind.MRZ_READ, draft.notes.get(0).kind);
    }

    @Test
    public void misreadPassportNumber_isFlaggedForTheTraveller() {
        String text = "P<INDSINGH<<PARAMVIR<<<<<<<<<<<<<<<<<<<<<<<<\nS0000007<9IND9005145M2903149<<<<<<<<<<<<<<04";

        DocumentDraft draft = DraftReader.read(text, Collections.emptyList(), DocSource.SCAN, TODAY);

        assertEquals("S0000007", draft.number); // shown as read, never silently "corrected"
        assertTrue(hasCheckFailure(draft, ReadNote.Field.NUMBER));
        assertTrue(hasCheckFailure(draft, ReadNote.Field.WHOLE_ZONE));
        assertFalse(hasCheckFailure(draft, ReadNote.Field.BIRTH_DATE));
    }

    @Test
    public void lookAlikeMisread_isFlaggedNotFixed_andTheTravellersCorrectionIsChecked() {
        // What the emulator's text recognition really read from the SAMPLE passport page: the letter O for a zero.
        String text = "P<INDSINGH<<PARAMVIR<<<<<<<<<<<<<<<<<<<<<<<<\nSO000001<9IND9005145M2903149<<<<<<<<<<<<<<04";

        DocumentDraft draft = DraftReader.read(text, Collections.emptyList(), DocSource.SCAN, TODAY);

        // Shown exactly as read, with a warning. Any change inside the number moves the overall check digit in the
        // same way, so the check digits cannot say which character is wrong: the app never guesses.
        assertEquals("SO000001", draft.number);
        assertTrue(hasCheckFailure(draft, ReadNote.Field.NUMBER));
        assertEquals(Character.valueOf('9'), draft.numberCheckDigit);
        assertEquals(Boolean.FALSE, draft.numberMatchesCheckDigit("SO000001"));
        // The traveller types it from the passport: the printed check digit agrees.
        assertEquals(Boolean.TRUE, draft.numberMatchesCheckDigit("s000 0001"));
        // Nothing to compare with for a typed-in document.
        assertNull(new DocumentDraft().numberMatchesCheckDigit("S0000001"));
    }

    @Test
    public void visaMrz_givesTheLastValidDay_andAsksToCheckTheCountries() {
        String text = "V<RWASINGH<<PARAMVIR<<<<<<<<<<<<<<<<<<<<<<<<\nL8988901C4IND9005145M2611309<<<<<<<<<<<<<<<<";

        DocumentDraft draft = DraftReader.read(text, Collections.emptyList(), DocSource.SCAN, TODAY);

        assertEquals(DocType.VISA, draft.type);
        assertEquals("RW", draft.issuingCountry);
        assertEquals("RW", draft.coversCountries);
        assertEquals(LocalDate.of(2026, 11, 30), draft.validTo);
        assertNull(draft.expiryDate);
        boolean asked = false;
        for (ReadNote note : draft.notes) {
            asked |= note.kind == ReadNote.Kind.CHECK_COVERED_COUNTRIES;
        }
        assertTrue(asked);
    }

    @Test
    public void unknownCountryCode_isShownAsPrinted_notGuessed() {
        String text = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<10";

        DocumentDraft draft = DraftReader.read(text, Collections.emptyList(), DocSource.SCAN, TODAY);

        assertNull(draft.issuingCountry);
        assertEquals(ReadNote.Kind.COUNTRY_NOT_RECOGNISED, draft.notes.get(1).kind);
        assertEquals("UTO", draft.notes.get(1).detail);
    }

    @Test
    public void boardingPassBarcode_preFillsTheFlight() {
        DocumentDraft draft = DraftReader.read("SEAT 14A", Collections.singletonList(BOARDING_PASS),
                DocSource.IMPORT, TODAY);

        assertEquals(DocType.BOARDING_PASS, draft.type);
        assertEquals("ADITI SHARMA", draft.holderName);
        assertEquals("IJ 512", draft.flightNo);
        assertEquals("K7Q2ZM", draft.bookingRef);
        assertEquals(LocalDate.of(2026, 10, 11), draft.validFrom);
        assertEquals("BOM → BLR", draft.titleHint);
        assertEquals(ReadNote.Kind.BARCODE_READ, draft.notes.get(0).kind);
    }

    @Test
    public void nothingReadable_givesAnEmptyDraftThatSaysSo() {
        DocumentDraft draft = DraftReader.read("Hotel Confirmation", Collections.singletonList("https://example.com"),
                DocSource.PDF, TODAY);

        assertNull(draft.type);
        assertFalse(draft.readAutomatically);
        assertEquals(ReadNote.Kind.NOTHING_READ, draft.notes.get(0).kind);
    }

    @Test
    public void countryCodes_comeFromTheBuiltInIsoList() {
        assertEquals("IN", CountryCodes.alpha2("IND"));
        assertEquals("RW", CountryCodes.alpha2("RWA"));
        assertEquals("CD", CountryCodes.alpha2("COD"));
        assertEquals("DE", CountryCodes.alpha2("D<<"));
        assertNull(CountryCodes.alpha2("XXA")); // stateless: not a country
        assertEquals("IND", CountryCodes.alpha3("in"));
        assertTrue(CountryCodes.isCountry("CD"));
        assertFalse(CountryCodes.isCountry("ZZ"));
    }

    @Test
    public void draftProblems_beforeSaving() {
        DocumentDraft draft = new DocumentDraft();
        assertEquals(EnumSet.of(DocumentDraft.Problem.TYPE_MISSING, DocumentDraft.Problem.TITLE_MISSING),
                draft.problems());

        draft.type = DocType.HOTEL;
        draft.title = "Hotel booking";
        draft.validFrom = LocalDate.of(2026, 11, 14);
        draft.validTo = LocalDate.of(2026, 11, 12);
        draft.issuingCountry = "ZZ";
        draft.coversCountries = "CD, XX";
        assertEquals(EnumSet.of(DocumentDraft.Problem.VALID_TO_BEFORE_VALID_FROM,
                DocumentDraft.Problem.ISSUING_COUNTRY_NOT_A_COUNTRY,
                DocumentDraft.Problem.COVERED_COUNTRY_NOT_A_COUNTRY), draft.problems());

        draft.validTo = LocalDate.of(2026, 11, 18);
        draft.issuingCountry = null;
        draft.coversCountries = "cd rw";
        assertTrue(draft.problems().isEmpty());
        assertEquals("CD,RW", DocumentDraft.normaliseCountries(draft.coversCountries));
        draft.number = " s 000 0001 ";
        assertEquals("S0000001", draft.cleanNumber());
    }

    private static boolean hasCheckFailure(DocumentDraft draft, ReadNote.Field field) {
        for (ReadNote note : draft.notes) {
            if (note.kind == ReadNote.Kind.MRZ_CHECK_FAILED && note.field == field) {
                return true;
            }
        }
        return false;
    }
}
