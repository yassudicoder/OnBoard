package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.TripCountryEntity;
import com.safarsaathi.app.data.vault.db.TripDocumentLinkEntity;
import com.safarsaathi.app.data.vault.db.TripLegEntity;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for Paramvir's sample trip ({@link SampleVault}): the shape the demo script relies on, and that
 * everything in it is marked as SAMPLE.
 */
public class SampleVaultTest {

    private final SampleVault.Content sample = SampleVault.paramvir(Instant.parse("2026-10-11T00:00:00Z"));

    @Test
    public void threeLegs_inOrder() {
        List<String> titles = new ArrayList<>();
        for (TripLegEntity leg : sample.legs) {
            titles.add(leg.title);
        }
        assertEquals(List.of("Delhi → Kigali", "Kigali transit", "Kinshasa"), titles);
        assertEquals(LegKind.FLIGHT, sample.legs.get(0).kind);
        assertEquals(LegKind.TRANSIT, sample.legs.get(1).kind);
        assertEquals("RW", sample.legs.get(1).countryIso2);
        assertEquals(LegKind.STAY, sample.legs.get(2).kind);
        assertEquals("CD", sample.legs.get(2).countryIso2);
    }

    @Test
    public void countriesAndTheirRoles() {
        assertEquals(3, sample.countries.size());
        assertCountry(sample.countries.get(0), "IN", CountryRole.ORIGIN);
        assertCountry(sample.countries.get(1), "RW", CountryRole.TRANSIT);
        assertCountry(sample.countries.get(2), "CD", CountryRole.DESTINATION);
    }

    @Test
    public void everythingIsMarkedSample() {
        assertTrue(sample.traveller.sample);
        assertTrue(sample.trip.sample);
        for (SampleVault.SampleDocument document : sample.documents) {
            assertTrue(document.document.title, document.document.sample);
            assertEquals(SampleVault.HOLDER, document.document.holderName);
        }
    }

    @Test
    public void theDemoGaps_noCongoVisa_andHotelOnly12To14Nov() {
        for (SampleVault.SampleDocument document : sample.documents) {
            DocType type = document.document.type;
            assertFalse("The sample has no visa yet; the demo adds it",
                    type == DocType.VISA || type == DocType.E_VISA || type == DocType.TRANSIT_VISA);
        }
        TravelDocumentEntity hotel = only(DocType.HOTEL);
        assertEquals(LocalDate.of(2026, 11, 12), hotel.validFrom);
        assertEquals(LocalDate.of(2026, 11, 14), hotel.validTo);
        assertTrue(hotel.validTo.isBefore(sample.trip.endDate));
    }

    @Test
    public void passport_isForTheWholeTrip_withItsNumberKeptSeparately() {
        TravelDocumentEntity passport = only(DocType.PASSPORT);
        assertEquals("IN", passport.issuingCountry);
        // The plain number travels next to the row; the repository encrypts it before saving.
        assertNull(passport.numberEncrypted);
        for (SampleVault.SampleDocument document : sample.documents) {
            if (document.document == passport) {
                assertEquals("S0000001", document.number);
                assertNull(document.legKey);
            }
        }
        TripDocumentLinkEntity link = linkOf(passport.id);
        assertNull(link.legId);
    }

    @Test
    public void onwardFlight_isFiledUnderTheKigaliTransit() {
        TravelDocumentEntity onward = null;
        for (SampleVault.SampleDocument document : sample.documents) {
            if ("ZZ 205".equals(document.document.flightNo)) {
                onward = document.document;
            }
        }
        assertEquals(sample.legs.get(1).id, linkOf(onward.id).legId);
    }

    @Test
    public void everyDocumentIsLinkedOnce_andIdsAreUnique() {
        assertEquals(sample.documents.size(), sample.links.size());
        Set<String> ids = new HashSet<>();
        for (SampleVault.SampleDocument document : sample.documents) {
            assertTrue(ids.add(document.document.id));
        }
        // A new copy gets new ids, so the sample can be deleted and loaded again.
        assertNotEquals(sample.trip.id, SampleVault.paramvir(Instant.now()).trip.id);
    }

    private TravelDocumentEntity only(DocType type) {
        TravelDocumentEntity found = null;
        for (SampleVault.SampleDocument document : sample.documents) {
            if (document.document.type == type) {
                assertNull("More than one " + type, found);
                found = document.document;
            }
        }
        return found;
    }

    private TripDocumentLinkEntity linkOf(String documentId) {
        for (TripDocumentLinkEntity link : sample.links) {
            if (link.documentId.equals(documentId)) {
                return link;
            }
        }
        throw new AssertionError("No link for " + documentId);
    }

    private static void assertCountry(TripCountryEntity country, String iso2, CountryRole role) {
        assertEquals(iso2, country.iso2);
        assertEquals(role, country.role);
    }
}
