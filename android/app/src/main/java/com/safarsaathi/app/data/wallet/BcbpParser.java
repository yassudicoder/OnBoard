package com.safarsaathi.app.data.wallet;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the text inside a boarding pass barcode: the IATA "Bar Coded Boarding Pass" (BCBP) format that airlines put
 * in the PDF417, Aztec or QR code on every boarding pass. Plain Java, checked by BcbpParserTest.
 *
 * <p>Only the fixed-width "mandatory" items are read; they hold everything the wallet shows. The layout of the
 * first flight (positions from 0):
 * <pre>
 *  0      format code, "M"
 *  1      number of flights on the pass, 1-4
 *  2-21   passenger name, "SURNAME/GIVEN", padded with spaces (20)
 *  22     "E" = e-ticket
 *  23-29  booking reference (PNR) (7)
 *  30-32  from airport, 33-35 to airport
 *  36-38  airline code (3), 39-43 flight number (5)
 *  44-46  day of the year of the flight (Julian date, 001-366)
 *  47     compartment, 48-51 seat (4), 52-56 check-in sequence number (5), 57 passenger status
 *  58-59  length of the optional part that follows, as 2 hex digits
 * </pre>
 * Each further flight repeats positions 23-59 (37 characters) after the previous flight's optional part.
 */
public final class BcbpParser {

    /** Thrown for text that is not a boarding pass barcode; the message says why, in plain words. */
    public static final class InvalidBoardingPassException extends Exception {
        InvalidBoardingPassException(String message) {
            super(message);
        }
    }

    private static final int FIRST_LEG_START = 23;
    private static final int LEG_LENGTH = 37;
    private static final int HEADER_LENGTH = 23;

    private BcbpParser() {
    }

    /**
     * @param text  the barcode's text
     * @param today used to pick the year of the flight date (the year that puts it closest to today)
     */
    public static BoardingPass parse(String text, LocalDate today) throws InvalidBoardingPassException {
        if (text == null || text.length() < HEADER_LENGTH + LEG_LENGTH) {
            throw new InvalidBoardingPassException("This is not a boarding pass code: it is too short.");
        }
        if (text.charAt(0) != 'M') {
            throw new InvalidBoardingPassException("This is not a boarding pass code: it should start with M.");
        }
        int legCount = text.charAt(1) - '0';
        if (legCount < 1 || legCount > 4) {
            throw new InvalidBoardingPassException("This is not a boarding pass code: unknown number of flights.");
        }
        String name = text.substring(2, 22).trim();
        int slash = name.indexOf('/');
        String surname = slash >= 0 ? name.substring(0, slash).trim() : name;
        String givenName = slash >= 0 ? name.substring(slash + 1).trim() : "";
        if (surname.isEmpty() && givenName.isEmpty()) {
            throw new InvalidBoardingPassException("This boarding pass code has no passenger name.");
        }
        boolean eTicket = text.charAt(22) == 'E';

        List<BoardingPass.Leg> legs = new ArrayList<>();
        int start = FIRST_LEG_START;
        for (int i = 0; i < legCount; i++) {
            if (text.length() < start + LEG_LENGTH) {
                throw new InvalidBoardingPassException("This boarding pass code is cut off after flight " + i + ".");
            }
            legs.add(readLeg(text.substring(start, start + LEG_LENGTH), today, i + 1));
            int optionalLength = hex(text.substring(start + 35, start + 37), i + 1);
            start += LEG_LENGTH + optionalLength;
        }
        return new BoardingPass(surname, givenName, eTicket, legs);
    }

    /** One flight: positions 23-59 of the first flight, or the same 37 characters of a later one. */
    private static BoardingPass.Leg readLeg(String leg, LocalDate today, int number)
            throws InvalidBoardingPassException {
        String pnr = leg.substring(0, 7).trim();
        String from = leg.substring(7, 10).trim();
        String to = leg.substring(10, 13).trim();
        String carrier = leg.substring(13, 16).trim();
        String flightNumber = withoutLeadingZeros(leg.substring(16, 21).trim());
        String julian = leg.substring(21, 24).trim();
        String compartment = leg.substring(24, 25).trim();
        String seat = withoutLeadingZeros(leg.substring(25, 29).trim());
        String sequence = withoutLeadingZeros(leg.substring(29, 34).trim());
        if (pnr.isEmpty() || !isAirport(from) || !isAirport(to) || carrier.isEmpty() || flightNumber.isEmpty()) {
            throw new InvalidBoardingPassException("Flight " + number + " on this boarding pass code is not readable.");
        }
        return new BoardingPass.Leg(pnr, from, to, carrier, flightNumber, flightDate(julian, today, number),
                compartment, seat, sequence);
    }

    /**
     * The barcode holds only the day of the year (e.g. 284 = 11 October in a normal year). Of last year, this year
     * and next year, the one that puts the date closest to today wins, so a pass for early January read in late
     * December gets next year.
     */
    static LocalDate flightDate(String julian, LocalDate today, int number) throws InvalidBoardingPassException {
        int dayOfYear;
        try {
            dayOfYear = Integer.parseInt(julian);
        } catch (NumberFormatException e) {
            throw new InvalidBoardingPassException("Flight " + number + " has no readable date.");
        }
        LocalDate best = null;
        for (int year = today.getYear() - 1; year <= today.getYear() + 1; year++) {
            if (dayOfYear < 1 || dayOfYear > Year.of(year).length()) {
                continue;
            }
            try {
                LocalDate candidate = LocalDate.ofYearDay(year, dayOfYear);
                if (best == null || distance(candidate, today) < distance(best, today)) {
                    best = candidate;
                }
            } catch (DateTimeException ignored) {
                // not a valid day in that year
            }
        }
        if (best == null) {
            throw new InvalidBoardingPassException("Flight " + number + " has an impossible date (" + julian + ").");
        }
        return best;
    }

    private static long distance(LocalDate a, LocalDate b) {
        return Math.abs(a.toEpochDay() - b.toEpochDay());
    }

    private static int hex(String twoDigits, int number) throws InvalidBoardingPassException {
        try {
            return Integer.parseInt(twoDigits, 16);
        } catch (NumberFormatException e) {
            throw new InvalidBoardingPassException("Flight " + number + " on this boarding pass code is not readable.");
        }
    }

    private static boolean isAirport(String code) {
        return code.length() == 3 && code.chars().allMatch(Character::isLetter);
    }

    /** "0512" -> "512", "014A" -> "14A", "0000" -> "0". */
    private static String withoutLeadingZeros(String text) {
        int i = 0;
        while (i < text.length() - 1 && text.charAt(i) == '0') {
            i++;
        }
        return text.substring(i);
    }
}
