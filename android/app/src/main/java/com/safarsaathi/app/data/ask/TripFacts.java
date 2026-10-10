package com.safarsaathi.app.data.ask;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.app.ui.trip.TripOverview;
import com.safarsaathi.routing.map.AirportMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything a Quick answer may use, taken from the phone's own saved data at the moment of the question: the trip
 * from Room (the same copy the Home tab shows), the terminal map, and later the gate and bag records. A snapshot, so
 * one answer never mixes two versions of the data.
 *
 * <p>GOLDEN RULE: these are the ONLY facts an answer can contain. Anything missing here is "not announced yet" or
 * "not on this phone", never a guess.
 *
 * <ul>
 *   <li>{@link #gate}: gate and boarding times. Null until the gate data source exists (phase G4). Home says "Not
 *       announced yet" for the same reason.</li>
 *   <li>{@link #bag}: the registered bag and its belt. Null until Baggage Shield saves bags (phase B1).</li>
 *   <li>Travel documents: deliberately NOT here. They stay locked in the Document Vault (see QuickAnswerer).</li>
 * </ul>
 */
public final class TripFacts {

    /** One flight from the saved trip. */
    public static final class Flight {
        /** The block itself (its id tells "is this the next step?"). */
        public final BlockEntity block;
        /** "IJ 512", read from the block title; the whole title when it has no flight number. */
        public final String name;
        /** Where it leaves from, e.g. "Mumbai Airport T2 (BOM)"; null when the trip does not say. */
        @Nullable
        public final String from;
        public final Instant departs;
        public final Instant arrives;
        /** The originally planned departure, when the phone knows it (see PlannedTimes); else null. */
        @Nullable
        public final Instant plannedDeparts;
        /** OK, DELAYED (OK but later than planned), AUTO_ADJUSTED, NEEDS_USER, DONE or CANCELLED. */
        public final String status;

        Flight(BlockEntity block) {
            this.block = block;
            this.name = flightName(block.title);
            this.from = block.locationName == null || block.locationName.trim().isEmpty() ? null : block.locationName;
            this.departs = block.startTime;
            this.arrives = block.endTime;
            this.plannedDeparts = block.plannedStartTime;
            this.status = TripOverview.displayStatus(block);
        }
    }

    /** Gate and boarding data for the flight (phase G4). Fields may be null: not announced. */
    public static final class GateInfo {
        @Nullable
        public final String gate;
        /** The gate before the latest change; null when it never changed. */
        @Nullable
        public final String previousGate;
        @Nullable
        public final Instant boardingStart;
        @Nullable
        public final Instant boardingClose;
        /** True when the boarding times are the app's estimate, false when the airline gave them. */
        public final boolean boardingEstimate;
        /** When this gate data was last checked; null = not known. */
        @Nullable
        public final Instant asOf;

        public GateInfo(@Nullable String gate, @Nullable String previousGate, @Nullable Instant boardingStart,
                        @Nullable Instant boardingClose, boolean boardingEstimate, @Nullable Instant asOf) {
            this.gate = gate;
            this.previousGate = previousGate;
            this.boardingStart = boardingStart;
            this.boardingClose = boardingClose;
            this.boardingEstimate = boardingEstimate;
            this.asOf = asOf;
        }
    }

    /** The traveller's registered bag (phase B1). */
    public static final class BagInfo {
        /** Last 4 digits of the bag tag, enough for the traveller to recognise it. */
        public final String tagEnd;
        /** REGISTERED, DROPPED, LOADED, ARRIVED, ON_BELT, COLLECTED, DELAYED, DAMAGED or MISSING (CLAUDE.md §5). */
        public final String status;
        /** Null = not announced. */
        @Nullable
        public final String belt;
        @Nullable
        public final Instant asOf;

        public BagInfo(String tagEnd, String status, @Nullable String belt, @Nullable Instant asOf) {
            this.tagEnd = tagEnd;
            this.status = status;
            this.belt = belt;
            this.asOf = asOf;
        }
    }

    /** "Flight IJ 512 Mumbai to Bengaluru" -> "IJ 512": two letters/digits (airline) + up to 4 digits. */
    private static final Pattern FLIGHT_NUMBER =
            Pattern.compile("\\b([A-Z][A-Z0-9]|[0-9][A-Z])\\s?([0-9]{1,4})\\b");

    public final Instant now;
    /** True when a copy of the trip is saved on the phone. */
    public final boolean tripSaved;
    /** When the phone last heard from the server about the trip; null = never. Shown as "as of HH:MM". */
    @Nullable
    public final Instant asOf;
    /** The trip's first flight (the one the Home card shows); null when it has none. */
    @Nullable
    public final Flight flight;
    /** The next flight after {@link #flight} (a connection); null when there is none. */
    @Nullable
    public final Flight connection;
    /** The next block that is not done or cancelled and has not ended yet (as on Home); null when none. */
    @Nullable
    public final BlockEntity nextUp;
    @Nullable
    public final GateInfo gate;
    @Nullable
    public final BagInfo bag;
    /** The pilot terminal's walking map (washrooms, help desks, lifts); null when not on the phone. */
    @Nullable
    public final AirportMap map;

    private TripFacts(Instant now, boolean tripSaved, @Nullable Instant asOf, @Nullable Flight flight,
                      @Nullable Flight connection, @Nullable BlockEntity nextUp, @Nullable GateInfo gate,
                      @Nullable BagInfo bag, @Nullable AirportMap map) {
        this.now = now;
        this.tripSaved = tripSaved;
        this.asOf = asOf;
        this.flight = flight;
        this.connection = connection;
        this.nextUp = nextUp;
        this.gate = gate;
        this.bag = bag;
        this.map = map;
    }

    /**
     * The facts from the saved trip and map.
     *
     * @param trip   the saved trip, or null when the phone has none
     * @param blocks its blocks (any order; empty when none)
     * @param map    the terminal map, or null
     */
    public static TripFacts from(@Nullable TripEntity trip, List<BlockEntity> blocks, @Nullable AirportMap map,
                                 Instant now) {
        List<BlockEntity> sorted = trip == null ? Collections.<BlockEntity>emptyList() : new ArrayList<>(blocks);
        TripOverview overview = TripOverview.of(sorted, now);
        Flight flight = overview.flight == null ? null : new Flight(overview.flight);
        Flight connection = null;
        if (flight != null) {
            BlockEntity next = null;
            for (BlockEntity block : sorted) {
                boolean later = block.startTime.isAfter(flight.departs);
                if (BlockEntity.TYPE_FLIGHT.equals(block.type) && block.id != flight.block.id && later
                        && (next == null || block.startTime.isBefore(next.startTime))) {
                    next = block;
                }
            }
            connection = next == null ? null : new Flight(next);
        }
        return new TripFacts(now, trip != null, trip == null ? null : trip.lastSyncedAt, flight, connection,
                overview.nextUp, null, null, map);
    }

    /** The same facts with gate data (phase G4 fills this from its own table; tests use it directly). */
    public TripFacts withGate(@Nullable GateInfo newGate) {
        return new TripFacts(now, tripSaved, asOf, flight, connection, nextUp, newGate, bag, map);
    }

    /** The same facts with a registered bag (phase B1). */
    public TripFacts withBag(@Nullable BagInfo newBag) {
        return new TripFacts(now, tripSaved, asOf, flight, connection, nextUp, gate, newBag, map);
    }

    /** "IJ 512" from a block title, or the title itself when it has no flight number. */
    static String flightName(String title) {
        Matcher matcher = FLIGHT_NUMBER.matcher(title);
        return matcher.find() ? matcher.group(1) + " " + matcher.group(2) : title;
    }
}
