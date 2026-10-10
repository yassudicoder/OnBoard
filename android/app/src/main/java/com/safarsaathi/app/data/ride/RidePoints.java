package com.safarsaathi.app.data.ride;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Airport pickup / drop-off points and ride time estimates, read from mock-data/rides/ride-points.json (it ships
 * inside the app as the asset {@value #ASSET}, so it works offline and nothing is fetched). Plain Java (Gson),
 * checked by RidePointsTest.
 *
 * <p>Honesty rules (CLAUDE.md): a point without a {@code sourceUrl} is ignored; {@code verified: false} (or missing)
 * makes the place "approximate", which the screen says; every time here is an estimate and is labelled so.
 */
public final class RidePoints {

    /** Asset path (mock-data/rides is an assets folder, see app/build.gradle.kts). */
    public static final String ASSET = "ride-points.json";

    /** One pickup or drop-off point. Field names match the JSON; Gson fills them. */
    public static final class Point {
        public String id;
        public String airport;
        public String terminal;
        /** PICKUP or DROPOFF. */
        public String kind;
        public String name;
        /** For button texts, e.g. "Mumbai T2". */
        public String shortName;
        public String address;
        public Double lat;
        public Double lng;
        public String sourceUrl;
        public String lastChecked;
        public boolean verified;
        public String pointNote;

        public RidePlace toPlace() {
            return RidePlace.point(name, address, lat, lng, !verified);
        }

        /** The short name for buttons, else the full name. */
        public String buttonName() {
            return shortName != null && !shortName.trim().isEmpty() ? shortName : name;
        }
    }

    /** "Leave by" for a departure terminal: departure minus time at the airport minus the ride. All estimates. */
    public static final class LeaveByRule {
        public String airport;
        public String terminal;
        public int rideEstimateMin;
        public int atAirportBeforeDepartureMin;
        public String source;
        public String lastChecked;

        public Instant leaveBy(Instant departure) {
            return departure.minus(Duration.ofMinutes((long) rideEstimateMin + atAirportBeforeDepartureMin));
        }
    }

    /** A nearby airport or terminal an alternative flight could leave from, with the road transfer estimate. */
    public static final class Transfer {
        public String fromAirport;
        public String fromTerminal;
        public String toPointId;
        public int transferEstimateMin;
        public String source;
        public String lastChecked;
    }

    /** Result of {@link #nearby}: where to ride to, and about how long it takes (estimate). */
    public static final class NearbyRide {
        public final Point to;
        public final int estimateMinutes;

        NearbyRide(Point to, int estimateMinutes) {
            this.to = to;
            this.estimateMinutes = estimateMinutes;
        }
    }

    // The JSON file's top level.
    private List<Point> points;
    private List<LeaveByRule> leaveBy;
    private List<Transfer> nearbyAirports;

    /** Reads the JSON file's text. Broken JSON gives an empty set (logged by the caller), never a crash. */
    public static RidePoints parse(String json) {
        return parse(new StringReader(json));
    }

    /** Same, straight from a file or asset (the app reads the asset this way, see ServiceLocator). */
    public static RidePoints parse(Reader json) {
        RidePoints parsed;
        try {
            parsed = new Gson().fromJson(json, RidePoints.class);
        } catch (JsonParseException e) {
            parsed = null;
        }
        if (parsed == null) {
            return empty();
        }
        parsed.points = withSource(parsed.points);
        parsed.leaveBy = parsed.leaveBy == null ? Collections.emptyList() : parsed.leaveBy;
        parsed.nearbyAirports = parsed.nearbyAirports == null ? Collections.emptyList() : parsed.nearbyAirports;
        return parsed;
    }

    /** No points at all: every ride button then says what is missing. */
    public static RidePoints empty() {
        RidePoints none = new RidePoints();
        none.points = Collections.emptyList();
        none.leaveBy = Collections.emptyList();
        none.nearbyAirports = Collections.emptyList();
        return none;
    }

    /** The drop-off point of a terminal (e.g. BOM T2 departures), or null when the file has none. */
    @Nullable
    public Point dropoff(String airport, String terminal) {
        for (Point point : points) {
            if ("DROPOFF".equals(point.kind) && same(point.airport, airport) && same(point.terminal, terminal)) {
                return point;
            }
        }
        return null;
    }

    @Nullable
    public LeaveByRule leaveByRule(String airport, String terminal) {
        for (LeaveByRule rule : leaveBy) {
            if (same(rule.airport, airport) && same(rule.terminal, terminal)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * A ride for an alternative flight that leaves from somewhere else: only when the file lists that place as
     * nearby to where the traveller is. Same airport and terminal, or a place not in the file = null (no button).
     * Not shown on any screen yet: the Disruption Recovery ride button uses it once C3's alternatives exist.
     */
    @Nullable
    public NearbyRide nearby(String fromAirport, String fromTerminal, String toAirport, String toTerminal) {
        if (same(fromAirport, toAirport) && same(fromTerminal, toTerminal)) {
            return null;
        }
        for (Transfer transfer : nearbyAirports) {
            if (!same(transfer.fromAirport, fromAirport) || !same(transfer.fromTerminal, fromTerminal)) {
                continue;
            }
            Point to = point(transfer.toPointId);
            if (to != null && same(to.airport, toAirport) && same(to.terminal, toTerminal)) {
                return new NearbyRide(to, transfer.transferEstimateMin);
            }
        }
        return null;
    }

    @Nullable
    private Point point(String id) {
        for (Point point : points) {
            if (point.id != null && point.id.equals(id)) {
                return point;
            }
        }
        return null;
    }

    /** Keeps only points that say where they come from (CLAUDE.md: never invent coordinates). */
    private static List<Point> withSource(@Nullable List<Point> points) {
        List<Point> kept = new ArrayList<>();
        if (points != null) {
            for (Point point : points) {
                if (point != null && point.sourceUrl != null && !point.sourceUrl.trim().isEmpty()) {
                    kept.add(point);
                }
            }
        }
        return kept;
    }

    private static boolean same(@Nullable String a, @Nullable String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}
