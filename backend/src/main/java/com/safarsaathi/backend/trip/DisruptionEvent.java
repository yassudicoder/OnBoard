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
 * One disruption the backend applied to a trip, e.g. "flight delayed by 90 minutes": one row of the
 * {@code disruption_events} table (db/migration/V2__create_disruption_events.sql).
 *
 * <p>{@code watcher.DisruptionService} saves one every time it runs the ripple engine. Like {@link Block}, it points
 * at its trip and block with plain id numbers, not objects.
 */
@Entity
@Table(name = "disruption_events")
public class DisruptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    /** The block that changed (e.g. the flight), not the blocks the ripple moved after it. */
    @Column(name = "block_id", nullable = false)
    private Long blockId;

    @Enumerated(EnumType.STRING) // stored by name, e.g. "DELAY"
    @Column(nullable = false, length = 16)
    private DisruptionKind kind;

    /** Plain English, e.g. "Delayed by 90 minutes (mock flight status)". At most 500 characters. */
    @Column(length = 500)
    private String detail;

    /** When the backend noticed the disruption (from the shared Clock). */
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    // Getters and setters: JPA reads and fills the fields; the rest of our code uses these methods.

    public Long getId() { return id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public DisruptionKind getKind() { return kind; }
    public void setKind(DisruptionKind kind) { this.kind = kind; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
}
