package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.TripVaultEntity;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for what other parts of the app may learn about the vault ({@link VaultSummaries}, the rules behind
 * VaultSummaryReader): nothing sensitive, ever; only hotel name and address while locked; and the status rules.
 */
public class VaultSummariesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final Instant CHECKED = Instant.parse("2026-10-10T10:00:00Z");

    /** Every sensitive value of the sample trip; none may appear anywhere in a summary. */
    private static final List<String> SENSITIVE = Arrays.asList(
            "S0000001", "POL0000001", "SINGH", "PARAMVIR SINGH", "1990-05-14", "ENCRYPTED-NUMBER", ".bin", "/");

    @Test
    public void theSummaryClass_hasNoFieldForSensitiveData() {
        for (Field field : DocumentSummary.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String name = field.getName().toLowerCase(Locale.ROOT);
            for (String forbidden : new String[] {"number", "mrz", "image", "page", "file", "path", "birth",
                    "fullname", "holdername", "title", "wrapped", "sha"}) {
                assertFalse("DocumentSummary must not have a field like " + field.getName(), name.contains(forbidden));
            }
        }
    }

    @Test
    public void unlocked_noSensitiveValueAppearsAnywhere_onlyTheFirstName() throws Exception {
        List<DocumentSummary> summaries = VaultSummaries.build(sampleFacts(), CHECKED, true, TODAY);

        assertEquals(8, summaries.size());
        for (DocumentSummary summary : summaries) {
            String everything = allValues(summary);
            for (String secret : SENSITIVE) {
                assertFalse(summary.type + " leaks " + secret + ": " + everything, everything.contains(secret));
            }
            assertEquals("Paramvir", summary.holderFirstName);
            assertFalse(summary.lockedView);
            assertTrue(summary.sample);
        }
    }

    @Test
    public void locked_onlyHotelNameAndAddressComeBack() throws Exception {
        List<DocumentSummary> summaries = VaultSummaries.build(sampleFacts(), CHECKED, false, TODAY);

        assertEquals("Only the hotel booking", 1, summaries.size());
        DocumentSummary hotel = summaries.get(0);
        assertEquals(DocType.HOTEL, hotel.type);
        assertEquals("SAMPLE Hotel Kinshasa", hotel.hotelName);
        assertEquals("1 Sample Avenue, Kinshasa (made-up address)", hotel.hotelAddress);
        assertTrue(hotel.lockedView);
        assertTrue("SAMPLE data stays labelled", hotel.sample);
        assertNull(hotel.documentId);
        assertNull(hotel.holderFirstName);
        assertNull(hotel.expiryDate);
        assertNull(hotel.coveredFrom);
        assertNull(hotel.coveredTo);
        assertTrue(hotel.coveredCountries.isEmpty());
        assertNull(hotel.status);
    }

    @Test
    public void hotelFields_onlyOnHotelBookings() {
        for (DocumentSummary summary : VaultSummaries.build(sampleFacts(), CHECKED, true, TODAY)) {
            if (summary.type != DocType.HOTEL) {
                assertNull(summary.hotelName);
                assertNull(summary.hotelAddress);
            } else {
                assertEquals("SAMPLE Hotel Kinshasa", summary.hotelName);
            }
        }
    }

    @Test
    public void statusRules_worstFirst() {
        TravelDocumentEntity passport = doc(DocType.PASSPORT, "PARAMVIR SINGH");
        passport.expiryDate = LocalDate.of(2029, 3, 14);
        assertEquals(SummaryStatus.READY, status(passport, "Paramvir Singh", 1, 0, CHECKED));
        assertEquals("Added after the last offline check", SummaryStatus.NOT_OFFLINE_YET,
                status(passport, "Paramvir Singh", 1, 0, null));
        assertEquals("Middle name and word order are ignored", SummaryStatus.READY,
                status(passport, "Singh Kumar Paramvir", 1, 0, CHECKED));
        assertEquals(SummaryStatus.MISMATCH, status(passport, "Paramjit Singh", 1, 0, CHECKED));
        assertEquals(SummaryStatus.MISSING, status(passport, "Paramvir Singh", 2, 1, CHECKED));

        passport.expiryDate = TODAY.plusDays(VaultSummaries.EXPIRES_SOON_DAYS);
        assertEquals(SummaryStatus.EXPIRES_SOON, status(passport, "Paramvir Singh", 1, 0, CHECKED));
        passport.expiryDate = TODAY.minusDays(1);
        assertEquals("Expired is never called 'expires soon'", SummaryStatus.EXPIRED,
                status(passport, "Paramvir Singh", 1, 0, CHECKED));

        // A visa's last valid day counts as its expiry; a hotel stay in the past does not "expire".
        TravelDocumentEntity visa = doc(DocType.E_VISA, "PARAMVIR SINGH");
        visa.validTo = TODAY.minusDays(3);
        assertEquals(SummaryStatus.EXPIRED, status(visa, null, 1, 0, CHECKED));
        TravelDocumentEntity hotel = doc(DocType.HOTEL, "PARAMVIR SINGH");
        hotel.validTo = TODAY.minusDays(3);
        assertEquals(SummaryStatus.READY, status(hotel, null, 1, 0, CHECKED));
        // A travel insurance ending after the trip is not "expiring soon": covering the trip is V4's check.
        TravelDocumentEntity insurance = doc(DocType.INSURANCE, "PARAMVIR SINGH");
        insurance.validTo = TODAY.plusDays(30);
        assertEquals(SummaryStatus.READY, status(insurance, null, 1, 0, CHECKED));
    }

    @Test
    public void aDocumentFiledUnderTwoLegs_isSummarisedOnce() {
        TravelDocumentEntity ticket = doc(DocType.TICKET, "PARAMVIR SINGH");
        List<VaultSummaries.DocumentFacts> facts = Arrays.asList(
                new VaultSummaries.DocumentFacts(ticket, null, 1, 0),
                new VaultSummaries.DocumentFacts(ticket, null, 1, 0));
        assertEquals(1, VaultSummaries.build(facts, CHECKED, true, TODAY).size());
    }

    @Test
    public void currentTrip_duringThenNextThenLast_andNoTitleWhileLocked() {
        TripVaultEntity past = trip("Past", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));
        TripVaultEntity next = trip("Next", LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 18));
        TripVaultEntity later = trip("Later", LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 28));
        TripVaultEntity now = trip("Now", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12));

        assertEquals("Now", VaultSummaries.currentTrip(Arrays.asList(past, next, later, now), true, TODAY).title);
        assertEquals("Next", VaultSummaries.currentTrip(Arrays.asList(past, later, next), true, TODAY).title);
        assertEquals("Past", VaultSummaries.currentTrip(Collections.singletonList(past), true, TODAY).title);
        TripRef locked = VaultSummaries.currentTrip(Arrays.asList(next), false, TODAY);
        assertNull(locked.title);
        assertEquals(next.id, locked.id);
        assertNull(VaultSummaries.currentTrip(Collections.emptyList(), true, TODAY));
    }

    // ---------------------------------------------------------------------------------------------

    /** The sample trip's documents, with a fake encrypted number on each, as the reader would see them. */
    private static List<VaultSummaries.DocumentFacts> sampleFacts() {
        List<VaultSummaries.DocumentFacts> facts = new ArrayList<>();
        for (SampleVault.SampleDocument sample : SampleVault.paramvir(Instant.parse("2026-10-01T00:00:00Z")).documents) {
            sample.document.numberEncrypted = "ENCRYPTED-NUMBER";
            facts.add(new VaultSummaries.DocumentFacts(sample.document, SampleVault.HOLDER, 1, 0));
        }
        return facts;
    }

    private static SummaryStatus status(TravelDocumentEntity document, String travellerName, int pages, int missing,
                                        Instant offlineReadyAt) {
        return VaultSummaries.status(new VaultSummaries.DocumentFacts(document, travellerName, pages, missing),
                offlineReadyAt, TODAY);
    }

    private static TravelDocumentEntity doc(DocType type, String holder) {
        TravelDocumentEntity document = new TravelDocumentEntity();
        document.id = type + "-" + holder;
        document.type = type;
        document.holderName = holder;
        document.createdAt = Instant.parse("2026-10-01T00:00:00Z");
        return document;
    }

    private static TripVaultEntity trip(String title, LocalDate start, LocalDate end) {
        TripVaultEntity trip = new TripVaultEntity();
        trip.id = title.toLowerCase(Locale.ROOT);
        trip.title = title;
        trip.startDate = start;
        trip.endDate = end;
        return trip;
    }

    /** Every field value of a summary, as one string (to search for leaks). */
    private static String allValues(DocumentSummary summary) throws IllegalAccessException {
        StringBuilder out = new StringBuilder();
        for (Field field : DocumentSummary.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                out.append(field.get(summary)).append('|');
            }
        }
        return out.toString();
    }
}
