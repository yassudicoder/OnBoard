package com.safarsaathi.backend.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * "Block {@code toBlockId} may start at the earliest {@code minGapMinutes + travelMinutes} after block
 * {@code fromBlockId} ends": one row of the {@code links} table. The ripple engine (Phase 2) follows these links
 * to find what breaks when a block moves.
 *
 * <p>Like {@link Block}, it points at its trip and blocks with plain id numbers, not objects.
 */
@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "from_block_id", nullable = false)
    private Long fromBlockId;

    @Column(name = "to_block_id", nullable = false)
    private Long toBlockId;

    /** Extra minutes needed between the two blocks, e.g. 75 for a minimum flight connection time. */
    @Column(name = "min_gap_minutes", nullable = false)
    private int minGapMinutes;

    /** Minutes to get from the first block's place to the second one's. */
    @Column(name = "travel_minutes", nullable = false)
    private int travelMinutes;

    @Enumerated(EnumType.STRING) // stored by name, e.g. "CAB"
    @Column(name = "travel_mode", nullable = false, length = 16)
    private TravelMode travelMode;

    // Getters and setters: JPA reads and fills the fields; the rest of our code uses these methods.

    public Long getId() { return id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Long getFromBlockId() { return fromBlockId; }
    public void setFromBlockId(Long fromBlockId) { this.fromBlockId = fromBlockId; }

    public Long getToBlockId() { return toBlockId; }
    public void setToBlockId(Long toBlockId) { this.toBlockId = toBlockId; }

    public int getMinGapMinutes() { return minGapMinutes; }
    public void setMinGapMinutes(int minGapMinutes) { this.minGapMinutes = minGapMinutes; }

    public int getTravelMinutes() { return travelMinutes; }
    public void setTravelMinutes(int travelMinutes) { this.travelMinutes = travelMinutes; }

    public TravelMode getTravelMode() { return travelMode; }
    public void setTravelMode(TravelMode travelMode) { this.travelMode = travelMode; }
}
