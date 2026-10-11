package com.safarsaathi.app.data.journey;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SAMPLE flights for "Plan a journey" (hard-coded for the demo: there is no live schedule source yet; live search comes
 * with the online assistant, phase A3). Every flight shown from here is labelled SAMPLE on screen. Durations are
 * rough team estimates per city pair, not airline timetables. Plain Java.
 */
public final class JourneyCatalog {

    /** A city with its main airport code. */
    public static final class City {
        public final String code;
        public final String name;
        public final String airport;

        City(String code, String name, String airport) {
            this.code = code;
            this.name = name;
            this.airport = airport;
        }

        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    /** One SAMPLE flight. */
    public static final class Flight {
        public final String flightNo;
        public final City from;
        public final City to;
        public final LocalDateTime departs;
        public final LocalDateTime arrives;

        Flight(String flightNo, City from, City to, LocalDateTime departs, LocalDateTime arrives) {
            this.flightNo = flightNo;
            this.from = from;
            this.to = to;
            this.departs = departs;
            this.arrives = arrives;
        }

        public long minutes() {
            return java.time.Duration.between(departs, arrives).toMinutes();
        }
    }

    public static final List<City> CITIES = Arrays.asList(
            new City("BOM", "Mumbai", "Mumbai Airport T2"),
            new City("GOX", "Goa", "Goa Mopa Airport"),
            new City("BLR", "Bengaluru", "Bengaluru Airport T1"),
            new City("DEL", "Delhi", "Delhi Airport T3"),
            new City("HYD", "Hyderabad", "Hyderabad Airport"),
            new City("MAA", "Chennai", "Chennai Airport T1"),
            new City("CCU", "Kolkata", "Kolkata Airport"),
            new City("AMD", "Ahmedabad", "Ahmedabad Airport T1"),
            new City("PNQ", "Pune", "Pune Airport"),
            new City("JAI", "Jaipur", "Jaipur Airport T2"),
            new City("DXB", "Dubai", "Dubai Airport T3"),
            new City("SIN", "Singapore", "Singapore Changi T2"));

    private static final LocalTime[] DEPARTURES = {
            LocalTime.of(6, 10), LocalTime.of(9, 35), LocalTime.of(13, 20), LocalTime.of(18, 45), LocalTime.of(21, 55)};

    private JourneyCatalog() {
    }

    public static City city(String code) {
        for (City city : CITIES) {
            if (city.code.equals(code)) {
                return city;
            }
        }
        return CITIES.get(0);
    }

    /** SAMPLE flights from one city to another on a day (none when both are the same city). */
    public static List<Flight> flights(City from, City to, LocalDate day) {
        List<Flight> flights = new ArrayList<>();
        if (from.code.equals(to.code)) {
            return flights;
        }
        int minutes = minutesBetween(from.code, to.code);
        int seed = Math.abs((from.code + to.code).hashCode());
        for (int i = 0; i < DEPARTURES.length; i++) {
            LocalDateTime departs = LocalDateTime.of(day, DEPARTURES[i]).plusMinutes((seed + i * 7) % 20);
            String number = "IJ " + (200 + (seed + i * 37) % 700);
            flights.add(new Flight(number, from, to, departs, departs.plusMinutes(minutes + (i % 2) * 10)));
        }
        return flights;
    }

    /** Rough flying time for a pair (team estimate for the demo), in minutes. */
    static int minutesBetween(String a, String b) {
        String pair = a.compareTo(b) < 0 ? a + b : b + a;
        switch (pair) {
            case "BOMGOX": return 75;
            case "BLRBOM": return 105;
            case "BOMDEL": return 130;
            case "BOMHYD": return 85;
            case "BOMMAA": return 115;
            case "BOMCCU": return 165;
            case "AMDBOM": return 70;
            case "BOMPNQ": return 50;
            case "BOMJAI": return 105;
            case "BOMDXB": return 200;
            case "BOMSIN": return 330;
            case "BLRDEL": return 165;
            case "DELGOX": return 150;
            case "BLRGOX": return 70;
            default: return 140;
        }
    }
}
