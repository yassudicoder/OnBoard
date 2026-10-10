package com.safarsaathi.backend.trip;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One booking or event with a time, e.g. a flight, a cab pickup or a meeting: one row of the {@code blocks} table.
 *
 * <p>{@code tripId} is a plain number, not a {@code Trip} object (no {@code @ManyToOne}). Simpler: loading a block
 * never loads its trip behind your back, and it is the same shape as the app's Room table.
 * Times are {@link Instant}s (exact moments); the API turns them into Indian time, see {@link TripMapper}.
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
}
