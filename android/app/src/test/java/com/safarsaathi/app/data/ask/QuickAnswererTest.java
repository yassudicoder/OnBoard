package com.safarsaathi.app.data.ask;

import static com.safarsaathi.app.data.ask.AskFixtures.NOW;
import static com.safarsaathi.app.data.ask.AskFixtures.SYNCED;
import static com.safarsaathi.app.data.ask.AskFixtures.block;
import static com.safarsaathi.app.data.ask.AskFixtures.bagClaim;
import static com.safarsaathi.app.data.ask.AskFixtures.demoFacts;
import static com.safarsaathi.app.data.ask.AskFixtures.facts;
import static com.safarsaathi.app.data.ask.AskFixtures.flight;
import static com.safarsaathi.app.data.ask.AskFixtures.ist;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.local.BlockEntity;

import org.junit.Test;

import java.util.Collections;

/**
 * Quick answers in every reply language, filled with the demo trip's real data (flight IJ 512, Mumbai 08:00 to
 * Bengaluru 09:45, saved at 05:42) and the real terminal map. Checks the golden rules: data comes from the facts, and
 * missing data says "not announced".
 */
public class QuickAnswererTest {

    private final AskEngine engine = AskEngine.standard();

    private Answer ask(String question, AskLanguage language, TripFacts facts) {
        return engine.ask(question, language, LanguageHint.NONE, facts);
    }

    private static TripFacts withGate(TripFacts facts, String gate, String previous, boolean estimate) {
        return facts.withGate(new TripFacts.GateInfo(gate, previous, ist("2026-10-12T07:05"),
                ist("2026-10-12T07:35"), estimate, ist("2026-10-12T05:50")));
    }

    // ---- Gate ----

    @Test
    public void gate_missing_saysNotAnnounced_inEveryLanguage_withTheRealDepartureTime() {
        TripFacts facts = demoFacts();
        Answer en = ask("Where is my gate?", AskLanguage.EN, facts);
        assertEquals("The gate for IJ 512 is not announced yet. Your flight departs at 08:00.", en.text);
        assertEquals(AskIntent.GATE, en.intent);
        assertEquals(Answer.Source.PHONE, en.source);
        assertEquals(SYNCED, en.asOf);

        assertTrue(ask("मेरा गेट कहाँ है?", AskLanguage.HI, facts).text.contains("घोषित नहीं"));
        assertTrue(ask("माझे गेट कोणते आहे?", AskLanguage.MR, facts).text.contains("जाहीर झालेले नाही"));
        assertTrue(ask("Mera gate kahan hai?", AskLanguage.HINGLISH, facts).text.contains("announce nahi"));
        for (AskLanguage language : AskLanguage.values()) {
            String text = ask("gate", language, facts).text;
            assertTrue(language + ": " + text, text.contains("IJ 512") && text.contains("08:00"));
        }
    }

    @Test
    public void gate_known_givesGateAndBoardingClose_inEveryLanguage() {
        TripFacts facts = withGate(demoFacts(), "32", null, false);
        Answer en = ask("Where is my gate?", AskLanguage.EN, facts);
        assertEquals("Your gate for IJ 512 is 32. Boarding closes at 07:35.", en.text);
        assertEquals(Answer.Action.NAVIGATE, en.action);
        assertEquals(ist("2026-10-12T05:50"), en.asOf);

        assertEquals("आपकी फ्लाइट IJ 512 का गेट 32 है। बोर्डिंग 07:35 पर बंद होगी।",
                ask("Mera gate kahan hai?", AskLanguage.HI, facts).text);
        assertEquals("तुमच्या फ्लाइट IJ 512 चे गेट 32 आहे. बोर्डिंग 07:35 वाजता बंद होईल.",
                ask("gate", AskLanguage.MR, facts).text);
        assertEquals("Aapki flight IJ 512 ka gate 32 hai. Boarding 07:35 baje band hogi.",
                ask("gate", AskLanguage.HINGLISH, facts).text);
    }

    @Test
    public void gate_changed_mentionsTheOldGate() {
        Answer answer = ask("gate", AskLanguage.EN, withGate(demoFacts(), "41", "32", false));
        assertTrue(answer.text, answer.text.startsWith("Gate changed: IJ 512 now leaves from gate 41 (it was 32)."));
    }

    @Test
    public void boardingClose_estimate_isLabelledEstimate_inEveryLanguage() {
        TripFacts facts = withGate(demoFacts(), "32", null, true);
        assertTrue(ask("boarding close", AskLanguage.EN, facts).text.contains("(estimate)"));
        assertTrue(ask("boarding close", AskLanguage.HI, facts).text.contains("(अनुमान)"));
        assertTrue(ask("boarding close", AskLanguage.MR, facts).text.contains("(अंदाज)"));
        assertTrue(ask("boarding close", AskLanguage.HINGLISH, facts).text.contains("(estimate)"));
    }

    @Test
    public void boardingTimes_missing_sayNotAnnounced() {
        Answer close = ask("When does boarding close?", AskLanguage.EN, demoFacts());
        assertEquals("The boarding close time for IJ 512 is not announced yet. Your flight departs at 08:00.",
                close.text);
        Answer start = ask("When does boarding start?", AskLanguage.EN, demoFacts());
        assertTrue(start.text.startsWith("The boarding time for IJ 512 is not announced yet."));
    }

    @Test
    public void boardingClose_known() {
        Answer answer = ask("Boarding kitne baje band hogi?", AskLanguage.HINGLISH,
                withGate(demoFacts(), "32", null, false));
        assertEquals("Flight IJ 512 ki boarding 07:35 baje band hogi.", answer.text);
    }

    @Test
    public void cancelledFlight_hasNoGate_theAnswerSaysCancelled() {
        BlockEntity cancelled = flight();
        cancelled.status = BlockEntity.STATUS_CANCELLED;
        Answer answer = ask("Where is my gate?", AskLanguage.EN, withGate(facts(cancelled), "32", null, false));
        assertEquals("IJ 512 is cancelled. Open Home to see what you can do.", answer.text);
        assertEquals(Answer.Action.OPEN_HOME, answer.action);
    }

    // ---- Flight status and connection ----

    @Test
    public void flightStatus_onPlan_delayed_needsDecision() {
        assertEquals("IJ 512 departs at 08:00 and lands at 09:45. No change in your saved plan.",
                ask("Is my flight on time?", AskLanguage.EN, demoFacts()).text);

        BlockEntity late = flight();
        late.startTime = ist("2026-10-12T09:10");
        late.endTime = ist("2026-10-12T10:55");
        assertEquals("IJ 512 is late: it now departs at 09:10 (planned 08:00) and lands at 10:55.",
                ask("is my flight delayed", AskLanguage.EN, facts(late)).text);

        BlockEntity decide = flight();
        decide.status = BlockEntity.STATUS_NEEDS_USER;
        assertEquals(Answer.Action.OPEN_HOME, ask("flight status", AskLanguage.EN, facts(decide)).action);
    }

    @Test
    public void connection_none_gap_andTooLate() {
        assertEquals("Your saved trip has no connecting flight after IJ 512.",
                ask("Will I miss my connection?", AskLanguage.EN, demoFacts()).text);

        BlockEntity second = block(12, BlockEntity.TYPE_FLIGHT, "Flight SM 220 Bengaluru to Doha",
                "2026-10-12T11:05", "2026-10-12T13:30", BlockEntity.STATUS_OK);
        Answer gap = ask("Will I miss my connection?", AskLanguage.EN, facts(flight(), second));
        assertTrue(gap.text, gap.text.startsWith(
                "IJ 512 lands at 09:45. SM 220 departs at 11:05, so you have 1 h 20 min between them."));
        assertTrue(gap.text, gap.text.contains("check with the airline"));
        assertTrue(ask("connection", AskLanguage.MR, facts(flight(), second)).text.contains("1 तास 20 मिनिटे"));

        BlockEntity late = flight();
        late.endTime = ist("2026-10-12T11:30");
        Answer tooLate = ask("Doha connection miss hoga kya?", AskLanguage.EN, facts(late, second));
        assertEquals("IJ 512 now lands at 11:30, after SM 220 departs at 11:05. "
                + "Talk to the airline about your connection.", tooLate.text);
    }

    // ---- Bags ----

    @Test
    public void bag_noneRegistered_pointsToTheBagsTab() {
        Answer answer = ask("Majhi bag kuthe aahe?", AskLanguage.MR, demoFacts());
        assertEquals(AskIntent.BAG_STATUS, answer.intent);
        assertTrue(answer.text.startsWith("या फोनवर अजून कोणतीही बॅग नोंदवलेली नाही."));
        assertEquals(Answer.Action.OPEN_BAGS, answer.action);
    }

    @Test
    public void belt_notAnnounced_thenKnownFromTheBagRecord() {
        assertTrue(ask("Which belt?", AskLanguage.EN, demoFacts()).text
                .startsWith("The baggage belt for IJ 512 is not announced yet."));
        TripFacts withBag = demoFacts().withBag(new TripFacts.BagInfo("4471", "ON_BELT", "7", NOW));
        assertEquals("Bags from IJ 512 come on belt 7.", ask("Which belt?", AskLanguage.EN, withBag).text);
        assertEquals("Your bag (tag ending 4471): on the belt now. Bags from IJ 512 come on belt 7.",
                ask("Where is my bag?", AskLanguage.EN, withBag).text);
    }

    // ---- What next ----

    @Test
    public void nextStep_beforeTheFlight_isTheAirportCheckpoints() {
        Answer answer = ask("What should I do next?", AskLanguage.EN, demoFacts());
        assertEquals("Next: IJ 512 departs at 08:00 from Mumbai Airport T2 (BOM). At the airport: check-in or bag "
                + "drop, then security, then your gate.", answer.text);
    }

    @Test
    public void nextStep_afterTheFlight_isTheNextBooking() {
        TripFacts afterLanding = TripFacts.from(AskFixtures.trip(),
                java.util.Arrays.asList(flight(), bagClaim()), null, ist("2026-10-12T09:50"));
        assertEquals("Next in your trip: Collect bags at Bengaluru T1 at 09:45.",
                ask("what now", AskLanguage.EN, afterLanding).text);
    }

    // ---- Documents, places, help, cloud requests ----

    // ---- Travel documents (vault summaries) ----

    /** Paramvir's SAMPLE folder: a passport, a Rwanda e-visa expiring soon, an insurance whose file is missing. */
    private static TripFacts withVault() {
        java.util.List<DocumentFacts.Doc> docs = java.util.Arrays.asList(
                new DocumentFacts.Doc("PASSPORT", "Paramvir", java.time.LocalDate.of(2031, 3, 12), null, null,
                        Collections.singletonList("IN"), DocumentFacts.Status.READY, true),
                new DocumentFacts.Doc("E_VISA", "Paramvir", java.time.LocalDate.of(2026, 10, 20), null, null,
                        Collections.singletonList("RW"), DocumentFacts.Status.EXPIRES_SOON, true),
                new DocumentFacts.Doc("INSURANCE", "Paramvir", null, java.time.LocalDate.of(2026, 10, 10),
                        java.time.LocalDate.of(2026, 10, 25), Collections.<String>emptyList(),
                        DocumentFacts.Status.MISSING, true));
        return demoFacts().withDocuments(DocumentFacts.unlocked("Delhi → Kinshasa", docs));
    }

    @Test
    public void documents_noVaultOnThePhone_saySo() {
        Answer answer = ask("When does my passport expire?", AskLanguage.EN, demoFacts());
        assertEquals(AskIntent.DOCUMENT_EXPIRY, answer.intent);
        assertEquals("There's no Document Vault on this phone yet, so I can't check your documents.", answer.text);
        assertNull(answer.asOf);
    }

    @Test
    public void documents_lockedVault_asksToUnlock_andShowsNothing() {
        TripFacts locked = demoFacts().withDocuments(DocumentFacts.locked(null));
        Answer answer = ask("Kya mera visa saved hai?", AskLanguage.HINGLISH, locked);
        assertEquals(AskIntent.DOCUMENT_MISSING, answer.intent);
        assertEquals("Aapka Document Vault lock hai. Ise dekhne ke liye vault unlock kijiye.", answer.text);
        assertEquals(Answer.Action.UNLOCK_VAULT, answer.action);
    }

    @Test
    public void passportExpiry_fromTheSummary_withFirstNameAndSampleLabel() {
        Answer answer = ask("When does my passport expire?", AskLanguage.EN, withVault());
        assertEquals("In Delhi → Kinshasa: Passport (Paramvir): valid until 12 Mar 2031, ready (SAMPLE).",
                answer.text);
        assertEquals(Answer.Action.OPEN_VAULT, answer.action);
    }

    @Test
    public void visaSaved_inMarathi_countsDaysLeft_andNeverSaysRequired() {
        Answer answer = ask("Majha visa save aahe ka?", AskLanguage.MR, withVault());
        assertEquals(AskIntent.DOCUMENT_MISSING, answer.intent);
        // (The month's short name comes from the phone's own Marathi calendar data, so only the day is checked.)
        assertTrue(answer.text, answer.text.contains("रवांडासाठी ई-व्हिसा (Paramvir): 20 "));
        assertTrue(answer.text, answer.text.contains("लवकरच संपेल (8 दिवस बाकी)"));
        assertTrue(answer.text, answer.text.endsWith("एअरलाइन किंवा दूतावासाला विचारा."));
    }

    @Test
    public void missingStatus_meansTheFileIsMissingOnThePhone() {
        Answer answer = ask("Am I missing any documents?", AskLanguage.EN, withVault());
        assertEquals(AskIntent.DOCUMENT_MISSING, answer.intent);
        assertTrue(answer.text, answer.text.startsWith("Needs your attention in Delhi → Kinshasa: "));
        assertTrue(answer.text, answer.text.contains(
                "Travel insurance (Paramvir): valid until 25 Oct 2026, its saved file is missing on this phone"));
        assertFalse("the passport is ready, so not listed", answer.text.contains("Passport"));
        assertTrue(answer.text, answer.text.endsWith(
                "I can't tell which documents a country requires: check with the airline or embassy."));
    }

    @Test
    public void noDocumentOfThatKind_saysNotSaved_withTheRequirementsNote() {
        TripFacts empty = demoFacts().withDocuments(DocumentFacts.unlocked("Mumbai trip",
                Collections.<DocumentFacts.Doc>emptyList()));
        assertEquals("No visa is saved in Mumbai trip. I can't tell which documents a country requires: check with "
                + "the airline or embassy.", ask("Is my visa saved?", AskLanguage.EN, empty).text);
    }

    @Test
    public void allSavedDocumentsReady_saysSo_withoutClaimingNothingIsRequired() {
        TripFacts ready = demoFacts().withDocuments(DocumentFacts.unlocked("Trip", Collections.singletonList(
                new DocumentFacts.Doc("PASSPORT", "Aditi", java.time.LocalDate.of(2030, 1, 1), null, null,
                        Collections.<String>emptyList(), DocumentFacts.Status.READY, false))));
        assertEquals("All 1 saved documents in Trip are ready. I can't tell which documents a country requires: "
                + "check with the airline or embassy.", ask("Am I missing any documents?", AskLanguage.EN, ready).text);
    }

    @Test
    public void washrooms_fromTheMap_describedByTheNearestLandmark_andLabelledDemo() {
        Answer answer = ask("Where are the washrooms?", AskLanguage.EN, demoFacts());
        assertTrue(answer.text, answer.text.startsWith("Washrooms in Terminal 2: near "));
        assertTrue(answer.text, answer.text.contains("near Gate 32 (Departures, after security)"));
        assertTrue(answer.text, answer.text.endsWith("From the demo map, not an official airport map."));
        assertEquals(Answer.Action.OPEN_MAP, answer.action);
        assertNull("map answers have no 'as of' time", answer.asOf);
    }

    @Test
    public void washrooms_nearAGate_closestFirst() {
        Answer answer = ask("washroom near gate 41", AskLanguage.EN, demoFacts());
        assertTrue(answer.text, answer.text.startsWith("Washrooms in Terminal 2 (closest to gate 41 first): near Gate 42"));
        assertTrue(answer.text, answer.text.indexOf("Gate 42") < answer.text.indexOf("Gate 32"));
    }

    @Test
    public void lifts_haveDifferentNames_soTheNameIsShown_inHindi() {
        Answer answer = ask("लिफ्ट किधर है", AskLanguage.HI, demoFacts());
        assertTrue(answer.text, answer.text.startsWith("टर्मिनल 2 में लिफ्ट: लिफ्ट "));
        assertTrue(answer.text, answer.text.contains("प्रस्थान"));
        assertTrue(answer.text, answer.text.contains("सुरक्षा जांच के बाद"));
    }

    @Test
    public void noMap_saysSo() {
        TripFacts noMap = TripFacts.from(AskFixtures.trip(), Collections.singletonList(flight()), null, NOW);
        assertTrue(ask("toilet", AskLanguage.EN, noMap).text.startsWith("The Terminal 2 map isn't on this phone"));
    }

    @Test
    public void searches_needOnline_andSayTheyCannotBeDoneOnThePhone() {
        Answer hotel = ask("Find me a hotel near the airport tonight", AskLanguage.EN, demoFacts());
        assertEquals(AskIntent.SEARCH_HOTELS, hotel.intent);
        assertTrue(hotel.needsOnline);
        assertTrue(hotel.text.startsWith("I can't search flights or hotels on the phone."));
        Answer cab = ask("Get me a cab to my hotel", AskLanguage.EN, demoFacts());
        assertEquals(Answer.Action.OPEN_HOME, cab.action);
        assertFalse(ask("Where is my gate?", AskLanguage.EN, demoFacts()).needsOnline);
    }

    // ---- No trip, and the "never a raw placeholder" rule ----

    @Test
    public void noSavedTrip_saysSo() {
        TripFacts empty = TripFacts.from(null, Collections.<BlockEntity>emptyList(), null, NOW);
        Answer answer = ask("Where is my gate?", AskLanguage.EN, empty);
        assertEquals("Your trip isn't saved on this phone yet. Open Home once while you're online to load it.",
                answer.text);
        assertNull(answer.asOf);
    }

    @Test
    public void everyIntent_inEveryLanguage_withAndWithoutData_neverShowsAPlaceholder() {
        BlockEntity second = block(12, BlockEntity.TYPE_FLIGHT, "Flight SM 220 Bengaluru to Doha",
                "2026-10-12T11:05", "2026-10-12T13:30", BlockEntity.STATUS_OK);
        TripFacts[] variants = {
                TripFacts.from(null, Collections.<BlockEntity>emptyList(), null, NOW),
                demoFacts(),
                withGate(facts(flight(), second), "32", "41", true)
                        .withBag(new TripFacts.BagInfo("4471", "DELAYED", null, NOW))};
        String[] questions = {"gate", "boarding start", "boarding close", "flight status", "connection",
                "where is my bag", "belt", "what now", "passport expiry", "washroom", "lift", "help desk", "help",
                "find hotel", "best flights", "cab", "blah"};
        for (TripFacts facts : variants) {
            for (AskLanguage language : AskLanguage.values()) {
                for (String question : questions) {
                    String text = ask(question, language, facts).text;
                    assertFalse(language + " " + question + ": " + text, text.contains("{") || text.isEmpty());
                }
            }
        }
    }

    @Test
    public void times_onAnotherDay_sayTomorrow_inTheReplyLanguage() {
        TripFacts dayBefore = TripFacts.from(AskFixtures.trip(), Collections.singletonList(flight()), null,
                ist("2026-10-11T21:00"));
        assertTrue(ask("flight status", AskLanguage.EN, dayBefore).text.contains("08:00 tomorrow"));
        assertTrue(ask("flight status", AskLanguage.HI, dayBefore).text.contains("कल 08:00"));
        assertTrue(ask("flight status", AskLanguage.MR, dayBefore).text.contains("उद्या 08:00"));
    }

    @Test
    public void flightName_isReadFromTheTitle() {
        assertEquals("IJ 512", TripFacts.flightName("Flight IJ 512 Mumbai to Bengaluru"));
        assertEquals("6E 2131", TripFacts.flightName("Flight 6E 2131 to Goa"));
        assertEquals("Morning flight", TripFacts.flightName("Morning flight"));
    }
}
