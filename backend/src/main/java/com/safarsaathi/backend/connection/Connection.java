package com.safarsaathi.backend.connection;

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
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.rules.ConnectionType;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * One change of planes in a trip (flight 1 lands, flight 2 leaves from the same airport): one row of the
 * {@code connections} table (db/migration/V3__flight_fields_and_connections.sql).
 *
 * <p>Two kinds of columns: the facts we keep ({@code ticketType}, {@code missConfirmedAt}) and the latest result of
 * the connection checker (status, minutes, sources, reason), which {@link ConnectionService} overwrites on every check.
 * Like the trip entities, it points at its trip and blocks with plain id numbers.
 */
@Entity
@Table(name = "connections")
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    /** Flight 1, the one that lands. */
    @Column(name = "inbound_block_id", nullable = false)
    private Long inboundBlockId;

    /** Flight 2, the one that leaves. */
    @Column(name = "outbound_block_id", nullable = false)
    private Long outboundBlockId;

    @Column(nullable = false, length = 3)
    private String airport;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", length = 16)
    private ConnectionType connectionType;

    @Column(name = "terminal_change")
    private Boolean terminalChange;

    @Column(name = "mct_minutes")
    private Integer mctMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mct_kind", length = 16)
    private ValueKind mctKind;

    @Column(name = "mct_source", length = 300)
    private String mctSource;

    @Column(name = "mct_source_url", length = 300)
    private String mctSourceUrl;

    @Column(name = "mct_last_checked", length = 10)
    private String mctLastChecked;

    @Column(name = "transfer_minutes", nullable = false)
    private int transferMinutes;

    @Column(name = "transfer_source", nullable = false, length = 300)
    private String transferSource;

    @Column(name = "gate_close_minutes", nullable = false)
    private int gateCloseMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_close_kind", nullable = false, length = 16)
    private ValueKind gateCloseKind;

    @Column(name = "gate_close_source", nullable = false, length = 300)
    private String gateCloseSource;

    @Column(name = "gate_close_source_url", length = 300)
    private String gateCloseSourceUrl;

    @Column(name = "gate_close_last_checked", length = 10)
    private String gateCloseLastChecked;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 16)
    private TicketType ticketType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConnectionStatus status;

    @Column(name = "available_minutes", nullable = false)
    private int availableMinutes;

    @Column(name = "projected_slack_minutes")
    private Integer projectedSlackMinutes;

    @Column(name = "based_on_demo_value", nullable = false)
    private boolean basedOnDemoValue;

    @Column(nullable = false, length = 1000)
    private String reason;

    /** When the miss was first confirmed; stays set (see ConnectionFacts#missConfirmed). Null = not confirmed. */
    @Column(name = "miss_confirmed_at")
    private Instant missConfirmedAt;

    /** When this row was last checked. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** When the flight data behind the check arrived (the "as of" time the app shows). */
    @Column(name = "data_as_of", nullable = false)
    private Instant dataAsOf;

    /** The traveller's choice for ranking alternatives (V4). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Urgency urgency = Urgency.NORMAL;

    /** The flight the airline rebooked the traveller on (confirmed by airline data or the traveller); null = none. */
    @Column(name = "rebooked_flight", length = 16)
    private String rebookedFlight;

    /** Its departure, when known. */
    @Column(name = "rebooked_departure")
    private Instant rebookedDeparture;

    /** When the traveller marked the connection as sorted (made it, or has a new plan); null = not yet. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // Getters and setters: JPA reads and fills the fields; the rest of our code uses these methods.

    public Long getId() { return id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Long getInboundBlockId() { return inboundBlockId; }
    public void setInboundBlockId(Long inboundBlockId) { this.inboundBlockId = inboundBlockId; }

    public Long getOutboundBlockId() { return outboundBlockId; }
    public void setOutboundBlockId(Long outboundBlockId) { this.outboundBlockId = outboundBlockId; }

    public String getAirport() { return airport; }
    public void setAirport(String airport) { this.airport = airport; }

    public ConnectionType getConnectionType() { return connectionType; }
    public void setConnectionType(ConnectionType connectionType) { this.connectionType = connectionType; }

    public Boolean getTerminalChange() { return terminalChange; }
    public void setTerminalChange(Boolean terminalChange) { this.terminalChange = terminalChange; }

    public Integer getMctMinutes() { return mctMinutes; }
    public void setMctMinutes(Integer mctMinutes) { this.mctMinutes = mctMinutes; }

    public ValueKind getMctKind() { return mctKind; }
    public void setMctKind(ValueKind mctKind) { this.mctKind = mctKind; }

    public String getMctSource() { return mctSource; }
    public void setMctSource(String mctSource) { this.mctSource = mctSource; }

    public String getMctSourceUrl() { return mctSourceUrl; }
    public void setMctSourceUrl(String mctSourceUrl) { this.mctSourceUrl = mctSourceUrl; }

    public String getMctLastChecked() { return mctLastChecked; }
    public void setMctLastChecked(String mctLastChecked) { this.mctLastChecked = mctLastChecked; }

    public int getTransferMinutes() { return transferMinutes; }
    public void setTransferMinutes(int transferMinutes) { this.transferMinutes = transferMinutes; }

    public String getTransferSource() { return transferSource; }
    public void setTransferSource(String transferSource) { this.transferSource = transferSource; }

    public int getGateCloseMinutes() { return gateCloseMinutes; }
    public void setGateCloseMinutes(int gateCloseMinutes) { this.gateCloseMinutes = gateCloseMinutes; }

    public ValueKind getGateCloseKind() { return gateCloseKind; }
    public void setGateCloseKind(ValueKind gateCloseKind) { this.gateCloseKind = gateCloseKind; }

    public String getGateCloseSource() { return gateCloseSource; }
    public void setGateCloseSource(String gateCloseSource) { this.gateCloseSource = gateCloseSource; }

    public String getGateCloseSourceUrl() { return gateCloseSourceUrl; }
    public void setGateCloseSourceUrl(String gateCloseSourceUrl) { this.gateCloseSourceUrl = gateCloseSourceUrl; }

    public String getGateCloseLastChecked() { return gateCloseLastChecked; }
    public void setGateCloseLastChecked(String gateCloseLastChecked) { this.gateCloseLastChecked = gateCloseLastChecked; }

    public TicketType getTicketType() { return ticketType; }
    public void setTicketType(TicketType ticketType) { this.ticketType = ticketType; }

    public ConnectionStatus getStatus() { return status; }
    public void setStatus(ConnectionStatus status) { this.status = status; }

    public int getAvailableMinutes() { return availableMinutes; }
    public void setAvailableMinutes(int availableMinutes) { this.availableMinutes = availableMinutes; }

    public Integer getProjectedSlackMinutes() { return projectedSlackMinutes; }
    public void setProjectedSlackMinutes(Integer projectedSlackMinutes) { this.projectedSlackMinutes = projectedSlackMinutes; }

    public boolean isBasedOnDemoValue() { return basedOnDemoValue; }
    public void setBasedOnDemoValue(boolean basedOnDemoValue) { this.basedOnDemoValue = basedOnDemoValue; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getMissConfirmedAt() { return missConfirmedAt; }
    public void setMissConfirmedAt(Instant missConfirmedAt) { this.missConfirmedAt = missConfirmedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDataAsOf() { return dataAsOf; }
    public void setDataAsOf(Instant dataAsOf) { this.dataAsOf = dataAsOf; }

    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }

    public String getRebookedFlight() { return rebookedFlight; }
    public void setRebookedFlight(String rebookedFlight) { this.rebookedFlight = rebookedFlight; }

    public Instant getRebookedDeparture() { return rebookedDeparture; }
    public void setRebookedDeparture(Instant rebookedDeparture) { this.rebookedDeparture = rebookedDeparture; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
