package com.safarsaathi.backend.watcher;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.trip.Block;

/**
 * Real flight status from AeroDataBox, through RapidAPI. Used only when AERODATABOX_API_KEY is set (see
 * {@link FlightWatcher#chooseProvider}); otherwise the {@link MockFlightStatusProvider} runs.
 *
 * <p>Deliberately thin and SAFE: any problem (no network, wrong key, quota used up, unknown flight, unexpected JSON)
 * means "no change", with one warning in the log. It can never break the demo.
 *
 * <p>How it asks: {@code GET https://aerodatabox.p.rapidapi.com/flights/number/IJ512/2026-10-11?dateLocalRole=Departure}
 * with the headers {@code X-RapidAPI-Key} (our key) and {@code X-RapidAPI-Host: aerodatabox.p.rapidapi.com}
 * (AeroDataBox OpenAPI spec 1.15, endpoint "Flight Status (single day)"). The answer is a list of flights; we read
 * {@code status} ("Canceled" = cancelled) and {@code departure.revisedTime.utc} / {@code arrival.revisedTime.utc}
 * (the newest estimate). 204 No Content = the flight is not known on that day.
 *
 * <p>The flight number comes from the block title, e.g. "IJ 512" in "Flight IJ 512 Mumbai to Bengaluru".
 * (Our demo airlines are invented, so for them AeroDataBox always answers "not known".)
 */
public class AeroDataBoxProvider implements FlightStatusProvider {

    /** The RapidAPI gateway address of AeroDataBox. Tests pass another address to the constructor. */
    public static final String BASE_URL = "https://aerodatabox.p.rapidapi.com";

    private static final String RAPIDAPI_HOST = "aerodatabox.p.rapidapi.com";

    /** A flight number inside a title: two letters/digits (airline) + up to 4 digits, e.g. "IJ 512", "6E2134". */
    private static final Pattern FLIGHT_NUMBER = Pattern.compile("\\b([A-Z][A-Z0-9]|[0-9][A-Z])\\s?(\\d{1,4})\\b");

    /** Only ask from one day before departure until a few hours after landing: earlier or later there is no news. */
    private static final Duration ASK_FROM_BEFORE_START = Duration.ofHours(24);
    private static final Duration ASK_UNTIL_AFTER_END = Duration.ofHours(3);

    /** Every call uses up paid quota, so ask about the same flight at most every 5 minutes, even in demo mode. */
    private static final Duration MIN_TIME_BETWEEN_CALLS = Duration.ofMinutes(5);

    /** A newer estimate this close to our time is normal noise, not a delay worth a ripple. */
    private static final Duration MIN_DELAY = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(AeroDataBoxProvider.class);

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId zone;

    /** Block id -> when we last asked about it (for {@link #MIN_TIME_BETWEEN_CALLS}). */
    private final Map<Long, Instant> lastAsked = new ConcurrentHashMap<>();

    /**
     * @param apiKey  the RapidAPI key (never logged)
     * @param baseUrl {@link #BASE_URL}, or a test server
     * @param zone    the trip zone (Asia/Kolkata): the departure date in the URL is the local date there
     */
    public AeroDataBoxProvider(String apiKey, String baseUrl, ObjectMapper objectMapper, Clock clock, ZoneId zone) {
        // Short timeouts: the watcher checks the flights one after another, so a slow answer must not hold it up.
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(3_000);
        timeouts.setReadTimeout(5_000);
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .defaultHeader("X-RapidAPI-Key", apiKey)
                .defaultHeader("X-RapidAPI-Host", RAPIDAPI_HOST)
                .build();
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public Optional<FlightStatusChange> check(Block flight) {
        String number = flightNumber(flight.getTitle());
        if (number == null || !worthAsking(flight)) {
            return Optional.empty();
        }
        try {
            LocalDate departureDay = flight.getStartTime().atZone(zone).toLocalDate();
            String body = http.get()
                    .uri("/flights/number/{number}/{date}?dateLocalRole=Departure", number, departureDay.toString())
                    .retrieve()
                    .body(String.class);
            return changeFrom(body, flight, number);
        } catch (Exception e) { // anything at all: treat it as "no news", never let it reach the watcher
            log.warn("AeroDataBox: could not check flight {} (block {}), treated as no change: {}",
                    number, flight.getId(), e.toString());
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "AeroDataBoxProvider (REAL: AERODATABOX_API_KEY is set)";
    }

    /**
     * Turns AeroDataBox's answer into a change for our block. Package-private so a test can feed it a sample answer.
     *
     * @param body the JSON text, or null/blank for 204 No Content
     */
    Optional<FlightStatusChange> changeFrom(String body, Block flight, String number) throws IOException {
        if (body == null || body.isBlank()) {
            return Optional.empty(); // 204 No Content: AeroDataBox does not know this flight on that day
        }
        JsonNode flights = objectMapper.readTree(body);
        if (!flights.isArray() || flights.isEmpty()) {
            return Optional.empty();
        }
        JsonNode found = flights.get(0); // a flight number flies at most once a day on our routes
        String status = found.path("status").asText("");
        if ("Canceled".equals(status)) {
            return Optional.of(FlightStatusChange.cancelled("Cancelled (AeroDataBox status of " + number + ")"));
        }
        Instant newDeparture = utcTime(found.path("departure").path("revisedTime"));
        if (newDeparture == null || newDeparture.isBefore(flight.getStartTime().plus(MIN_DELAY))) {
            return Optional.empty(); // no newer estimate, or not (much) later than our plan: nothing to do
        }
        // Compared with the block's CURRENT start, so asking again after we moved the block gives no change.
        Duration late = Duration.between(flight.getStartTime(), newDeparture);
        Instant newArrival = utcTime(found.path("arrival").path("revisedTime"));
        if (newArrival == null || newArrival.isBefore(newDeparture)) {
            newArrival = flight.getEndTime().plus(late); // no arrival estimate: the flight takes as long as planned
        }
        return Optional.of(FlightStatusChange.movedTo(newDeparture, newArrival, "Delayed by " + late.toMinutes()
                + " minutes (AeroDataBox status of " + number + ": " + status + ")"));
    }

    /** "IJ512" from "Flight IJ 512 Mumbai to Bengaluru"; null when the title has no flight number. */
    static String flightNumber(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = FLIGHT_NUMBER.matcher(title);
        return matcher.find() ? matcher.group(1) + matcher.group(2) : null;
    }

    /** Reads {"utc": "2026-10-11 02:30Z"}; null when missing or not a time. */
    static Instant utcTime(JsonNode time) {
        JsonNode utc = time.path("utc");
        if (!utc.isTextual()) {
            return null;
        }
        try {
            // AeroDataBox writes a space between date and time; Java expects a "T" there.
            return OffsetDateTime.parse(utc.asText().trim().replace(' ', 'T')).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Near the flight's time, and not asked about it in the last 5 minutes. Remembers that we ask now. */
    private boolean worthAsking(Block flight) {
        Instant now = clock.instant();
        boolean nearTheFlight = !now.isBefore(flight.getStartTime().minus(ASK_FROM_BEFORE_START))
                && !now.isAfter(flight.getEndTime().plus(ASK_UNTIL_AFTER_END));
        if (!nearTheFlight) {
            return false;
        }
        Instant last = lastAsked.get(flight.getId());
        if (last != null && now.isBefore(last.plus(MIN_TIME_BETWEEN_CALLS))) {
            return false;
        }
        lastAsked.put(flight.getId(), now);
        return true;
    }
}
