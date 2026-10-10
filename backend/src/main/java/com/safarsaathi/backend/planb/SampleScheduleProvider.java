package com.safarsaathi.backend.planb;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.MockDataFile;
import com.safarsaathi.backend.connection.AirportRulesProvider;
import com.safarsaathi.recovery.alternatives.CandidateRoute;
import com.safarsaathi.recovery.alternatives.OptionSource;
import com.safarsaathi.recovery.alternatives.SeatAvailability;
import com.safarsaathi.recovery.connection.FlightLeg;
import com.safarsaathi.recovery.connection.FlightPhase;
import com.safarsaathi.recovery.rules.AirportPack;
import com.safarsaathi.recovery.rules.AirportRules;

/**
 * The SAMPLE DATA flight search: {@code mock-data/recovery/sample-schedules.json}, invented daily flights of invented
 * airlines. It offers direct flights and one-stop routes (the second flight leaving after the first lands, within
 * RecoverySettings.maxLayoverHours). It does not check minimum connection times: the ranker does, so a route that is
 * too tight shows up as "rejected" with the reason instead of silently vanishing.
 *
 * <p>Every flight runs every day, so the file never goes stale: an instance is "the flight on day D", with its
 * departure at the local time of its departure airport and its arrival at the local time of its arrival airport
 * (time zones from airport-rules.json). Seats are never known here.
 */
@Component
public class SampleScheduleProvider implements AlternativeFlightProvider {

    static final String SCHEDULE_FILE = "mock-data/recovery/sample-schedules.json";

    private static final Logger log = LoggerFactory.getLogger(SampleScheduleProvider.class);

    private final List<SampleFlight> flights;
    private final AirportRules rules;
    private final Duration maxLayover;

    public SampleScheduleProvider(ObjectMapper objectMapper, AirportRulesProvider rulesProvider,
                                  FileRecoverySettings settings) {
        this.rules = rulesProvider.rules();
        this.flights = MockDataFile.read(objectMapper, SCHEDULE_FILE, ScheduleFile.class).flights();
        this.maxLayover = Duration.ofHours(settings.settings().maxLayoverHours);
        for (SampleFlight flight : flights) {
            zoneOf(flight.from(), flight.flightNo());
            zoneOf(flight.to(), flight.flightNo());
        }
        log.info("Alternative flights: {} with {} SAMPLE flights (invented, daily)", name(), flights.size());
    }

    @Override
    public List<CandidateRoute> search(String from, String to, Instant earliestDeparture, Instant latestDeparture) {
        List<CandidateRoute> routes = new ArrayList<>();
        for (FlightLeg first : instances(from, null, earliestDeparture, latestDeparture)) {
            if (first.arrAirport.equals(to)) {
                routes.add(route(first));
                continue;
            }
            Instant landed = first.arrival();
            for (FlightLeg second : instances(first.arrAirport, to, landed, landed.plus(maxLayover))) {
                routes.add(route(first, second));
            }
        }
        return routes;
    }

    @Override
    public String name() {
        return "SampleScheduleProvider (SAMPLE DATA: " + SCHEDULE_FILE + ")";
    }

    /** Every instance of a flight from {@code from} (to {@code to}, if given) leaving between the two moments. */
    private List<FlightLeg> instances(String from, String to, Instant notBefore, Instant notAfter) {
        List<FlightLeg> legs = new ArrayList<>();
        for (SampleFlight flight : flights) {
            if (!flight.from().equals(from) || (to != null && !flight.to().equals(to))) {
                continue;
            }
            ZoneId fromZone = zoneOf(flight.from(), flight.flightNo());
            ZoneId toZone = zoneOf(flight.to(), flight.flightNo());
            LocalDate firstDay = notBefore.atZone(fromZone).toLocalDate().minusDays(1);
            LocalDate lastDay = notAfter.atZone(fromZone).toLocalDate();
            for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
                Instant departs = ZonedDateTime.of(day, flight.departs(), fromZone).toInstant();
                if (departs.isBefore(notBefore) || departs.isAfter(notAfter)) {
                    continue;
                }
                FlightLeg leg = new FlightLeg();
                leg.flightNo = flight.flightNo();
                leg.depAirport = flight.from();
                leg.arrAirport = flight.to();
                leg.depTerminal = flight.depTerminal();
                leg.arrTerminal = flight.arrTerminal();
                leg.depZone = fromZone;
                leg.arrZone = toZone;
                leg.scheduledDep = departs;
                leg.scheduledArr = ZonedDateTime.of(day.plusDays(flight.arrivalDayOffset()), flight.arrives(), toZone)
                        .toInstant();
                leg.phase = FlightPhase.SCHEDULED;
                legs.add(leg);
            }
        }
        return legs;
    }

    private CandidateRoute route(FlightLeg... legs) {
        CandidateRoute route = new CandidateRoute();
        Integer fare = 0;
        StringBuilder rules = new StringBuilder();
        for (FlightLeg leg : legs) {
            route.legs.add(leg);
            SampleFlight flight = flight(leg.flightNo);
            fare = fare == null || flight.fareInr() == null ? null : fare + flight.fareInr();
            if (flight.fareRules() != null) {
                if (rules.length() > 0) {
                    rules.append(" / ");
                }
                rules.append(leg.flightNo).append(": ").append(flight.fareRules());
            }
        }
        route.fareInr = fare;
        route.fareRules = rules.length() == 0 ? null : rules.toString();
        route.seats = SeatAvailability.UNKNOWN; // a timetable knows nothing about seats
        route.source = OptionSource.SAMPLE;
        return route;
    }

    private SampleFlight flight(String flightNo) {
        for (SampleFlight flight : flights) {
            if (flight.flightNo().equals(flightNo)) {
                return flight;
            }
        }
        throw new IllegalStateException("No SAMPLE flight " + flightNo);
    }

    private ZoneId zoneOf(String iata, String flightNo) {
        AirportPack airport = rules.find(iata);
        if (airport == null) {
            throw new IllegalStateException(SCHEDULE_FILE + ": flight " + flightNo + " uses airport " + iata
                    + ", which is not in mock-data/recovery/airport-rules.json (its time zone is unknown)");
        }
        return airport.zone();
    }

    /** The file's shape. */
    @JsonIgnoreProperties("about")
    record ScheduleFile(String lastChecked, List<SampleFlight> flights) {
    }

    /** One daily SAMPLE flight: local departure at {@code from}, local arrival at {@code to}. */
    record SampleFlight(String flightNo, String from, String to, LocalTime departs, LocalTime arrives,
                        int arrivalDayOffset, String depTerminal, String arrTerminal, Integer fareInr,
                        String fareRules) {
    }
}
