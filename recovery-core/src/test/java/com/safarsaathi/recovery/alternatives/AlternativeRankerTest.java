package com.safarsaathi.recovery.alternatives;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.safarsaathi.recovery.connection.ConnectionStatus;
import com.safarsaathi.recovery.connection.TicketType;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * Ranking the alternatives after MJ 31 (Doha 21:55 → London 03:10) is lost. Scores (minutes later than 03:10 +
 * changes of planes × penalty): NORMAL penalty 180, URGENT 30.
 * <pre>
 *   via Bahrain (DN 404 + DN 9)  06:00  1 stop  NORMAL 350  URGENT 200   Bahrain has no MCT: never recommended
 *   MJ 33 direct                 12:55  0 stop  NORMAL 585  URGENT 585
 *   via Dubai (DA 112 + DA 5)    10:25  1 stop  NORMAL 615  URGENT 465   130 min in Dubai, needs 75 (demo value)
 *   MJ 37 direct                 18:45  0 stop  NORMAL 935  URGENT 935
 * </pre>
 */
class AlternativeRankerTest {

    private final AlternativeRanker ranker = new AlternativeRanker(DemoRoutes.rules(), DemoRoutes.settings(),
            new NoTransitData());

    @Test
    void normalRecommendsTheDirectFlightAndSaysWhy() {
        Ranking ranking = ranker.rank(DemoRoutes.fromDoha(), request(Urgency.NORMAL, TicketType.SINGLE_BOOKING));

        assertThat(ranking.recommended.route.flightNumbers()).isEqualTo("MJ 33");
        assertThat(ranking.recommended.reason).isEqualTo("Arrives in London at 12:55 on 12 Oct (9 h 45 min later "
                + "than planned), no change of planes. Recommended: DA 112 + DA 5 arrives 2 h 30 min earlier but "
                + "needs 1 more change of planes. An earlier option via Bahrain is not recommended: its connection "
                + "time is not checked.");
        assertThat(ranking.others).extracting(option -> option.route.flightNumbers())
                .containsExactly("DN 404 + DN 9", "DA 112 + DA 5");
    }

    @Test
    void urgentRecommendsTheEarliestCheckedRouteViaDubai() {
        Ranking ranking = ranker.rank(DemoRoutes.fromDoha(), request(Urgency.URGENT, TicketType.SINGLE_BOOKING));

        RankedOption dubai = ranking.recommended;
        assertThat(dubai.route.flightNumbers()).isEqualTo("DA 112 + DA 5");
        assertThat(dubai.reason).isEqualTo("Arrives in London at 10:25 on 12 Oct (7 h 15 min later than planned), "
                + "1 change of planes in Dubai. Recommended: the earliest arrival with a checked connection time. An "
                + "earlier option via Bahrain is not recommended: its connection time is not checked.");
        assertThat(dubai.connectionChecks).singleElement().satisfies(check -> {
            assertThat(check.status).isEqualTo(ConnectionStatus.OK);
            assertThat(check.availableMinutes).isEqualTo(130);
            assertThat(check.mctMinutes).isEqualTo(75);
            assertThat(check.mctKind).isEqualTo(ValueKind.DEMO_VALUE);
        });
        assertThat(dubai.basedOnDemoValue).isTrue();
        assertThat(dubai.transitVisaStatus).isEqualTo(TransitVisaStatus.UNKNOWN);
        assertThat(dubai.transitNote).isEqualTo("Transit visa: UNKNOWN, check transit requirements for Dubai.");
        assertThat(ranking.others).extracting(option -> option.route.flightNumbers())
                .containsExactly("DN 404 + DN 9", "MJ 33");
    }

    @Test
    void urgentAndNormalRecommendDifferentOptions() {
        Ranking normal = ranker.rank(DemoRoutes.fromDoha(), request(Urgency.NORMAL, TicketType.UNKNOWN));
        Ranking urgent = ranker.rank(DemoRoutes.fromDoha(), request(Urgency.URGENT, TicketType.UNKNOWN));

        assertThat(normal.recommended.route.key()).isNotEqualTo(urgent.recommended.route.key());
        assertThat(normal.ranked).extracting(option -> option.score).containsExactly(350, 585, 615, 935);
        assertThat(urgent.ranked).extracting(option -> option.score).containsExactly(200, 465, 585, 935);
    }

    @Test
    void routeViaAnotherHubIsRejectedWhenTooShortForItsMinimum() {
        Ranking ranking = ranker.rank(DemoRoutes.fromDoha(), request(Urgency.URGENT, TicketType.UNKNOWN));

        assertThat(ranking.rejected).singleElement().satisfies(rejected -> {
            assertThat(rejected.route.flightNumbers()).isEqualTo("DA 114 + DA 3");
            assertThat(rejected.failedCheck.status).isEqualTo(ConnectionStatus.LIKELY_MISSED);
            assertThat(rejected.reason).isEqualTo("Not offered: the time to change planes in Dubai is too short. "
                    + "You land in Dubai at 00:45, your next flight leaves at 01:40. Dubai needs at least 75 min "
                    + "(demo value). You have 55 min.");
        });
        assertThat(ranking.ranked).extracting(option -> option.route.flightNumbers()).doesNotContain("DA 114 + DA 3");
    }

    @Test
    void anUncheckedConnectionTimeIsNeverRecommendedWhileACheckedOneExists() {
        for (Urgency urgency : Urgency.values()) {
            Ranking ranking = ranker.rank(DemoRoutes.fromDoha(), request(urgency, TicketType.UNKNOWN));

            RankedOption best = ranking.ranked.get(0); // the best score of all: via Bahrain
            assertThat(best.route.flightNumbers()).isEqualTo("DN 404 + DN 9");
            assertThat(best.recommended).isFalse();
            assertThat(best.connectionTimeChecked).isFalse();
            assertThat(best.connectionLabel).isEqualTo("connection time not checked");
            assertThat(best.reason).endsWith("Connection time not checked: OnBoard has no minimum connection time "
                    + "for Bahrain.");
            assertThat(ranking.recommended.connectionTimeChecked).isTrue();
        }
    }

    @Test
    void whenNothingIsCheckedTheBestIsRecommendedWithAWarning() {
        Ranking ranking = ranker.rank(Collections.singletonList(DemoRoutes.viaBahrain()),
                request(Urgency.NORMAL, TicketType.UNKNOWN));

        assertThat(ranking.recommended.route.flightNumbers()).isEqualTo("DN 404 + DN 9");
        assertThat(ranking.recommended.connectionLabel).isEqualTo("connection time not checked");
        assertThat(ranking.recommended.reason).endsWith("No option has a checked connection time: ask the airline "
                + "whether this connection works.");
    }

    @Test
    void twoWaysToTheSameLastFlightKeepTheShorterWait() {
        Ranking ranking = ranker.rank(DemoRoutes.fromDoha(), request(Urgency.NORMAL, TicketType.UNKNOWN));

        assertThat(ranking.ranked).extracting(option -> option.route.flightNumbers())
                .contains("DA 112 + DA 5").doesNotContain("DA 114 + DA 5");
    }

    @Test
    void separateTicketsShowTheFareAndTheRisk() {
        RankingRequest separate = request(Urgency.NORMAL, TicketType.SEPARATE_TICKETS);
        RankedOption twoAirlines = ranker.rank(Arrays.asList(DemoRoutes.delhiTwoAirlines()), separate).recommended;

        assertThat(twoAirlines.separateTickets).isTrue();
        assertThat(twoAirlines.separateTicketWarning).isEqualTo("Separate tickets: if one flight is late the other "
                + "airline may not wait, and your bags may not be transferred.");
        assertThat(twoAirlines.fareDifferenceInr).isEqualTo(49700);
        assertThat(twoAirlines.fareNote).isEqualTo("New ticket about ₹49,700 (SAMPLE fare). Whether your old "
                + "ticket is refunded depends on its rules.");

        RankedOption onOneTicket = ranker.rank(Arrays.asList(DemoRoutes.delhiTwoAirlines()),
                request(Urgency.NORMAL, TicketType.SINGLE_BOOKING)).recommended;
        assertThat(onOneTicket.fareDifferenceInr).isNull(); // the fare only matters when a new ticket is needed
        assertThat(onOneTicket.fareNote).isNull();
        assertThat(onOneTicket.separateTickets).isTrue(); // the route itself still mixes airlines
    }

    @Test
    void separateTicketFaresCountInNormalRanking() {
        // NORMAL with separate tickets: + fare/1000 × 10 min. MJ 33 = 585 + 338 = 923; via Dubai = 615 + 463 = 1078.
        Ranking ranking = ranker.rank(Arrays.asList(DemoRoutes.viaDubai(), DemoRoutes.mj33()),
                request(Urgency.NORMAL, TicketType.SEPARATE_TICKETS));

        assertThat(ranking.ranked).extracting(option -> option.score).containsExactly(923, 1078);
    }

    @Test
    void seatsAreUnknownUnlessAProviderVerifiedThem() {
        RankedOption direct = ranker.rank(Arrays.asList(DemoRoutes.mj33()),
                request(Urgency.NORMAL, TicketType.UNKNOWN)).recommended;

        assertThat(direct.seatAvailability).isEqualTo(SeatAvailability.UNKNOWN);
        assertThat(direct.seatNote).isEqualTo("Availability unknown");
        assertThat(direct.transitVisaStatus).isEqualTo(TransitVisaStatus.OK);
        assertThat(direct.transitNote).isEqualTo("No change of planes, so no transit visa question.");
        assertThat(direct.connectionTimeChecked).isTrue(); // nothing to check on a direct flight
    }

    private static RankingRequest request(Urgency urgency, TicketType ticketType) {
        RankingRequest request = new RankingRequest();
        request.urgency = urgency;
        request.ticketType = ticketType;
        request.originalArrival = DemoRoutes.ORIGINAL_ARRIVAL;
        request.preferredAirline = "MJ";
        return request;
    }
}
