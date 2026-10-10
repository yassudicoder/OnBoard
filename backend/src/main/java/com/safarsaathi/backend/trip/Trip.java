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
 * One trip, e.g. "Client visit: Mumbai to Bengaluru": one row of the {@code trips} table.
 *
 * <p>{@code @Entity} tells JPA (Hibernate does the work) to store objects of this class as table rows, one field
 * per column. The table itself comes from Flyway ({@code db/migration/V1__create_trips_blocks_links.sql}); at
 * startup Hibernate only checks that these fields match it.
 *
 * <p>The trip's blocks and links are not fields here. They are separate rows that point back to the trip by
 * {@code tripId}, and {@link TripService} loads them by that id. That keeps these classes simple and matches the
 * Room tables in the Android app.
 */
@Entity
@Table(name = "trips")
public class Trip {

    /** Handed out by the database when the trip is first saved: 1, 2, 3, ... */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    // EnumType.STRING stores the value's name ("BUSINESS"), not its position in the enum (0), which would
    // silently change meaning if someone reordered the values.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Purpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Persona persona;

    /** The traveller's language as a short code: en, hi, mr, ta or bn. */
    @Column(nullable = false, length = 8)
    private String language;

    @Column(nullable = false)
    private int travellers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TripStatus status;

    /**
     * The plan's version number: 1 when the trip is created, and our own code adds 1 on every plan change
     * (from Phase 2). The app overwrites its offline copy only when the server's number is higher.
     * A plain number on purpose: JPA's {@code @Version} would also count changes that are not plan changes.
     */
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Getters and setters: JPA reads and fills the fields; the rest of our code uses these methods.

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }

    public Persona getPersona() { return persona; }
    public void setPersona(Persona persona) { this.persona = persona; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public int getTravellers() { return travellers; }
    public void setTravellers(int travellers) { this.travellers = travellers; }

    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
