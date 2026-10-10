package com.safarsaathi.recovery.connection;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import com.safarsaathi.recovery.rules.AirportPack;
import com.safarsaathi.recovery.rules.AirportRules;
import com.safarsaathi.recovery.rules.ConnectionRule;
import com.safarsaathi.recovery.rules.ConnectionType;
import com.safarsaathi.recovery.rules.SourcedMinutes;
import com.safarsaathi.recovery.rules.TransferEstimates;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * The connection checker: will the traveller make it from flight 1 to flight 2? Plain Java, shared by the backend
 * and (from C3) the app, so both give exactly the same answer, also offline.
 *
 * <p>Three numbers decide it:
 * <ol>
 *   <li><b>Available time</b>: from flight 1's landing (its latest estimate, else the timetable) to flight 2's
 *       departure.</li>
 *   <li><b>Minimum connection time (MCT)</b> from the airport rules file, by airport and connection type (and terminal
 *       change). It is the official minimum, or a clearly labelled demo value. No figure = UNKNOWN, never a guess.</li>
 *   <li><b>Practical time</b>: what reaching the gate before it closes takes = taxiing + getting off the plane +
 *       transfer security (team estimates) + how long before departure the gate closes.</li>
 * </ol>
 * Then, in this order (the first that applies wins):
 * <pre>
 *   RESOLVED / REBOOKED_BY_AIRLINE   confirmed facts the caller keeps (ConnectionFacts)
 *   CANCELLED                        flight 1 or flight 2 is cancelled
 *   CONFIRMED_MISSED                 flight 2 has left while flight 1 had not landed yet, or confirmed earlier
 *   LIKELY_MISSED                    flight 2 leaves before flight 1 lands (no MCT needed to know that)
 *   UNKNOWN                          no MCT for this airport and connection type
 *   LIKELY_MISSED                    available &lt; MCT
 *   AT_RISK                          available &lt; max(MCT, practical) + margin
 *   OK                               otherwise
 * </pre>
 * Only the official (or demo) MCT can make a connection "likely missed". Our own estimates (taxiing, security) can
 * only add a warning: AT_RISK.
 */
public final class ConnectionChecker {

    private final AirportRules rules;

    public ConnectionChecker(AirportRules rules) {
        if (rules == null || rules.defaults == null || rules.defaults.transfer == null
                || rules.defaults.gateClose == null) {
            throw new IllegalArgumentException("The airport rules need defaults for transfer and gateClose");
        }
        this.rules = rules;
    }

    /** {@link #check(FlightLeg, FlightLeg, ConnectionFacts, String)} with the reason in English. */
    public ConnectionCheck check(FlightLeg inbound, FlightLeg outbound, ConnectionFacts facts) {
        return check(inbound, outbound, facts, "en");
    }

    /**
     * Checks the connection from {@code inbound} (flight 1) to {@code outbound} (flight 2).
     *
     * @param facts    confirmed facts kept by the caller; null = none
     * @param language "en", "hi" or "mr" for the reason (English when there is no text for the language yet)
     */
    public ConnectionCheck check(FlightLeg inbound, FlightLeg outbound, ConnectionFacts facts, String language) {
        if (inbound == null || outbound == null) {
            throw new IllegalArgumentException("Both flights are needed");
        }
        ConnectionFacts known = facts == null ? new ConnectionFacts() : facts;
        AirportPack hub = rules.find(inbound.arrAirport);
        ZoneId hubZone = inbound.arrZone != null ? inbound.arrZone : (hub != null ? hub.zone() : null);
        if (hubZone == null) {
            throw new IllegalArgumentException("No time zone for " + inbound.arrAirport
                    + ": set FlightLeg.arrZone or add the airport to the rules file");
        }

        ConnectionCheck check = new ConnectionCheck();
        check.airport = inbound.arrAirport;
        check.landsAt = inbound.arrival().atZone(hubZone);
        check.nextLeavesAt = outbound.departure().atZone(hubZone);
        check.availableMinutes = (int) Duration.between(inbound.arrival(), outbound.departure()).toMinutes();
        check.terminalChange = terminalChange(inbound.arrTerminal, outbound.depTerminal);
        check.connectionType = connectionType(inbound, outbound);
        fillMinimum(check, hub);
        fillPractical(check, hub);

        boolean sameAirport = inbound.arrAirport != null && inbound.arrAirport.equals(outbound.depAirport);
        check.status = decide(inbound, outbound, known, check, sameAirport);
        if (check.mctMinutes != null) {
            check.slackMinutes = check.availableMinutes - check.mctMinutes;
        }
        check.basedOnDemoValue = check.mctKind == ValueKind.DEMO_VALUE && reliesOnMinimum(check);
        check.reason = reason(inbound, outbound, known, check, hub, sameAirport, ConnectionText.forLanguage(language));
        return check;
    }

    /**
     * The minimum connection time for this pair of flights, or null when the rules file has none. The backend's
     * ripple engine uses it as the gap a flight-to-flight link needs, so its "missed" verdict matches this checker.
     */
    public Integer minimumConnectionMinutes(FlightLeg inbound, FlightLeg outbound) {
        ConnectionRule rule = strictestRule(rules.find(inbound.arrAirport), connectionType(inbound, outbound),
                terminalChange(inbound.arrTerminal, outbound.depTerminal));
        return rule == null ? null : rule.minutes;
    }

    // ---- The steps of check() ----

    private static ConnectionStatus decide(FlightLeg inbound, FlightLeg outbound, ConnectionFacts facts,
                                           ConnectionCheck check, boolean sameAirport) {
        if (facts.resolved) {
            return ConnectionStatus.RESOLVED;
        }
        if (facts.rebookedFlight != null) {
            return ConnectionStatus.REBOOKED_BY_AIRLINE;
        }
        if (inbound.cancelled || outbound.cancelled) {
            return ConnectionStatus.CANCELLED;
        }
        if (facts.missConfirmed || departedFirst(inbound, outbound)) {
            return ConnectionStatus.CONFIRMED_MISSED;
        }
        if (!sameAirport) {
            return ConnectionStatus.UNKNOWN;
        }
        if (check.availableMinutes < 0) {
            return ConnectionStatus.LIKELY_MISSED; // flight 2 leaves before flight 1 lands
        }
        if (check.mctMinutes == null) {
            return ConnectionStatus.UNKNOWN;
        }
        if (check.availableMinutes < check.mctMinutes) {
            return ConnectionStatus.LIKELY_MISSED;
        }
        int comfortable = Math.max(check.mctMinutes, check.practicalMinutes) + check.atRiskMarginMinutes;
        return check.availableMinutes < comfortable ? ConnectionStatus.AT_RISK : ConnectionStatus.OK;
    }

    /**
     * Flight 2 has left while flight 1 has not landed: the traveller was certainly not on it. If both have already
     * landed we can't see which came first, so the caller keeps an earlier confirmation in {@link ConnectionFacts}.
     */
    private static boolean departedFirst(FlightLeg inbound, FlightLeg outbound) {
        return outbound.phase != null && outbound.phase.hasDeparted() && inbound.phase != FlightPhase.LANDED;
    }

    /** True when the status came from comparing with the MCT (so a demo MCT must be mentioned). */
    private static boolean reliesOnMinimum(ConnectionCheck check) {
        boolean timingStatus = check.status == ConnectionStatus.OK || check.status == ConnectionStatus.AT_RISK
                || check.status == ConnectionStatus.LIKELY_MISSED;
        return timingStatus && check.availableMinutes >= 0;
    }

    private void fillMinimum(ConnectionCheck check, AirportPack hub) {
        ConnectionRule rule = strictestRule(hub, check.connectionType, check.terminalChange);
        if (rule == null) {
            return; // UNKNOWN: mctMinutes stays null
        }
        check.mctMinutes = rule.minutes;
        check.mctKind = rule.kind;
        check.mctSource = rule.source;
        check.mctSourceUrl = rule.sourceUrl;
        check.mctLastChecked = rule.lastChecked;
        check.mctAssumesTerminalChange = check.terminalChange == null && Boolean.TRUE.equals(rule.terminalChange);
    }

    private void fillPractical(ConnectionCheck check, AirportPack hub) {
        TransferEstimates transfer = hub != null && hub.transfer != null ? hub.transfer : rules.defaults.transfer;
        SourcedMinutes gateClose = hub != null && hub.gateClose != null ? hub.gateClose : rules.defaults.gateClose;
        check.transferMinutes = transfer.totalMinutes();
        check.transferKind = transfer.kind;
        check.transferSource = transfer.source;
        check.gateCloseMinutes = gateClose.minutes;
        check.gateCloseKind = gateClose.kind;
        check.gateCloseSource = gateClose.source;
        check.gateCloseSourceUrl = gateClose.sourceUrl;
        check.gateCloseLastChecked = gateClose.lastChecked;
        check.practicalMinutes = check.transferMinutes + check.gateCloseMinutes;
        check.atRiskMarginMinutes = rules.defaults.atRiskMarginMinutes;
    }

    /**
     * The rule with the most minutes among those that apply. When the terminals are not known, both a "same
     * terminal" and a "terminal change" rule may apply: we take the stricter one and say so.
     */
    private static ConnectionRule strictestRule(AirportPack hub, ConnectionType type, Boolean terminalChange) {
        if (hub == null || type == null) {
            return null;
        }
        ConnectionRule strictest = null;
        for (ConnectionRule rule : hub.connectionRules) {
            if (rule.appliesTo(type, terminalChange) && (strictest == null || rule.minutes > strictest.minutes)) {
                strictest = rule;
            }
        }
        return strictest;
    }

    /** Domestic or international on each side; null when an airport (and so its country) is not in the file. */
    private ConnectionType connectionType(FlightLeg inbound, FlightLeg outbound) {
        AirportPack from = rules.find(inbound.depAirport);
        AirportPack hub = rules.find(inbound.arrAirport);
        AirportPack to = rules.find(outbound.arrAirport);
        if (from == null || hub == null || to == null) {
            return null;
        }
        return ConnectionType.of(!from.country.equals(hub.country), !hub.country.equals(to.country));
    }

    private static Boolean terminalChange(String arrivalTerminal, String departureTerminal) {
        if (arrivalTerminal == null || departureTerminal == null) {
            return null;
        }
        return !arrivalTerminal.equals(departureTerminal);
    }

    // ---- The reason, in plain words ----

    private String reason(FlightLeg inbound, FlightLeg outbound, ConnectionFacts facts, ConnectionCheck check,
                          AirportPack hub, boolean sameAirport, ConnectionText text) {
        String language = text.language(); // city names follow the sentences (English if the language has none)
        String city = hub != null ? hub.cityName(language) : inbound.arrAirport;
        LocalDate landingDay = check.landsAt.toLocalDate();
        String lands = text.time(check.landsAt, landingDay);
        String leaves = text.time(check.nextLeavesAt, landingDay);
        String landsAndLeaves = text.sentence("lands_and_leaves", "city", city, "lands", lands, "leaves", leaves);
        String available = String.valueOf(check.availableMinutes);

        switch (check.status) {
            case RESOLVED:
                return text.sentence("resolved");
            case REBOOKED_BY_AIRLINE:
                return text.sentence("rebooked", "flight", facts.rebookedFlight);
            case CANCELLED:
                if (inbound.cancelled) {
                    return text.sentence("cancelled_inbound", "flight", inbound.flightNo,
                            "from", cityOf(inbound.depAirport, language), "city", city);
                }
                return text.sentence("cancelled_outbound", "flight", outbound.flightNo, "city", city,
                        "to", cityOf(outbound.arrAirport, language));
            case CONFIRMED_MISSED:
                if (outbound.phase != null && outbound.phase.hasDeparted()) {
                    return text.sentence("confirmed_missed_departed", "flight", outbound.flightNo, "city", city);
                }
                return text.sentence("confirmed_missed", "city", city);
            case UNKNOWN:
                if (!sameAirport) {
                    return text.sentence("other_airport", "nextAirport", outbound.depAirport,
                            "airport", inbound.arrAirport);
                }
                if (hub == null) {
                    return landsAndLeaves + " " + text.sentence("unknown_airport", "airport", inbound.arrAirport,
                            "available", available);
                }
                return landsAndLeaves + " " + text.sentence("unknown_mct", "city", city,
                        "type", text.typeName(check.connectionType), "available", available);
            default:
                break; // OK, AT_RISK, LIKELY_MISSED below
        }

        if (check.availableMinutes < 0) {
            return text.sentence("leaves_before_landing", "city", city, "leaves", leaves, "lands", lands);
        }
        StringBuilder reason = new StringBuilder(landsAndLeaves).append(' ')
                .append(text.sentence("needs_and_have", "city", city, "mct", String.valueOf(check.mctMinutes),
                        "mctLabel", text.label(check.mctKind), "available", available));
        if (check.mctAssumesTerminalChange) {
            reason.append(' ').append(text.sentence("terminals_assumed"));
        }
        if (check.status == ConnectionStatus.AT_RISK) {
            int spare = check.availableMinutes - Math.max(check.mctMinutes, check.practicalMinutes);
            String key = spare >= 0 ? "at_risk_spare" : "at_risk_late";
            reason.append(' ').append(text.sentence(key,
                    "spare", String.valueOf(spare), "late", String.valueOf(-spare),
                    "transfer", String.valueOf(check.transferMinutes), "transferLabel", text.label(check.transferKind),
                    "gateClose", String.valueOf(check.gateCloseMinutes),
                    "gateCloseLabel", text.label(check.gateCloseKind)));
        }
        return reason.toString();
    }

    private String cityOf(String iata, String language) {
        AirportPack airport = rules.find(iata);
        return airport != null ? airport.cityName(language) : iata;
    }
}
