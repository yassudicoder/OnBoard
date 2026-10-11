package com.safarsaathi.app.data.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/** SAMPLE flights for "Plan a journey". */
public class JourneyCatalogTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 11);

    @Test
    public void mumbaiToGoaHasFiveDirectFlightsOnTheDay() {
        List<JourneyCatalog.Flight> flights =
                JourneyCatalog.flights(JourneyCatalog.city("BOM"), JourneyCatalog.city("GOX"), DAY);
        assertEquals(5, flights.size());
        for (JourneyCatalog.Flight flight : flights) {
            assertEquals(DAY, flight.departs.toLocalDate());
            assertTrue(flight.minutes() >= 75 && flight.minutes() <= 85);
            assertTrue(flight.flightNo.startsWith("IJ "));
        }
    }

    @Test
    public void sameCityHasNoFlights() {
        assertTrue(JourneyCatalog.flights(JourneyCatalog.city("BOM"), JourneyCatalog.city("BOM"), DAY).isEmpty());
    }

    @Test
    public void unknownCityFallsBackToMumbai() {
        assertEquals("BOM", JourneyCatalog.city("XYZ").code);
    }
}
