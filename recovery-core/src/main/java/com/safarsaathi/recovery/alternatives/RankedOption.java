package com.safarsaathi.recovery.alternatives;

import java.util.ArrayList;
import java.util.List;

import com.safarsaathi.recovery.connection.ConnectionCheck;

/** One checked and ranked alternative, with everything the app shows about it. */
public final class RankedOption {

    public CandidateRoute route;
    /** 1 = best score. The recommended option is the best one whose connection times are all checked. */
    public int rank;
    public boolean recommended;

    /** One check per change of planes (empty for a direct flight), against the airport's minimum connection time. */
    public List<ConnectionCheck> connectionChecks = new ArrayList<>();
    /** Every change of planes has a minimum connection time (official or demo value). True for a direct flight. */
    public boolean connectionTimeChecked;
    /** "connection time not checked" when {@link #connectionTimeChecked} is false; otherwise null. */
    public String connectionLabel;
    /** A connection's verdict rests on a DEMO VALUE minimum. */
    public boolean basedOnDemoValue;

    public TransitVisaStatus transitVisaStatus;
    public String transitNote;
    public SeatAvailability seatAvailability;
    /** "Availability unknown" unless a provider verified seats. */
    public String seatNote;

    public boolean separateTickets;
    /** The separate-ticket risk in words; null for one ticket. */
    public String separateTicketWarning;
    /** Only for SEPARATE_TICKETS: the new ticket's (SAMPLE) fare in whole rupees; otherwise null. */
    public Integer fareDifferenceInr;
    public String fareNote;
    public String changeRules;

    /** Minutes later than the original arrival (0 = not later). */
    public int arrivalDelayMinutes;
    /** The ranking score in minutes (lower = better); see {@link AlternativeRanker}. */
    public int score;
    /** For the recommended option "why this one"; for the others a short summary. */
    public String reason;
}
