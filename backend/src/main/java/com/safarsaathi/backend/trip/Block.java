package com.safarsaathi.backend.trip;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.safarsaathi.recovery.connection.FlightPhase;

/**
 * One booking or event with a time, e.g. a flight, a cab pickup or a meeting: one row of the {@code blocks} table.
 *
 * <p>{@code tripId} is a plain number, not a {@code Trip} object (no {@code @ManyToOne}). Simpler: loading a block
 * never loads its trip behind your back, and it is the same shape as the app's Room table.
 * Times are {@link Instant}s (exact moments) plus the time zone of the place they belong to ({@code startZone},
 * {@code endZone}; for a flight: the departure and the arrival airport). Together they give the local time
 * ({@link #getLocalStart()}), which is how the API shows them, see {@link TripMapper}.
 *
 * <p>{@code startTime} / {@code endTime} are the plan as it is now (a flight's latest estimate after a delay);
 * {@code scheduledStart} / {@code scheduledEnd} are the original timetable and never change.
 */
@Entity
@Table(name = "blocks")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING) // stored by name, e.g. "FLIGHT"
    @Column(nullable = false, length = 32)
    private BlockType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "location_name", length = 200)
    private String locationName;

    /** Latitude and longitude of the place; null when unknown. */
    private Double lat;
    private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BlockProvider provider;

    /** The booking reference at the provider, e.g. a PNR; null when nothing is booked. */
    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Flex flex;

    /** true = the block the traveller must not miss (the reason for the trip, e.g. the client meeting). */
    @Column(nullable = false)
    private boolean anchor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BlockStatus status;

    /**
     * Until when a change is free, in minutes from the block's start: -30 = until 30 minutes before it starts,
     * 0 = until it starts. null = never free (the change fee always applies).
     */
    @Column(name = "free_change_until_minutes")
    private Integer freeChangeUntilMinutes;

    /** Fee for a change outside the free window, in whole rupees. */
    @Column(name = "change_fee_inr", nullable = false)
    private int changeFeeInr;

    @Column(length = 1000)
    private String notes;

    /** Time zone of the start, e.g. "Asia/Kolkata"; for a flight the departure airport's zone. */
    @Column(name = "start_zone", nullable = false, length = 64)
    private String startZone;

    /** Time zone of the end; for a flight the arrival airport's zone, e.g. "Asia/Qatar". */
    @Column(name = "end_zone", nullable = false, length = 64)
    private String endZone;

    /** The original timetable / plan. Set once when the block is created, never changed (unlike startTime). */
    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    // ---- Flight fields: null on every block that is not a FLIGHT. ----

    /** e.g. "IJ 1907". */
    @Column(name = "flight_no", length = 16)
    private String flightNo;

    /** Three-letter airport codes, e.g. "DEL" and "DOH". */
    @Column(name = "dep_airport", length = 3)
    private String depAirport;

    @Column(name = "arr_airport", length = 3)
    private String arrAirport;

    /** e.g. "T3"; null = not known. */
    @Column(name = "dep_terminal", length = 8)
    private String depTerminal;

    @Column(name = "arr_terminal", length = 8)
    private String arrTerminal;

    /** Where the flight is in its day (SCHEDULED until flight data or a demo button says otherwise). */
    @Enumerated(EnumType.STRING)
    @Column(name = "flight_phase", length = 16)
    private FlightPhase flightPhase;

    // Getters and setters: JPA reads and fills the fields; the rest of our code uses these methods.

    public Long getId() { return id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public BlockType getType() { return type; }
    public void setType(BlockType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public BlockProvider getProvider() { return provider; }
    public void setProvider(BlockProvider provider) { this.provider = provider; }

    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }

    public Flex getFlex() { return flex; }
    public void setFlex(Flex flex) { this.flex = flex; }

    public boolean isAnchor() { return anchor; }
    public void setAnchor(boolean anchor) { this.anchor = anchor; }

    public BlockStatus getStatus() { return status; }
    public void setStatus(BlockStatus status) { this.status = status; }

    public Integer getFreeChangeUntilMinutes() { return freeChangeUntilMinutes; }
    public void setFreeChangeUntilMinutes(Integer freeChangeUntilMinutes) { this.freeChangeUntilMinutes = freeChangeUntilMinutes; }

    public int getChangeFeeInr() { return changeFeeInr; }
    public void setChangeFeeInr(int changeFeeInr) { this.changeFeeInr = changeFeeInr; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStartZone() { return startZone; }
    public void setStartZone(String startZone) { this.startZone = startZone; }

    public String getEndZone() { return endZone; }
    public void setEndZone(String endZone) { this.endZone = endZone; }

    public Instant getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(Instant scheduledStart) { this.scheduledStart = scheduledStart; }

    public Instant getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(Instant scheduledEnd) { this.scheduledEnd = scheduledEnd; }

    public String getFlightNo() { return flightNo; }
    public void setFlightNo(String flightNo) { this.flightNo = flightNo; }

    public String getDepAirport() { return depAirport; }
    public void setDepAirport(String depAirport) { this.depAirport = depAirport; }

    public String getArrAirport() { return arrAirport; }
    public void setArrAirport(String arrAirport) { this.arrAirport = arrAirport; }

    public String getDepTerminal() { return depTerminal; }
    public void setDepTerminal(String depTerminal) { this.depTerminal = depTerminal; }

    public String getArrTerminal() { return arrTerminal; }
    public void setArrTerminal(String arrTerminal) { this.arrTerminal = arrTerminal; }

    public FlightPhase getFlightPhase() { return flightPhase; }
    public void setFlightPhase(FlightPhase flightPhase) { this.flightPhase = flightPhase; }

    /** The start in its own local time, e.g. 17:50 in Delhi. */
    public ZonedDateTime getLocalStart() { return startTime.atZone(ZoneId.of(startZone)); }

    /** The end in its own local time, e.g. 19:35 in Doha. */
    public ZonedDateTime getLocalEnd() { return endTime.atZone(ZoneId.of(endZone)); }
}
