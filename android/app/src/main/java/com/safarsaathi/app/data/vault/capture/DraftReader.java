package com.safarsaathi.app.data.vault.capture;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.vault.DocSource;
import com.safarsaathi.app.data.vault.DocType;
import com.safarsaathi.app.data.wallet.BcbpParser;
import com.safarsaathi.app.data.wallet.BoardingPass;

import java.time.LocalDate;
import java.util.List;

/**
 * Turns what the phone read on a document's pages into a pre-filled {@link DocumentDraft}:
 * <ol>
 *   <li>a machine-readable zone (passport, ID card, visa) in the recognised text, read by {@link MrzParser};</li>
 *   <li>otherwise a boarding pass barcode (IATA BCBP), read by the Wallet's {@link BcbpParser};</li>
 *   <li>otherwise nothing: an empty draft that says so, and the traveller types the details.</li>
 * </ol>
 * It only copies what is printed; it never fills in a field from guesswork, apart from one clearly marked pre-fill
 * (a visa's countries = the country that issued it), which the confirm screen asks the traveller to check.
 * Plain Java: the Android part (camera, text recognition, barcode scanning) only supplies the text and barcodes.
 */
public final class DraftReader {

    private DraftReader() {
    }

    /**
     * @param recognisedText text recognised on the pages (null or empty when there was none)
     * @param barcodes       the text of every barcode found on the pages
     * @param today          for the century of MRZ dates and the year of a boarding pass date
     */
    public static DocumentDraft read(@Nullable String recognisedText, List<String> barcodes, DocSource source,
                                     LocalDate today) {
        DocumentDraft draft = new DocumentDraft();
        draft.source = source;
        MrzResult mrz = recognisedText == null ? null : MrzParser.find(recognisedText, today);
        if (mrz != null) {
            fromMrz(draft, mrz);
            return draft;
        }
        for (String barcode : barcodes) {
            try {
                fromBoardingPass(draft, BcbpParser.parse(barcode, today));
                return draft;
            } catch (BcbpParser.InvalidBoardingPassException notABoardingPass) {
                // try the next barcode
            }
        }
        draft.notes.add(new ReadNote(ReadNote.Kind.NOTHING_READ, null, null));
        return draft;
    }

    private static void fromMrz(DocumentDraft draft, MrzResult mrz) {
        draft.readAutomatically = true;
        draft.notes.add(new ReadNote(ReadNote.Kind.MRZ_READ, null, null));
        if (mrz.isPassport()) {
            draft.type = DocType.PASSPORT;
        } else if (mrz.isVisa()) {
            draft.type = DocType.VISA;
        } else if (mrz.format == MrzResult.Format.TD1 || mrz.format == MrzResult.Format.TD2) {
            draft.type = DocType.ID_CARD;
        } else {
            draft.type = DocType.OTHER;
        }
        draft.holderName = mrz.fullName();
        draft.number = mrz.documentNumber;
        draft.numberCheckDigit = mrz.numberCheckDigit;
        draft.issuingCountry = country(draft, mrz.issuingState);
        draft.titleHint = draft.issuingCountry;
        if (mrz.isVisa()) {
            // A visa's MRZ date is the last day it is valid. Which countries it is for is not in the MRZ: the issuing
            // country is the usual answer (not for every visa, e.g. Schengen), so it is only a pre-fill to check.
            draft.validTo = mrz.expiryDate;
            draft.coversCountries = draft.issuingCountry;
            if (draft.issuingCountry != null) {
                draft.notes.add(new ReadNote(ReadNote.Kind.CHECK_COVERED_COUNTRIES, null, draft.issuingCountry));
            }
        } else {
            draft.expiryDate = mrz.expiryDate;
        }
        draft.nationality = CountryCodes.alpha2(mrz.nationality);
        draft.birthDate = mrz.birthDate;

        if (!mrz.numberCheckOk) {
            draft.notes.add(new ReadNote(ReadNote.Kind.MRZ_CHECK_FAILED, ReadNote.Field.NUMBER, null));
        }
        if (!mrz.birthDateCheckOk) {
            draft.notes.add(new ReadNote(ReadNote.Kind.MRZ_CHECK_FAILED, ReadNote.Field.BIRTH_DATE, null));
        }
        if (!mrz.expiryCheckOk) {
            draft.notes.add(new ReadNote(ReadNote.Kind.MRZ_CHECK_FAILED, ReadNote.Field.EXPIRY_DATE, null));
        }
        if (!mrz.optionalCheckOk) {
            draft.notes.add(new ReadNote(ReadNote.Kind.MRZ_CHECK_FAILED, ReadNote.Field.PERSONAL_NUMBER, null));
        }
        if (!mrz.compositeCheckOk) {
            draft.notes.add(new ReadNote(ReadNote.Kind.MRZ_CHECK_FAILED, ReadNote.Field.WHOLE_ZONE, null));
        }
    }

    private static void fromBoardingPass(DocumentDraft draft, BoardingPass pass) {
        BoardingPass.Leg leg = pass.legs.get(0);
        draft.readAutomatically = true;
        draft.notes.add(new ReadNote(ReadNote.Kind.BARCODE_READ, null, null));
        draft.type = DocType.BOARDING_PASS;
        draft.holderName = pass.passengerName();
        draft.flightNo = leg.flight();
        draft.bookingRef = leg.pnr;
        draft.validFrom = leg.date;
        draft.validTo = leg.date;
        draft.titleHint = leg.from + " → " + leg.to;
    }

    /** The 2-letter code of a printed 3-letter code; a note when it is not a country the phone knows. */
    @Nullable
    private static String country(DocumentDraft draft, @Nullable String alpha3) {
        String alpha2 = CountryCodes.alpha2(alpha3);
        if (alpha2 == null && alpha3 != null && !alpha3.isEmpty()) {
            draft.notes.add(new ReadNote(ReadNote.Kind.COUNTRY_NOT_RECOGNISED, null, alpha3));
        }
        return alpha2;
    }
}
