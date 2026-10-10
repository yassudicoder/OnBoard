package com.safarsaathi.recovery.alternatives;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safarsaathi.recovery.connection.ConnectionCheck;
import com.safarsaathi.recovery.connection.ConnectionChecker;
import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.FlightLeg;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.plan.RecoverySettings;
import com.safarsaathi.recovery.rules.AirportPack;
import com.safarsaathi.recovery.rules.AirportRules;
import com.safarsaathi.recovery.text.Sentences;

/**
 * Checks and ranks alternative routes. Plain Java, shared by the backend and (from C3) the app, so switching between
 * NORMAL and URGENT works offline on the last downloaded options.
 *
 * <ol>
 *   <li><b>Check</b> every change of planes with the {@link ConnectionChecker} (the airport's minimum connection time,
 *       official or DEMO VALUE). A route that fails it (LIKELY_MISSED) is <b>rejected</b> and listed with the reason.
 *       A route through an airport with no minimum stays, labelled "connection time not checked".</li>
 *   <li><b>Score</b> each route in minutes, lower = better: minutes later than the original arrival + an extra
 *       change of planes × the urgency's stop penalty + extra cost × its cost weight ({@link RankingWeights}). Extra
 *       cost only counts for SEPARATE_TICKETS (a new ticket is needed); on one ticket the airline normally rebooks.
 *       Ties: fewer changes of planes, lower cost, the preferred airline, earlier departure.</li>
 *   <li><b>Recommend</b> the best-scored route whose connection times are all checked. A route with an unchecked
 *       connection time is never recommended while a checked one exists; if none is checked, the best one is
 *       recommended with a warning.</li>
 * </ol>
 * It never books anything: it only orders what a provider offered.
 */
public final class AlternativeRanker {

    private final ConnectionChecker checker;
    private final AirportRules rules;
    private final RecoverySettings settings;
    private final TransitRequirementProvider transit;

    public AlternativeRanker(AirportRules rules, RecoverySettings settings, TransitRequirementProvider transit) {
        if (rules == null || settings == null || transit == null) {
            throw new IllegalArgumentException("rules, settings and transit are all needed");
        }
        this.rules = rules;
        this.settings = settings;
        this.transit = transit;
        this.checker = new ConnectionChecker(rules);
    }

    public Ranking rank(List<CandidateRoute> routes, RankingRequest request) {
        RankingRequest req = request == null ? new RankingRequest() : request;
        Sentences text = Sentences.load(AlternativeRanker.class, "alternative_text", req.language);
        Ranking ranking = new Ranking();

        List<RankedOption> usable = new ArrayList<>();
        for (CandidateRoute route : routes) {
            List<ConnectionCheck> checks = checkConnections(route, req.language);
            ConnectionCheck failed = firstFailure(checks);
            if (failed != null) {
                ranking.rejected.add(rejected(route, failed, text));
            } else {
                usable.add(evaluate(route, checks, req, text));
            }
        }
        usable = bestPerFinalFlight(usable);
        Collections.sort(usable, order(req));
        for (int i = 0; i < usable.size(); i++) {
            usable.get(i).rank = i + 1;
        }
        ranking.ranked = usable;

        RankedOption recommended = null;
        for (RankedOption option : usable) {
            if (option.connectionTimeChecked) {
                recommended = option;
                break;
            }
        }
        boolean noneChecked = recommended == null && !usable.isEmpty();
        if (noneChecked) {
            recommended = usable.get(0);
        }
        if (recommended != null) {
            recommended.recommended = true;
            recommended.reason = whyThisOne(recommended, usable, text, noneChecked);
            ranking.recommended = recommended;
        }
        for (RankedOption option : usable) {
            if (option.recommended) {
                continue;
            }
            option.reason = otherReason(option, text);
            if (ranking.others.size() < settings.otherOptionsShown) {
                ranking.others.add(option);
            }
        }
        return ranking;
    }

    // ---- Checking ----

    private List<ConnectionCheck> checkConnections(CandidateRoute route, String language) {
        List<ConnectionCheck> checks = new ArrayList<>();
        for (int i = 0; i + 1 < route.legs.size(); i++) {
            checks.add(checker.check(route.legs.get(i), route.legs.get(i + 1), null, language));
        }
        return checks;
    }

    /** The first change of planes that does not work (shorter than the minimum, or the next flight leaves first). */
    private static ConnectionCheck firstFailure(List<ConnectionCheck> checks) {
        for (ConnectionCheck check : checks) {
            if (check.status == ConnectionStatus.LIKELY_MISSED) {
                return check;
            }
        }
        return null;
    }

    private Ranking.Rejected rejected(CandidateRoute route, ConnectionCheck failed, Sentences text) {
        Ranking.Rejected rejected = new Ranking.Rejected();
        rejected.route = route;
        rejected.failedCheck = failed;
        rejected.reason = text.get("rejected", "city", city(failed.airport, text), "checkReason", failed.reason);
        return rejected;
    }

    private RankedOption evaluate(CandidateRoute route, List<ConnectionCheck> checks, RankingRequest req,
                                  Sentences text) {
        RankedOption option = new RankedOption();
        option.route = route;
        option.connectionChecks = checks;
        option.connectionTimeChecked = true;
        for (ConnectionCheck check : checks) {
            if (check.mctMinutes == null) {
                option.connectionTimeChecked = false;
            }
            if (check.basedOnDemoValue) {
                option.basedOnDemoValue = true;
            }
        }
        option.connectionLabel = option.connectionTimeChecked ? null : text.get("connection_not_checked");

        option.separateTickets = route.separateTickets();
        option.separateTicketWarning = option.separateTickets ? text.get("separate_tickets") : null;
        option.fareDifferenceInr = req.ticketType == TicketType.SEPARATE_TICKETS ? route.fareInr : null;
        option.fareNote = option.fareDifferenceInr == null ? null : text.get("fare_new_ticket",
                "amount", text.amount(option.fareDifferenceInr), "fareLabel", fareLabel(route, text));
        option.changeRules = route.fareRules;

        option.seatAvailability = route.seats;
        option.seatNote = route.seats == SeatAvailability.VERIFIED
                ? text.get("seats_verified") : text.get("seats_unknown");
        fillTransit(option, text);

        option.arrivalDelayMinutes = req.originalArrival == null ? 0
                : (int) Math.max(0, Duration.between(req.originalArrival, route.arrival()).toMinutes());
        RankingWeights weights = settings.weights(req.urgency);
        long costMinutes = (long) cost(option) * weights.minutesPerThousandInr / 1000;
        option.score = (int) (option.arrivalDelayMinutes + (long) route.stops() * weights.stopPenaltyMinutes
                + costMinutes);
        return option;
    }

    /** Direct = no transit. Otherwise the worst answer over all stops: REQUIRED, then UNKNOWN, then OK. */
    private void fillTransit(RankedOption option, Sentences text) {
        List<FlightLeg> legs = option.route.legs;
        if (legs.size() == 1) {
            option.transitVisaStatus = TransitVisaStatus.OK;
            option.transitNote = text.get("transit_none");
            return;
        }
        TransitVisaStatus worst = TransitVisaStatus.OK;
        StringBuilder cities = new StringBuilder();
        for (int i = 0; i + 1 < legs.size(); i++) {
            String airport = legs.get(i).arrAirport;
            TransitVisaStatus status = transit.statusAt(airport, option.separateTickets);
            if (status == TransitVisaStatus.REQUIRED
                    || (status == TransitVisaStatus.UNKNOWN && worst == TransitVisaStatus.OK)) {
                worst = status;
            }
            if (cities.length() > 0) {
                cities.append(", ");
            }
            cities.append(city(airport, text));
        }
        option.transitVisaStatus = worst;
        if (worst == TransitVisaStatus.REQUIRED) {
            option.transitNote = text.get("transit_required", "cities", cities.toString());
        } else if (worst == TransitVisaStatus.UNKNOWN) {
            option.transitNote = text.get("transit_unknown", "cities", cities.toString());
        } else {
            option.transitNote = null; // OK through a checked source: nothing to warn about
        }
    }

    /**
     * Two routes that end with the same flight (e.g. two ways to reach the same Dubai-London flight): keep the one
     * that leaves last, so the traveller waits less. Done after the checks, so a failing route never hides a good one.
     */
    private static List<RankedOption> bestPerFinalFlight(List<RankedOption> options) {
        Map<String, RankedOption> best = new LinkedHashMap<>();
        for (RankedOption option : options) {
            List<FlightLeg> legs = option.route.legs;
            FlightLeg last = legs.get(legs.size() - 1);
            String key = last.flightNo + "@" + last.departure();
            RankedOption kept = best.get(key);
            if (kept == null || option.route.departure().isAfter(kept.route.departure())
                    || (option.route.departure().equals(kept.route.departure())
                        && option.route.stops() < kept.route.stops())) {
                best.put(key, option);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static Comparator<RankedOption> order(final RankingRequest req) {
        return new Comparator<RankedOption>() {
            @Override
            public int compare(RankedOption a, RankedOption b) {
                int result = Integer.compare(a.score, b.score);
                if (result == 0) {
                    result = Integer.compare(a.route.stops(), b.route.stops());
                }
                if (result == 0) {
                    result = Integer.compare(cost(a), cost(b));
                }
                if (result == 0) {
                    result = Boolean.compare(!a.route.usesAirline(req.preferredAirline),
                            !b.route.usesAirline(req.preferredAirline));
                }
                if (result == 0) {
                    result = a.route.departure().compareTo(b.route.departure());
                }
                if (result == 0) {
                    result = a.route.key().compareTo(b.route.key());
                }
                return result;
            }
        };
    }

    /** The extra cost that counts: the new ticket's fare for separate tickets, otherwise nothing. */
    private static int cost(RankedOption option) {
        return option.fareDifferenceInr == null ? 0 : option.fareDifferenceInr;
    }

    // ---- Sentences ----

    /** "Arrives in London at 12:55 on 12 Oct (9 h 45 min later than planned), no change of planes." */
    private String summary(RankedOption option, Sentences text) {
        List<FlightLeg> legs = option.route.legs;
        FlightLeg last = legs.get(legs.size() - 1);
        ZonedDateTime arrives = last.arrival().atZone(last.arrZone);
        String delay = option.arrivalDelayMinutes == 0 ? text.get("delay_none")
                : text.get("delay_later", "duration", text.duration(option.arrivalDelayMinutes));
        String stops;
        if (legs.size() == 1) {
            stops = text.get("stops_none");
        } else if (legs.size() == 2) {
            stops = text.get("stops_one", "city", city(legs.get(0).arrAirport, text));
        } else {
            stops = text.get("stops_many", "count", String.valueOf(legs.size() - 1));
        }
        return text.get("arrives", "city", city(last.arrAirport, text), "time", text.timeWithDay(arrives),
                "delay", delay, "stops", stops);
    }

    private String whyThisOne(RankedOption chosen, List<RankedOption> usable, Sentences text,
                              boolean noneChecked) {
        StringBuilder reason = new StringBuilder(summary(chosen, text));
        if (noneChecked) {
            return reason.append(' ').append(text.get("none_checked")).toString();
        }
        RankedOption earliestChecked = null;
        boolean uncheckedEarlier = false;
        for (RankedOption option : usable) {
            Instant arrival = option.route.arrival();
            if (option.connectionTimeChecked) {
                if (earliestChecked == null || arrival.isBefore(earliestChecked.route.arrival())) {
                    earliestChecked = option;
                }
            } else if (arrival.isBefore(chosen.route.arrival())) {
                uncheckedEarlier = true;
            }
        }
        reason.append(' ');
        if (earliestChecked == null || !earliestChecked.route.arrival().isBefore(chosen.route.arrival())) {
            reason.append(uncheckedEarlier ? text.get("why_earliest_checked") : text.get("why_earliest"));
        } else {
            String difference = text.duration((int) Duration.between(earliestChecked.route.arrival(),
                    chosen.route.arrival()).toMinutes());
            int moreStops = earliestChecked.route.stops() - chosen.route.stops();
            int moreCost = cost(earliestChecked) - cost(chosen);
            if (moreStops == 1) {
                reason.append(text.get("why_fewer_stops_one", "other", earliestChecked.route.flightNumbers(),
                        "difference", difference));
            } else if (moreStops > 1) {
                reason.append(text.get("why_fewer_stops_many", "other", earliestChecked.route.flightNumbers(),
                        "difference", difference, "count", String.valueOf(moreStops)));
            } else if (moreCost > 0) {
                reason.append(text.get("why_cheaper", "other", earliestChecked.route.flightNumbers(),
                        "difference", difference, "amount", text.amount(moreCost),
                        "fareLabel", fareLabel(earliestChecked.route, text)));
            } else {
                reason.append(text.get("why_balance"));
            }
        }
        for (RankedOption option : usable) {
            if (option.rank < chosen.rank && !option.connectionTimeChecked) {
                reason.append(' ').append(text.get("skipped_unchecked", "city", firstUncheckedCity(option, text)));
                break;
            }
        }
        return reason.toString();
    }

    private String otherReason(RankedOption option, Sentences text) {
        StringBuilder reason = new StringBuilder(summary(option, text));
        for (ConnectionCheck check : option.connectionChecks) {
            if (check.mctMinutes == null) {
                reason.append(' ').append(text.get("not_checked_detail", "city", city(check.airport, text)));
            } else if (check.status == ConnectionStatus.AT_RISK) {
                reason.append(' ').append(text.get("tight", "city", city(check.airport, text)));
            }
        }
        return reason.toString();
    }

    private String firstUncheckedCity(RankedOption option, Sentences text) {
        for (ConnectionCheck check : option.connectionChecks) {
            if (check.mctMinutes == null) {
                return city(check.airport, text);
            }
        }
        return "";
    }

    private static String fareLabel(CandidateRoute route, Sentences text) {
        return route.source == OptionSource.SAMPLE ? text.get("sample_fare_label") : "";
    }

    private String city(String iata, Sentences text) {
        AirportPack airport = rules.find(iata);
        return airport != null ? airport.cityName(text.language()) : iata;
    }
}
