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

import com.safarsaathi.recovery.alternatives.OptionSource;
import com.safarsaathi.recovery.alternatives.SeatAvailability;
import com.safarsaathi.recovery.alternatives.TransitVisaStatus;

/**
 * One checked alternative of a recovery plan (or one rejected route, with the reason): one row of
 * {@code alternative_options}. The main facts are columns; the full details the app shows (legs in local time,
 * connection checks, notes, reason) are {@code optionJson}, an {@link OptionResponse} without its id.
 */
@Entity
@Table(name = "alternative_options")
public class AlternativeOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 1 = best score; 0 for a rejected route. */
    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(nullable = false)
    private boolean recommended;

    @Column(nullable = false)
    private boolean rejected;

    @Column(name = "route_key", nullable = false, length = 300)
    private String routeKey;

    @Column(nullable = false)
    private Instant departs;

    @Column(nullable = false)
    private Instant arrives;

    @Column(nullable = false)
    private int stops;

    @Column(name = "connection_time_checked", nullable = false)
    private boolean connectionTimeChecked;

    @Enumerated(EnumType.STRING)
    @Column(name = "transit_visa_status", nullable = false, length = 16)
    private TransitVisaStatus transitVisaStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_availability", nullable = false, length = 16)
    private SeatAvailability seatAvailability;

    @Column(name = "fare_difference_inr")
    private Integer fareDifferenceInr;

    @Column(name = "separate_tickets", nullable = false)
    private boolean separateTickets;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OptionSource source;

    @Column(name = "option_json", nullable = false, length = 20000)
    private String optionJson;

    // Getters and setters: JPA reads and fills the fields; the rest of our code uses these methods.

    public Long getId() { return id; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public int getRankOrder() { return rankOrder; }
    public void setRankOrder(int rankOrder) { this.rankOrder = rankOrder; }

    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }

    public boolean isRejected() { return rejected; }
    public void setRejected(boolean rejected) { this.rejected = rejected; }

    public String getRouteKey() { return routeKey; }
    public void setRouteKey(String routeKey) { this.routeKey = routeKey; }

    public Instant getDeparts() { return departs; }
    public void setDeparts(Instant departs) { this.departs = departs; }

    public Instant getArrives() { return arrives; }
    public void setArrives(Instant arrives) { this.arrives = arrives; }

    public int getStops() { return stops; }
    public void setStops(int stops) { this.stops = stops; }

    public boolean isConnectionTimeChecked() { return connectionTimeChecked; }
    public void setConnectionTimeChecked(boolean connectionTimeChecked) { this.connectionTimeChecked = connectionTimeChecked; }

    public TransitVisaStatus getTransitVisaStatus() { return transitVisaStatus; }
    public void setTransitVisaStatus(TransitVisaStatus transitVisaStatus) { this.transitVisaStatus = transitVisaStatus; }

    public SeatAvailability getSeatAvailability() { return seatAvailability; }
    public void setSeatAvailability(SeatAvailability seatAvailability) { this.seatAvailability = seatAvailability; }

    public Integer getFareDifferenceInr() { return fareDifferenceInr; }
    public void setFareDifferenceInr(Integer fareDifferenceInr) { this.fareDifferenceInr = fareDifferenceInr; }

    public boolean isSeparateTickets() { return separateTickets; }
    public void setSeparateTickets(boolean separateTickets) { this.separateTickets = separateTickets; }

    public OptionSource getSource() { return source; }
    public void setSource(OptionSource source) { this.source = source; }

    public String getOptionJson() { return optionJson; }
    public void setOptionJson(String optionJson) { this.optionJson = optionJson; }
}
