package com.safarsaathi.app.ui.trip;

import androidx.annotation.Nullable;

import com.safarsaathi.app.AppConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * How times look on the Trip screen. Always Indian time (Asia/Kolkata), whatever time zone the phone is
 * set to, because the whole trip happens in India. Plain Java, checked by TripTimeTest.
 */
public final class TripTime {

    public static final ZoneId ZONE = AppConfig.TRIP_ZONE;

    /** 24-hour clock, e.g. "08:00". The same in every language, so it needs no Locale. */
    private static final DateTimeFormatter HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private TripTime() {
    }

    /** "08:00" */
    public static String time(Instant instant) {
        return HOURS_MINUTES.format(instant.atZone(ZONE));
    }

    /** The calendar day in India at that moment. */
    public static LocalDate localDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    /**
     * "08:00–09:45"; just "10:05" when start and end are the same moment (a cab pickup);
     * "23:30–01:00 +1" when it ends on a later day.
     */
    public static String timeRange(Instant start, @Nullable Instant end) {
        String startText = time(start);
        if (end == null || !end.isAfter(start)) {
            return startText;
        }
        String range = startText + "–" + time(end); // – = en dash, the usual sign for "until"
        long extraDays = ChronoUnit.DAYS.between(localDate(start), localDate(end));
        return extraDays > 0 ? range + " +" + extraDays : range;
    }

    /** Day header for the timeline in the screen's language, e.g. "Sun, 11 Oct". */
    public static String dayHeader(LocalDate day, Locale locale) {
        return DateTimeFormatter.ofPattern("EEE, d MMM", locale).format(day);
    }
}
