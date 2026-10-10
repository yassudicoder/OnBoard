package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IntentMatcherTest {

    private final IntentMatcher matcher = new IntentMatcher(Lexicon.get());

    @Test
    public void boardingClose_beatsBoardingTime_whenCloseIsAsked() {
        IntentMatch match = matcher.match("Boarding kitne baje band hogi?");
        assertEquals(AskIntent.BOARDING_CLOSE, match.intent);
        assertEquals(IntentMatch.Confidence.HIGH, match.confidence);
    }

    @Test
    public void cabToHotel_isACab_notAHotelSearch() {
        assertEquals(AskIntent.CAB, matcher.match("Get me a cab to my hotel").intent);
    }

    @Test
    public void searchesAndBookings_areRecognised_butAreNotQuickAnswers() {
        assertEquals(AskIntent.SEARCH_HOTELS, matcher.match("Find me a hotel near the airport tonight").intent);
        assertEquals(AskIntent.SEARCH_FLIGHTS, matcher.match("Show the best flights to London tomorrow").intent);
        assertFalse(AskIntent.SEARCH_FLIGHTS.quickAnswer);
        assertFalse(AskIntent.CAB.quickAnswer);
    }

    @Test
    public void whereIs_knowsWhichPlace_andTheGateNumberMentioned() {
        IntentMatch match = matcher.match("washroom near gate 32");
        assertEquals(AskIntent.WHERE_IS, match.intent);
        assertEquals(IntentMatch.Place.WASHROOM, match.place);
        assertEquals("32", match.gateNumber);

        assertEquals(IntentMatch.Place.LIFT, matcher.match("लिफ्ट कुठे आहे").place);
        assertEquals(IntentMatch.Place.HELP_DESK, matcher.match("help desk kahan hai").place);
    }

    @Test
    public void bagDelayed_isAboutTheBag_notTheFlight() {
        assertEquals(AskIntent.BAG_STATUS, matcher.match("is my bag delayed").intent);
    }

    @Test
    public void nothingKnown_isUnknownWithNoConfidence() {
        IntentMatch match = matcher.match("tell me a joke");
        assertEquals(AskIntent.UNKNOWN, match.intent);
        assertEquals(IntentMatch.Confidence.NONE, match.confidence);
        assertNull(match.place);
    }

    @Test
    public void closeCall_betweenTwoIntents_isOnlyMediumConfidence() {
        // Lift (where is, 8) and gate (7) both match: the answer is about the lift, but not with a clear lead.
        IntentMatch match = matcher.match("is there a lift near gate 32");
        assertEquals(AskIntent.WHERE_IS, match.intent);
        assertEquals(IntentMatch.Confidence.MEDIUM, match.confidence);
    }

    @Test
    public void quickIntents_areMarkedAsQuickAnswers() {
        assertTrue(AskIntent.GATE.quickAnswer);
        assertTrue(AskIntent.WHERE_IS.quickAnswer);
        assertFalse(AskIntent.UNKNOWN.quickAnswer);
    }
}
