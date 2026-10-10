package com.safarsaathi.app.ui.trip;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Unit tests for {@link TripTime}: times are always shown in Indian time (Asia/Kolkata), whatever time
 * zone the laptop or phone running the code is set to.
 */
public class TripTimeTest {

    @Test
    public void time_isIndianTime() {
        // 02:30 UTC = 08:00 in India (+05:30).
        assertEquals("08:00", TripTime.time(Instant.parse("2026-10-11T02:30:00Z")));
    }

    @Test
    public void localDate_usesTheIndianCalendarDay() {
        // 19:00 UTC on 10 Oct is already 00:30 on 11 Oct in India.
        assertEquals(LocalDate.of(2026, 10, 11), TripTime.localDate(Instant.parse("2026-10-10T19:00:00Z")));
    }

    @Test
    public void timeRange_showsStartAndEnd() {
        assertEquals("08:00–09:45", TripTime.timeRange(
                Instant.parse("2026-10-11T02:30:00Z"), Instant.parse("2026-10-11T04:15:00Z")));
    }

    @Test
    public void timeRange_sameStartAndEnd_showsOneTime() {
        Instant pickup = Instant.parse("2026-10-11T04:35:00Z");

        assertEquals("10:05", TripTime.timeRange(pickup, pickup));
        assertEquals("10:05", TripTime.timeRange(pickup, null));
    }

    @Test
    public void timeRange_endingOnTheNextDay_isMarkedPlusOne() {
        // 23:30 -> 01:00 the next day, Indian time.
        assertEquals("23:30–01:00 +1", TripTime.timeRange(
                Instant.parse("2026-10-11T18:00:00Z"), Instant.parse("2026-10-11T19:30:00Z")));
    }

    @Test
    public void dayHeader_isShortWeekdayDayAndMonth() {
        assertEquals("Sun, 11 Oct", TripTime.dayHeader(LocalDate.of(2026, 10, 11), Locale.ENGLISH));
        assertEquals("Sat, 10 Oct", TripTime.dayHeader(LocalDate.of(2026, 10, 10), Locale.ENGLISH));
    }
}
