package com.safarsaathi.backend.demo;

import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.safarsaathi.backend.trip.BlockProvider;
import com.safarsaathi.backend.trip.BlockType;
import com.safarsaathi.backend.trip.Flex;
import com.safarsaathi.backend.trip.Persona;
import com.safarsaathi.backend.trip.Purpose;
import com.safarsaathi.backend.trip.TravelMode;
import com.safarsaathi.backend.trip.TripStatus;
import com.safarsaathi.recovery.connection.TicketType;

/**
 * The shape of {@code mock-data/demo-trips.json}, as Java records. Jackson (the JSON library) fills them: each JSON
 * field goes into the record field with the same name. mock-data/README.md explains every field.
 *
 * <p>Text such as "FLIGHT" becomes the enum value with that name; an unknown value stops the app with an error that
 * names it. "08:00" becomes a {@link LocalTime}. The {@code "_comment"} lines at the top are skipped.
 */
@JsonIgnoreProperties("_comment")
record DemoTripFile(List<SeedTrip> trips) {

    /** One trip. {@code key} is only a name inside the file; the database hands out the real id. */
    record SeedTrip(String key, String title, Purpose purpose, Persona persona, String language, int travellers,
                    TripStatus status, List<SeedBlock> blocks, List<SeedLink> links) {
    }

    /**
     * One block. Its times are relative to the trip day and LOCAL to where they happen: it starts {@code day} days
     * after the trip day at {@code start}, and ends on day {@code endDay} at {@code end} (without {@code endDay}: the
     * day it starts). A FLIGHT gives its airports in {@code flight}: start = local time at the departure airport, end =
     * local time at the arrival airport (zones from mock-data/recovery/airport-rules.json). Any other block is in
     * {@code zone}, or in the trip zone (Asia/Kolkata) when {@code zone} is missing.
     */
    record SeedBlock(String key, BlockType type, String title, int day, LocalTime start, Integer endDay,
                     LocalTime end, String locationName, Double lat, Double lng, BlockProvider provider,
                     String providerRef, Flex flex, boolean anchor, Integer freeChangeUntilMinutes,
                     int changeFeeInr, String notes, String zone, SeedFlight flight) {
    }

    /** The flight fields of a FLIGHT block. Terminals may be null (not known). */
    record SeedFlight(String flightNo, String depAirport, String arrAirport, String depTerminal,
                      String arrTerminal) {
    }

    /**
     * One link between two blocks of the same trip, named by their {@code key}s. Between two flights
     * {@code minGapMinutes} must be 0 (the minimum connection time comes from the airport rules file) and
     * {@code ticketType} says whether both flights are on one ticket (missing = UNKNOWN: the app asks).
     */
    record SeedLink(String from, String to, int minGapMinutes, int travelMinutes, TravelMode travelMode,
                    TicketType ticketType) {
    }
}
