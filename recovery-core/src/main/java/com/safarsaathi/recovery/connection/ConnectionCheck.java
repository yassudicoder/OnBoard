package com.safarsaathi.recovery.connection;

import java.time.ZonedDateTime;

import com.safarsaathi.recovery.rules.ConnectionType;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * What the {@link ConnectionChecker} found for one connection: the status, the numbers behind it (each with its
 * source) and one plain-language reason, e.g. "You land in Doha at 21:10, your next flight leaves at 21:55. Doha
 * needs at least 60 min (demo value). You have 45 min."
 */
public final class ConnectionCheck {

    public ConnectionStatus status;
    /** The airport where the traveller changes planes, e.g. "DOH". */
    public String airport;
    /** null = not known (an airport of the trip is missing from the rules file). */
    public ConnectionType connectionType;
    /** true / false, or null when a terminal is not known. */
    public Boolean terminalChange;

    /** Flight 1's (projected) landing, in the connection airport's local time. */
    public ZonedDateTime landsAt;
    /** Flight 2's (projected) departure, in the connection airport's local time. */
    public ZonedDateTime nextLeavesAt;
    /** Minutes from landing to the next departure; negative when flight 2 leaves first. */
    public int availableMinutes;

    /** The minimum connection time; null = UNKNOWN (no rule for this airport and connection type). */
    public Integer mctMinutes;
    public ValueKind mctKind;
    public String mctSource;
    public String mctSourceUrl;
    public String mctLastChecked;
    /** The terminals were not known, so the stricter terminal-change minimum was used. */
    public boolean mctAssumesTerminalChange;

    /** Taxiing + getting off the plane + transfer security (always an estimate). */
    public int transferMinutes;
    public ValueKind transferKind;
    public String transferSource;
    /** How long before departure the gate closes. */
    public int gateCloseMinutes;
    public ValueKind gateCloseKind;
    public String gateCloseSource;
    public String gateCloseSourceUrl;
    public String gateCloseLastChecked;
    /** transferMinutes + gateCloseMinutes: what getting to the gate in time takes in practice (an estimate). */
    public int practicalMinutes;
    /** Less than this many spare minutes counts as "at risk" (a product choice, see the rules file). */
    public int atRiskMarginMinutes;

    /** availableMinutes - mctMinutes; null when the MCT is unknown. Negative = minutes short. */
    public Integer slackMinutes;
    /**
     * The status rests on a DEMO VALUE minimum connection time: the app then says "based on a demo minimum
     * connection time".
     */
    public boolean basedOnDemoValue;
    /** The status, explained in plain words in the requested language, times in local airport time. */
    public String reason;

    public Certainty certainty() {
        return status.certainty();
    }
}
