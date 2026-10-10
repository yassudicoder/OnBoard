package com.safarsaathi.app.data.vault;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.vault.capture.MrzWriter;
import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.TravellerEntity;
import com.safarsaathi.app.data.vault.db.TripCountryEntity;
import com.safarsaathi.app.data.vault.db.TripDocumentLinkEntity;
import com.safarsaathi.app.data.vault.db.TripLegEntity;
import com.safarsaathi.app.data.vault.db.TripTravellerEntity;
import com.safarsaathi.app.data.vault.db.TripVaultEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Paramvir's SAMPLE trip for the demo: Delhi (DEL) to Kigali, Rwanda (KGL, transit) to Kinshasa, DR Congo (FIH),
 * 11 to 18 November 2026.
 *
 * <p>Everything here is MADE UP and marked {@code sample = true}: the person, every number and booking reference,
 * the hotel and the insurer. Flights use the airline code "ZZ" only as an obviously fake code; they are not real
 * flights. The page images say "SAMPLE - NOT A REAL DOCUMENT" across them.
 *
 * <p>Deliberately incomplete, as in docs/demo-script.md: there is no DR Congo visa yet, and the hotel only covers
 * 12 to 14 Nov of a stay that lasts until 18 Nov. The pre-departure check (V4) is meant to find both.
 *
 * <p>This class holds no rules. Whether a visa, transit document or vaccination is needed comes only from sourced
 * country packs (V5), never from the sample data.
 */
public final class SampleVault {

    /** Which leg a sample document is filed under; null = whole trip. */
    public static final String LEG_FLIGHT = "flight";
    public static final String LEG_TRANSIT = "transit";
    public static final String LEG_STAY = "stay";

    /** The traveller's name as printed on all his sample documents. */
    public static final String HOLDER = "PARAMVIR SINGH";

    /** One sample document: its database row, its number (encrypted when saved) and the text of its page image. */
    public static final class SampleDocument {
        public final TravelDocumentEntity document;
        @Nullable
        public final String number;
        @Nullable
        public final String legKey;
        /** Lines drawn on the sample page image, e.g. "Passport no.: S0000001". */
        public final List<String> pageLines;
        /** The passport's machine-readable zone, drawn at the bottom of its page (empty for other documents). */
        public final List<String> mrzLines;

        SampleDocument(TravelDocumentEntity document, @Nullable String number, @Nullable String legKey,
                       List<String> pageLines, List<String> mrzLines) {
            this.document = document;
            this.number = number;
            this.legKey = legKey;
            this.pageLines = pageLines;
            this.mrzLines = mrzLines;
        }
    }

    /** Everything the repository saves for the sample trip. */
    public static final class Content {
        public TravellerEntity traveller;
        public TripVaultEntity trip;
        public final List<TripTravellerEntity> tripTravellers = new ArrayList<>();
        public final List<TripLegEntity> legs = new ArrayList<>();
        public final List<TripCountryEntity> countries = new ArrayList<>();
        public final List<SampleDocument> documents = new ArrayList<>();
        public final List<TripDocumentLinkEntity> links = new ArrayList<>();
    }

    private static final LocalDate DEPART = LocalDate.of(2026, 11, 11);
    private static final LocalDate ONWARD = LocalDate.of(2026, 11, 12);
    private static final LocalDate RETURN = LocalDate.of(2026, 11, 18);

    private SampleVault() {
    }

    /** Builds a fresh copy with new random ids (the sample can be deleted and loaded again). */
    public static Content paramvir(Instant now) {
        Content content = new Content();

        TravellerEntity traveller = new TravellerEntity();
        traveller.id = newId();
        traveller.name = HOLDER;
        traveller.nationality = "IN";
        traveller.dateOfBirth = LocalDate.of(1990, 5, 14);
        traveller.sample = true;
        traveller.createdAt = now;
        content.traveller = traveller;

        TripVaultEntity trip = new TripVaultEntity();
        trip.id = newId();
        trip.title = "Delhi → Kigali → Kinshasa";
        trip.startDate = DEPART;
        trip.endDate = RETURN;
        trip.sample = true;
        trip.createdAt = now;
        content.trip = trip;

        TripTravellerEntity onTrip = new TripTravellerEntity();
        onTrip.tripId = trip.id;
        onTrip.travellerId = traveller.id;
        content.tripTravellers.add(onTrip);

        TripLegEntity flight = leg(trip.id, 0, LegKind.FLIGHT, "Delhi → Kigali", "DEL", "KGL", null, DEPART, DEPART);
        TripLegEntity transit = leg(trip.id, 1, LegKind.TRANSIT, "Kigali transit", "KGL", null, "RW", DEPART, ONWARD);
        TripLegEntity stay = leg(trip.id, 2, LegKind.STAY, "Kinshasa", "FIH", null, "CD", ONWARD, RETURN);
        content.legs.addAll(Arrays.asList(flight, transit, stay));

        content.countries.add(country(trip.id, "IN", CountryRole.ORIGIN, DEPART, DEPART));
        content.countries.add(country(trip.id, "RW", CountryRole.TRANSIT, DEPART, ONWARD));
        content.countries.add(country(trip.id, "CD", CountryRole.DESTINATION, ONWARD, RETURN));

        // The passport: shared by every trip, so it is filed under the whole trip.
        TravelDocumentEntity passport = document(traveller.id, DocType.PASSPORT, "Passport (India)", now);
        passport.issuingCountry = "IN";
        passport.issueDate = LocalDate.of(2019, 3, 15);
        passport.expiryDate = LocalDate.of(2029, 3, 14);
        // Its machine-readable zone has correct check digits (MrzWriter), so scanning this page shows the reader
        // working, just like a real passport.
        content.documents.add(new SampleDocument(passport, "S0000001", null, Arrays.asList(
                "Type: Passport", "Country: India", "Surname: SINGH", "Given name: PARAMVIR",
                "Passport no.: S0000001", "Nationality: INDIAN", "Date of birth: 14 May 1990",
                "Date of issue: 15 Mar 2019", "Date of expiry: 14 Mar 2029"),
                MrzWriter.passport("IND", "SINGH", "PARAMVIR", "S0000001", "IND", LocalDate.of(1990, 5, 14), 'M',
                        LocalDate.of(2029, 3, 14), "")));

        TravelDocumentEntity ticketOut = ticket(traveller.id, "E-ticket Delhi → Kigali", "ZZ 101", "SMPL01",
                DEPART, now);
        add(content, ticketOut, null, LEG_FLIGHT,
                "Type: E-ticket", "Passenger: " + HOLDER, "Flight: ZZ 101 (sample airline)",
                "From: Delhi (DEL)", "To: Kigali (KGL)", "Date: 11 Nov 2026", "Booking ref: SMPL01");

        TravelDocumentEntity boardingPass = document(traveller.id, DocType.BOARDING_PASS,
                "Boarding pass Delhi → Kigali", now);
        boardingPass.flightNo = "ZZ 101";
        boardingPass.bookingRef = "SMPL01";
        boardingPass.validFrom = DEPART;
        boardingPass.validTo = DEPART;
        add(content, boardingPass, null, LEG_FLIGHT,
                "Type: Boarding pass", "Passenger: " + HOLDER, "Flight: ZZ 101", "From: DEL  To: KGL",
                "Date: 11 Nov 2026", "Seat: 23A");

        // The onward flight belongs to the Kigali stop: it is what Paramvir shows at the transit desk.
        TravelDocumentEntity ticketOnward = ticket(traveller.id, "E-ticket Kigali → Kinshasa", "ZZ 205", "SMPL02",
                ONWARD, now);
        add(content, ticketOnward, null, LEG_TRANSIT,
                "Type: E-ticket", "Passenger: " + HOLDER, "Flight: ZZ 205 (sample airline)",
                "From: Kigali (KGL)", "To: Kinshasa (FIH)", "Date: 12 Nov 2026", "Booking ref: SMPL02");

        // Covers only 12-14 Nov, though the stay lasts until 18 Nov (the demo's deliberate gap).
        TravelDocumentEntity hotel = document(traveller.id, DocType.HOTEL, "Hotel booking, Kinshasa", now);
        hotel.bookingRef = "HTL-0001";
        hotel.placeName = "SAMPLE Hotel Kinshasa";
        hotel.address = "1 Sample Avenue, Kinshasa (made-up address)";
        hotel.validFrom = ONWARD;
        hotel.validTo = LocalDate.of(2026, 11, 14);
        add(content, hotel, null, LEG_STAY,
                "Type: Hotel booking", "Guest: " + HOLDER, "Hotel: SAMPLE Hotel Kinshasa",
                "Address: 1 Sample Avenue, Kinshasa (made-up)",
                "Check-in: 12 Nov 2026", "Check-out: 14 Nov 2026", "Confirmation: HTL-0001");

        TravelDocumentEntity ticketHome = ticket(traveller.id, "Return ticket Kinshasa → Delhi", "ZZ 306", "SMPL03",
                RETURN, now);
        add(content, ticketHome, null, LEG_STAY,
                "Type: E-ticket", "Passenger: " + HOLDER, "Flight: ZZ 306 (sample airline)",
                "From: Kinshasa (FIH)", "To: Delhi (DEL)", "Date: 18 Nov 2026", "Booking ref: SMPL03");

        TravelDocumentEntity insurance = document(traveller.id, DocType.INSURANCE, "Travel insurance", now);
        insurance.validFrom = DEPART;
        insurance.validTo = RETURN;
        add(content, insurance, "POL0000001", null,
                "Type: Travel insurance", "Insured: " + HOLDER, "Insurer: SAMPLE Insurance Co.",
                "Policy no.: POL0000001", "Cover: 11 Nov 2026 to 18 Nov 2026");

        TravelDocumentEntity vaccination = document(traveller.id, DocType.VACCINATION_CERT,
                "Vaccination certificate (yellow fever)", now);
        vaccination.issueDate = LocalDate.of(2024, 2, 1);
        add(content, vaccination, null, null,
                "Type: International certificate of vaccination", "Name: " + HOLDER,
                "Vaccine: Yellow fever", "Date given: 1 Feb 2024");

        // File each document under its leg.
        for (SampleDocument sample : content.documents) {
            TripDocumentLinkEntity link = new TripDocumentLinkEntity();
            link.tripId = trip.id;
            link.documentId = sample.document.id;
            if (LEG_FLIGHT.equals(sample.legKey)) {
                link.legId = flight.id;
            } else if (LEG_TRANSIT.equals(sample.legKey)) {
                link.legId = transit.id;
            } else if (LEG_STAY.equals(sample.legKey)) {
                link.legId = stay.id;
            }
            content.links.add(link);
        }
        return content;
    }

    private static void add(Content content, TravelDocumentEntity document, @Nullable String number,
                            @Nullable String legKey, String... pageLines) {
        content.documents.add(new SampleDocument(document, number, legKey, Arrays.asList(pageLines),
                Collections.emptyList()));
    }

    private static TravelDocumentEntity document(String travellerId, DocType type, String title, Instant now) {
        TravelDocumentEntity document = new TravelDocumentEntity();
        document.id = newId();
        document.travellerId = travellerId;
        document.type = type;
        document.title = title;
        document.holderName = HOLDER;
        document.source = DocSource.MANUAL;
        // The made-up fields are exactly what was intended, so they count as confirmed.
        document.fieldsConfirmed = true;
        document.sample = true;
        document.createdAt = now;
        return document;
    }

    /** A flight ticket: validFrom = validTo = the day of the flight. */
    private static TravelDocumentEntity ticket(String travellerId, String title, String flightNo, String bookingRef,
                                               LocalDate day, Instant now) {
        TravelDocumentEntity ticket = document(travellerId, DocType.TICKET, title, now);
        ticket.flightNo = flightNo;
        ticket.bookingRef = bookingRef;
        ticket.validFrom = day;
        ticket.validTo = day;
        return ticket;
    }

    private static TripLegEntity leg(String tripId, int position, LegKind kind, String title, String fromCode,
                                     @Nullable String toCode, @Nullable String countryIso2, LocalDate start,
                                     LocalDate end) {
        TripLegEntity leg = new TripLegEntity();
        leg.id = newId();
        leg.tripId = tripId;
        leg.position = position;
        leg.kind = kind;
        leg.title = title;
        leg.fromCode = fromCode;
        leg.toCode = toCode;
        leg.countryIso2 = countryIso2;
        leg.startDate = start;
        leg.endDate = end;
        return leg;
    }

    private static TripCountryEntity country(String tripId, String iso2, CountryRole role, LocalDate from,
                                             LocalDate to) {
        TripCountryEntity country = new TripCountryEntity();
        country.tripId = tripId;
        country.iso2 = iso2;
        country.role = role;
        country.fromDate = from;
        country.toDate = to;
        return country;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
