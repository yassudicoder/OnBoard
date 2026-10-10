package com.safarsaathi.backend.planb;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.safarsaathi.recovery.alternatives.Urgency;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.plan.RecoveryTrigger;

/**
 * The recovery plan of one connection: one row of {@code recovery_plans} (db/migration/V4__recovery_plans.sql).
 * {@link RecoveryPlanService} rebuilds it in the same transaction as every change of the trip; its alternatives are
 * {@link AlternativeOption} rows. The next steps and the Overnight section are stored as JSON, ready to send.
 */
@Entity
@Table(name = "recovery_plans")
public class RecoveryPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    /** false = the connection needs no plan any more (e.g. on track again); the row stays, the API answers 404. */
    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_kind", nullable = false, length = 32)
    private RecoveryTrigger trigger;

    @Column(name = "start_airport", nullable = false, length = 3)
    private String startAirport;

    @Column(nullable = false, length = 3)
    private String destination;

    @Column(name = "earliest_departure", nullable = false)
    private Instant earliestDeparture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 16)
    private TicketType ticketType;

    /** 1 for the first plan, + 1 on every rebuild. */
    @Column(nullable = false)
    private int version;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /** The connection's dataAsOf at build time: how fresh the flight data behind this plan is. */
    @Column(name = "based_on_data_at", nullable = false)
    private Instant basedOnDataAt;

    @Column(name = "recommended_option_id")
    private Long recommendedOptionId;

    @Column(name = "steps_json", nullable = false, length = 10000)
    private String stepsJson;

    @Column(name = "overnight_json", nullable = false, length = 10000)
    private String overnightJson;

    @Column(name = "where_to_go", nullable = false, length = 1000)
    private String whereToGo;

    // Getters and setters: JPA reads and fills the fields; the rest of our code uses these methods.

    public Long getId() { return id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public RecoveryTrigger getTrigger() { return trigger; }
    public void setTrigger(RecoveryTrigger trigger) { this.trigger = trigger; }

    public String getStartAirport() { return startAirport; }
    public void setStartAirport(String startAirport) { this.startAirport = startAirport; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public Instant getEarliestDeparture() { return earliestDeparture; }
    public void setEarliestDeparture(Instant earliestDeparture) { this.earliestDeparture = earliestDeparture; }

    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }

    public TicketType getTicketType() { return ticketType; }
    public void setTicketType(TicketType ticketType) { this.ticketType = ticketType; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public Instant getBasedOnDataAt() { return basedOnDataAt; }
    public void setBasedOnDataAt(Instant basedOnDataAt) { this.basedOnDataAt = basedOnDataAt; }

    public Long getRecommendedOptionId() { return recommendedOptionId; }
    public void setRecommendedOptionId(Long recommendedOptionId) { this.recommendedOptionId = recommendedOptionId; }

    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }

    public String getOvernightJson() { return overnightJson; }
    public void setOvernightJson(String overnightJson) { this.overnightJson = overnightJson; }

    public String getWhereToGo() { return whereToGo; }
    public void setWhereToGo(String whereToGo) { this.whereToGo = whereToGo; }
}
