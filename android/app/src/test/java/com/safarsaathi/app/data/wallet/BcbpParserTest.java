package com.safarsaathi.app.data.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/** Unit tests for reading boarding pass barcodes ({@link BcbpParser}), on hand-built IATA BCBP strings. */
public class BcbpParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    /**
     * One flight: SHARMA/ADITI, e-ticket, PNR K7Q2ZM, BOM-BLR, IJ 0512, day 284 (11 Oct 2026), Y, seat 014A,
     * check-in 0001, status 1, no optional part ("00"). Exactly 60 characters.
     */
    static final String ONE_FLIGHT = "M1SHARMA/ADITI        EK7Q2ZM BOMBLRIJ 0512 284Y014A0001 100";

    /**
     * Two flights on one pass (Pune-Delhi, then Delhi-Leh), each with no optional part. The second flight repeats
     * the 37 characters from the booking reference to the optional-part length.
     */
    static final String TWO_FLIGHTS = "M2PATIL/SUNITA        ER4T8WD PNQDELSM 0231 284Y021C0007 100"
            + "R4T8WD DELIXLSM 0445 284Y009F0003 100";

    @Test
    public void oneFlight_everyField() throws Exception {
        assertEquals(60, ONE_FLIGHT.length());

        BoardingPass pass = BcbpParser.parse(ONE_FLIGHT, TODAY);

        assertEquals("SHARMA", pass.surname);
        assertEquals("ADITI", pass.givenName);
        assertEquals("ADITI SHARMA", pass.passengerName());
        assertTrue(pass.electronicTicket);
        assertEquals(1, pass.legs.size());
        BoardingPass.Leg leg = pass.legs.get(0);
        assertEquals("K7Q2ZM", leg.pnr);
        assertEquals("BOM", leg.from);
        assertEquals("BLR", leg.to);
        assertEquals("IJ 512", leg.flight());
        assertEquals(LocalDate.of(2026, 10, 11), leg.date);
        assertEquals("Y", leg.compartment);
        assertEquals("14A", leg.seat);
        assertEquals("1", leg.sequence);
    }

    @Test
    public void twoFlights_onOnePass() throws Exception {
        BoardingPass pass = BcbpParser.parse(TWO_FLIGHTS, TODAY);

        assertEquals("SUNITA PATIL", pass.passengerName());
        assertEquals(2, pass.legs.size());
        assertEquals("SM 231", pass.legs.get(0).flight());
        assertEquals("PNQ", pass.legs.get(0).from);
        assertEquals("DEL", pass.legs.get(0).to);
        assertEquals("21C", pass.legs.get(0).seat);
        assertEquals("SM 445", pass.legs.get(1).flight());
        assertEquals("DEL", pass.legs.get(1).from);
        assertEquals("IXL", pass.legs.get(1).to);
        assertEquals("R4T8WD", pass.legs.get(1).pnr);
    }

    @Test
    public void optionalPartIsSkipped_beforeTheNextFlight() throws Exception {
        // First flight with a 4-character optional part ("04" + "ABCD"), then the second flight.
        String text = "M2PATIL/SUNITA        ER4T8WD PNQDELSM 0231 284Y021C0007 104ABCD"
                + "R4T8WD DELIXLSM 0445 284Y009F0003 100";

        BoardingPass pass = BcbpParser.parse(text, TODAY);

        assertEquals("IXL", pass.legs.get(1).to);
    }

    @Test
    public void flightDate_takesTheYearClosestToToday() throws Exception {
        // Day 5 read on 28 Dec 2026 = 5 Jan 2027; day 360 read on 3 Jan 2027 = 26 Dec 2026.
        assertEquals(LocalDate.of(2027, 1, 5), BcbpParser.flightDate("005", LocalDate.of(2026, 12, 28), 1));
        assertEquals(LocalDate.of(2026, 12, 26), BcbpParser.flightDate("360", LocalDate.of(2027, 1, 3), 1));
    }

    @Test
    public void noSeatYet_andNotAnETicket() throws Exception {
        String text = "M1SHARMA/ADITI         K7Q2ZM BOMBLRIJ 0512 284Y    0001 100";

        BoardingPass pass = BcbpParser.parse(text, TODAY);

        assertFalse(pass.electronicTicket);
        assertEquals("", pass.legs.get(0).seat);
    }

    @Test
    public void notABoardingPass_isRefusedWithAReason() {
        assertThrows(BcbpParser.InvalidBoardingPassException.class, () -> BcbpParser.parse("", TODAY));
        assertThrows(BcbpParser.InvalidBoardingPassException.class,
                () -> BcbpParser.parse("https://example.com/this-is-a-normal-qr-code-not-a-boarding-pass", TODAY));
        // Right length, wrong first letter.
        assertThrows(BcbpParser.InvalidBoardingPassException.class,
                () -> BcbpParser.parse("X" + ONE_FLIGHT.substring(1), TODAY));
        // Says 2 flights but holds only 1.
        assertThrows(BcbpParser.InvalidBoardingPassException.class,
                () -> BcbpParser.parse("M2" + ONE_FLIGHT.substring(2), TODAY));
        // Airport code with digits.
        assertThrows(BcbpParser.InvalidBoardingPassException.class,
                () -> BcbpParser.parse(ONE_FLIGHT.replace("BOMBLR", "B0MBLR"), TODAY));
    }
}
