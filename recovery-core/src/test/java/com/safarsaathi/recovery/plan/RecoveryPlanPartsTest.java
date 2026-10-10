package com.safarsaathi.recovery.plan;

import static com.safarsaathi.recovery.alternatives.DemoRoutes.INDIA;
import static com.safarsaathi.recovery.alternatives.DemoRoutes.LONDON;
import static com.safarsaathi.recovery.alternatives.DemoRoutes.QATAR;
import static com.safarsaathi.recovery.alternatives.DemoRoutes.at;
import static com.safarsaathi.recovery.alternatives.DemoRoutes.leg;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.safarsaathi.recovery.alternatives.AlternativeRanker;
import com.safarsaathi.recovery.alternatives.DemoRoutes;
import com.safarsaathi.recovery.alternatives.NoTransitData;
import com.safarsaathi.recovery.alternatives.RankedOption;
import com.safarsaathi.recovery.alternatives.RankingRequest;
import com.safarsaathi.recovery.alternatives.Urgency;
import com.safarsaathi.recovery.connection.FlightLeg;
import com.safarsaathi.recovery.connection.TicketType;

/**
 * Where a plan starts (the three triggers), the Overnight section and the next steps, on trip 3: IJ 1907 Delhi 17:50
 * → Doha 19:35, MJ 31 Doha 21:55 → London 03:10. "Now" is the evening before (10 Oct 17:30 in India).
 */
class RecoveryPlanPartsTest {

    private static final Instant NOW = at("2026-10-10T17:30+05:30");

    // ---- Where and when the plan starts ----

    @Test
    void delayStartsAtTheHubAfterTheMinimumConnectionTime() {
        RecoveryStart start = RecoveryStart.of(delhiDoha(95), dohaLondon(), 60, 60, NOW, DemoRoutes.rules());

        assertThat(start.trigger).isEqualTo(RecoveryTrigger.DELAY_MISSED_CONNECTION);
        assertThat(start.airport).isEqualTo("DOH");
        assertThat(start.atHub).isTrue();
        assertThat(start.earliestDeparture).isEqualTo(at("2026-10-11T22:10+03:00")); // lands 21:10 + 60 min
        assertThat(start.destination).isEqualTo("LHR");
        assertThat(start.originalArrival).isEqualTo(at("2026-10-12T03:10+01:00"));
        assertThat(start.disruptedFlight.flightNo).isEqualTo("IJ 1907"); // the late one: its assistance policy
        assertThat(start.replacedFlight.flightNo).isEqualTo("MJ 31");
    }

    @Test
    void cancelledBeforeDepartureStartsAtTheDepartureAirport() {
        FlightLeg cancelled = delhiDoha(0);
        cancelled.cancelled = true;

        RecoveryStart start = RecoveryStart.of(cancelled, dohaLondon(), 60, 60, NOW, DemoRoutes.rules());

        assertThat(start.trigger).isEqualTo(RecoveryTrigger.CANCELLED_BEFORE_DEPARTURE);
        assertThat(start.airport).isEqualTo("DEL");
        assertThat(start.atHub).isFalse();
        // Known the evening before: alternatives from the original departure time, not from tonight.
        assertThat(start.earliestDeparture).isEqualTo(at("2026-10-11T17:50+05:30"));

        // Known at the gate (17:00): now + 90 min for check-in and security (estimate) is later, so that counts.
        RecoveryStart atTheGate = RecoveryStart.of(cancelled, dohaLondon(), 60, 60, at("2026-10-11T17:00+05:30"),
                DemoRoutes.rules());
        assertThat(atTheGate.earliestDeparture).isEqualTo(at("2026-10-11T18:30+05:30"));
    }

    @Test
    void connectionCancelledAtTheHubStartsThereFromLanding() {
        FlightLeg cancelled = dohaLondon();
        cancelled.cancelled = true;

        RecoveryStart start = RecoveryStart.of(delhiDoha(0), cancelled, 60, 60, NOW, DemoRoutes.rules());

        assertThat(start.trigger).isEqualTo(RecoveryTrigger.CONNECTION_CANCELLED_AT_HUB);
        assertThat(start.airport).isEqualTo("DOH");
        assertThat(start.earliestDeparture).isEqualTo(at("2026-10-11T20:35+03:00")); // lands 19:35 + 60
        assertThat(start.disruptedFlight.flightNo).isEqualTo("MJ 31"); // the cancelled one
    }

    // ---- Overnight ----

    @Test
    void nextDayFlightNeedsTheOvernightSectionWithTheAirlinesPolicyOrUnknown() {
        RecoveryStart delay = RecoveryStart.of(delhiDoha(95), dohaLondon(), 60, 60, NOW, DemoRoutes.rules());

        OvernightAdvice advice = OvernightAdvice.build(delay, at("2026-10-12T07:40+03:00"), TicketType.SINGLE_BOOKING,
                DemoRoutes.policies(), DemoRoutes.settings(), DemoRoutes.rules(), "en");

        assertThat(advice.needed).isTrue();
        assertThat(advice.reason).isEqualTo("Your next suitable flight leaves Doha at 07:40 on 12 Oct, 9 h 30 min "
                + "after you could leave.");
        assertThat(advice.airlineName).isEqualTo("IndiJet"); // the delayed flight's airline: no policy text known
        assertThat(advice.policyText).isNull();
        assertThat(advice.policyUnknownText).isEqualTo("Ask the airline whether meals, a hotel or transport are "
                + "provided under your ticket.");
        assertThat(advice.receiptsText).isEqualTo("Keep all receipts (meals, hotel, transport).");
        assertThat(advice.noBookingText).isEqualTo("OnBoard never books a hotel without your explicit approval.");
        assertThat(advice.passengerRightsText).startsWith("Passenger-rights rules may apply");
        assertThat(advice.leavingTransitText).contains("permission to enter the country");
        assertThat(advice.requestLine).isEqualTo("As the next suitable flight is not until 07:40 on 12 Oct, please "
                + "confirm whether meals, hotel accommodation and transport are provided under my ticket.");
    }

    @Test
    void cancellationAtTheHubShowsThatAirlinesSamplePolicy() {
        FlightLeg cancelled = dohaLondon();
        cancelled.cancelled = true;
        RecoveryStart start = RecoveryStart.of(delhiDoha(0), cancelled, 60, 60, NOW, DemoRoutes.rules());

        OvernightAdvice advice = OvernightAdvice.build(start, at("2026-10-12T07:40+03:00"), TicketType.UNKNOWN,
                DemoRoutes.policies(), DemoRoutes.settings(), DemoRoutes.rules(), "en");

        assertThat(advice.airlineName).isEqualTo("Marjan Air");
        assertThat(advice.policyText).startsWith("SAMPLE POLICY");
        assertThat(advice.policyKind).isEqualTo("SAMPLE");
        assertThat(advice.policyUnknownText).isNull();
    }

    @Test
    void aFlightLaterTheSameEveningIsNoOvernight() {
        RecoveryStart delay = RecoveryStart.of(delhiDoha(95), dohaLondon(), 60, 60, NOW, DemoRoutes.rules());

        OvernightAdvice advice = OvernightAdvice.build(delay, at("2026-10-11T23:20+03:00"), TicketType.UNKNOWN,
                DemoRoutes.policies(), DemoRoutes.settings(), DemoRoutes.rules(), "en");

        assertThat(advice.needed).isFalse();
        assertThat(advice.reason).isNull();
    }

    // ---- Next steps ----

    @Test
    void stepsForAMissedConnectionOnOneTicket() {
        RecoverySteps.Input in = input(TicketType.SINGLE_BOOKING);

        List<PlanStep> steps = RecoverySteps.build(in);

        assertThat(steps).extracting(step -> step.kind)
                .containsExactly("CHECK_REBOOKING", "ASK_AIRLINE", "COMPARE", "TRANSIT", "OVERNIGHT", "CONFIRM");
        assertThat(steps).extracting(step -> step.number).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(steps.get(0).text).isEqualTo("Check whether Marjan Air has already rebooked you: look in the "
                + "airline's app, email and SMS. On one ticket the airline is normally responsible for getting you to "
                + "London.");
        assertThat(steps.get(2).text).isEqualTo("Compare the alternatives: OnBoard recommends MJ 33, arriving in "
                + "London at 12:55 on 12 Oct. The alternatives are SAMPLE DATA.");
        assertThat(steps.get(3).text).isEqualTo("Check transit and visa rules for the route you choose. The "
                + "recommended route has no change of planes.");
        assertThat(steps.get(4).text).isEqualTo("Your next suitable flight is not until 07:40 on 12 Oct: see below "
                + "what IndiJet provides (not known: ask the airline). Keep all receipts.");
        assertThat(steps.get(5).text).isEqualTo("Confirm your choice. Nothing is booked or paid until you approve "
                + "the price and conditions.");
        assertThat(RecoverySteps.whereToGo(in.start, in.policies, in.rules, "en")).isEqualTo("On arrival in Doha: "
                + "follow signs to Transfers, go to the airline's transfer desk and show this screen. If it is "
                + "closed, use the contact channels below.");
    }

    @Test
    void stepsAdaptToSeparateTicketsAndToAnAirlineRebooking() {
        RecoverySteps.Input separate = input(TicketType.SEPARATE_TICKETS);
        assertThat(RecoverySteps.build(separate).get(0).text).isEqualTo("Your tickets are separate, so Marjan Air may "
                + "treat you as a no-show. Check your messages, then contact Marjan Air as early as you can.");

        RecoverySteps.Input unknown = input(TicketType.UNKNOWN);
        assertThat(RecoverySteps.build(unknown).get(0).text).startsWith("Check whether the airline has already "
                + "rebooked you");

        RecoverySteps.Input rebooked = input(TicketType.SINGLE_BOOKING);
        rebooked.rebookedFlight = "MJ 33";
        rebooked.rebookedDeparture = at("2026-10-12T07:40+03:00");
        List<PlanStep> steps = RecoverySteps.build(rebooked);
        assertThat(steps.get(0).text).isEqualTo("The airline has rebooked you on MJ 33, leaving Doha at 07:40 on "
                + "12 Oct. Check the new booking in the airline's app or email, then accept it or ask for one of the "
                + "alternatives.");
        assertThat(steps.get(1).kind).isEqualTo("ASK_AIRLINE");
    }

    @Test
    void atTheDepartureAirportTheAdviceIsTheAirlinesCounter() {
        FlightLeg cancelled = delhiDoha(0);
        cancelled.cancelled = true;
        RecoveryStart start = RecoveryStart.of(cancelled, dohaLondon(), 60, 60, NOW, DemoRoutes.rules());

        assertThat(RecoverySteps.whereToGo(start, DemoRoutes.policies(), DemoRoutes.rules(), "en"))
                .isEqualTo("At Delhi airport: ask at the IndiJet counter, or use the contact channels below.");
    }

    // ---- Test data ----

    private static RecoverySteps.Input input(TicketType ticketType) {
        RecoveryStart start = RecoveryStart.of(delhiDoha(95), dohaLondon(), 60, 60, NOW, DemoRoutes.rules());
        RankingRequest request = new RankingRequest();
        request.urgency = Urgency.NORMAL;
        request.ticketType = ticketType;
        request.originalArrival = start.originalArrival;
        RankedOption recommended = new AlternativeRanker(DemoRoutes.rules(), DemoRoutes.settings(), new NoTransitData())
                .rank(DemoRoutes.fromDoha(), request).recommended;

        RecoverySteps.Input in = new RecoverySteps.Input();
        in.start = start;
        in.ticketType = ticketType;
        in.recommended = recommended;
        in.overnight = OvernightAdvice.build(start, recommended.route.departure(), ticketType, DemoRoutes.policies(),
                DemoRoutes.settings(), DemoRoutes.rules(), "en");
        in.searchWindowHours = 36;
        in.policies = DemoRoutes.policies();
        in.rules = DemoRoutes.rules();
        return in;
    }

    private static FlightLeg delhiDoha(int minutesLate) {
        FlightLeg leg = leg("IJ 1907", "DEL", INDIA, "2026-10-11T17:50+05:30", "DOH", QATAR, "2026-10-11T19:35+03:00");
        if (minutesLate > 0) {
            leg.estimatedDep = leg.scheduledDep.plus(Duration.ofMinutes(minutesLate));
            leg.estimatedArr = leg.scheduledArr.plus(Duration.ofMinutes(minutesLate));
        }
        return leg;
    }

    private static FlightLeg dohaLondon() {
        return leg("MJ 31", "DOH", QATAR, "2026-10-11T21:55+03:00", "LHR", LONDON, "2026-10-12T03:10+01:00");
    }
}
