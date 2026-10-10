package com.safarsaathi.app.data.wallet;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What a boarding pass barcode says, read by {@link BcbpParser}. Plain Java, so it is tested on the laptop.
 * One pass can hold several flights ("legs", e.g. Pune-Delhi and Delhi-Leh on one ticket).
 */
public final class BoardingPass {

    /** The passenger as printed: "SHARMA/ADITI" becomes surname "SHARMA", given name "ADITI". */
    public final String surname;
    public final String givenName;
    /** True when the ticket is an e-ticket (the barcode says "E"). */
    public final boolean electronicTicket;
    public final List<Leg> legs;

    BoardingPass(String surname, String givenName, boolean electronicTicket, List<Leg> legs) {
        this.surname = surname;
        this.givenName = givenName;
        this.electronicTicket = electronicTicket;
        this.legs = Collections.unmodifiableList(legs);
    }

    /** "ADITI SHARMA", or just one of the two when the other is empty. */
    public String passengerName() {
        if (givenName.isEmpty()) {
            return surname;
        }
        return surname.isEmpty() ? givenName : givenName + " " + surname;
    }

    /** One flight on the pass. */
    public static final class Leg {
        /** Booking reference (PNR) at the operating airline, e.g. "K7Q2ZM". */
        public final String pnr;
        /** Airport codes, e.g. "BOM" and "BLR". */
        public final String from;
        public final String to;
        /** Airline code, e.g. "IJ". */
        public final String carrier;
        /** Flight number without leading zeros, e.g. "512" (a letter suffix stays: "512A"). */
        public final String flightNumber;
        /** The flight's date. The barcode only holds the day of the year; the year is the one closest to today. */
        public final LocalDate date;
        /** Cabin letter, e.g. "Y" (economy). */
        public final String compartment;
        /** Seat without leading zeros, e.g. "14A"; empty when not assigned yet. */
        public final String seat;
        /** Check-in sequence number without leading zeros, e.g. "1". */
        public final String sequence;

        Leg(String pnr, String from, String to, String carrier, String flightNumber, LocalDate date,
            String compartment, String seat, String sequence) {
            this.pnr = pnr;
            this.from = from;
            this.to = to;
            this.carrier = carrier;
            this.flightNumber = flightNumber;
            this.date = date;
            this.compartment = compartment;
            this.seat = seat;
            this.sequence = sequence;
        }

        /** "IJ 512" */
        public String flight() {
            return carrier + " " + flightNumber;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Leg)) {
                return false;
            }
            Leg that = (Leg) other;
            return pnr.equals(that.pnr) && from.equals(that.from) && to.equals(that.to)
                    && carrier.equals(that.carrier) && flightNumber.equals(that.flightNumber)
                    && date.equals(that.date) && compartment.equals(that.compartment)
                    && seat.equals(that.seat) && sequence.equals(that.sequence);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pnr, from, to, carrier, flightNumber, date, compartment, seat, sequence);
        }

        @NonNull
        @Override
        public String toString() {
            return flight() + " " + from + "-" + to + " " + date + " seat " + seat + " PNR " + pnr;
        }
    }
}
