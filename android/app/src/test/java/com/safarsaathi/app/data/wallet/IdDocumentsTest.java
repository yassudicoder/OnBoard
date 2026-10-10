package com.safarsaathi.app.data.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/** Unit tests for the ID document rules ({@link IdDocuments}): what is kept, how it is masked, visa status. */
public class IdDocumentsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Test
    public void aadhaar_onlyEverTheLast4Digits() {
        assertTrue(IdDocuments.isValidAadhaarLast4("1234"));
        assertFalse(IdDocuments.isValidAadhaarLast4("123"));
        assertFalse(IdDocuments.isValidAadhaarLast4("123456789012")); // the full number is refused
        assertFalse(IdDocuments.isValidAadhaarLast4("12a4"));
        assertFalse(IdDocuments.isValidAadhaarLast4(null));
        assertEquals("XXXX XXXX 1234", IdDocuments.maskAadhaar("1234"));
    }

    @Test
    public void pan_formatAndMasking() {
        assertEquals("ABCDE1234F", IdDocuments.normalisePan(" abcde 1234 f "));
        assertTrue(IdDocuments.isValidPan("ABCDE1234F"));
        assertFalse(IdDocuments.isValidPan("ABCD1234F"));
        assertFalse(IdDocuments.isValidPan("ABCDE12345"));
        assertFalse(IdDocuments.isValidPan("abcde1234f")); // not normalised
        assertEquals("XXXXX1234F", IdDocuments.maskPan("ABCDE1234F"));
        assertEquals("XXXXXXXXXX", IdDocuments.maskPan("short"));
    }

    @Test
    public void dates_areReadStrictly() {
        assertEquals(LocalDate.of(2027, 12, 31), IdDocuments.parseDate("31/12/2027"));
        assertEquals(LocalDate.of(2026, 10, 1), IdDocuments.parseDate(" 01/10/2026 "));
        assertNull(IdDocuments.parseDate("31/02/2027")); // no 31 February
        assertNull(IdDocuments.parseDate("2027-12-31"));
        assertNull(IdDocuments.parseDate(""));
        assertNull(IdDocuments.parseDate(null));
    }

    @Test
    public void visaStatus() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertEquals(IdDocuments.VisaStatus.VALID, IdDocuments.visaStatus(from, LocalDate.of(2027, 12, 31), TODAY));
        assertEquals(IdDocuments.VisaStatus.EXPIRES_SOON,
                IdDocuments.visaStatus(from, TODAY.plusDays(IdDocuments.EXPIRES_SOON_DAYS), TODAY));
        assertEquals(IdDocuments.VisaStatus.EXPIRES_SOON, IdDocuments.visaStatus(from, TODAY, TODAY));
        assertEquals(IdDocuments.VisaStatus.EXPIRED, IdDocuments.visaStatus(from, TODAY.minusDays(1), TODAY));
        assertEquals(IdDocuments.VisaStatus.NOT_YET_VALID,
                IdDocuments.visaStatus(TODAY.plusDays(5), TODAY.plusDays(100), TODAY));
        assertEquals(12, IdDocuments.daysLeft(TODAY.plusDays(12), TODAY));
    }
}
