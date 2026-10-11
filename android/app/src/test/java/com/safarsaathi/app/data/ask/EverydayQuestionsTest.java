package com.safarsaathi.app.data.ask;

import static com.safarsaathi.app.data.ask.AskFixtures.demoFacts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.baggage.BaggageFacts;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The everyday questions that used to get "Sorry, I didn't get that" or a wrong answer (demo probe, 11 Oct): bag
 * problems, lost things, more places on the map, rules the phone does not have, small talk. Bag-problem and Lost &amp;
 * Found numbers and places must come from the real baggage-facts.json, never from the sentences.
 */
public class EverydayQuestionsTest {

    private static BaggageFacts baggage;
    private static AskEngine engine;

    @BeforeClass
    public static void readFacts() throws IOException {
        try (Reader reader = new InputStreamReader(
                new FileInputStream("../../mock-data/baggage/" + BaggageFacts.ASSET), StandardCharsets.UTF_8)) {
            baggage = BaggageFacts.parse(reader);
        }
        engine = AskEngine.standard(baggage);
    }

    private static Answer ask(String question) {
        return engine.ask(question, null, LanguageHint.NONE, demoFacts());
    }

    // ---- Bag problems ----

    @Test
    public void damagedBag_isNotTheNextStep_andQuotesThePirAndTheLimitFromTheFile() {
        Answer answer = ask("My bag is damaged, what do I do?");
        assertEquals(AskIntent.BAG_DAMAGED, answer.intent);
        assertTrue(answer.text, answer.text.contains("Property Irregularity Report (PIR)"));
        assertTrue(answer.text, answer.text.contains("within " + baggage.deadlines.damageDays + " days"));
        assertTrue(answer.text, answer.text.contains("Airline baggage services")); // the counter, from the map
        assertEquals(Answer.Action.OPEN_HELP, answer.action);
    }

    @Test
    public void bagThatDidNotArrive_usesTheDelayAndLostLimits_andPointsToLostAndFound() {
        Answer answer = ask("My bag didn't arrive");
        assertEquals(AskIntent.BAG_NOT_ARRIVED, answer.intent);
        assertTrue(answer.text, answer.text.contains("within " + baggage.deadlines.delayDays + " days"));
        assertTrue(answer.text, answer.text.contains("after " + baggage.deadlines.lostAfterDays + " days"));
        assertTrue(answer.text, answer.text.contains("Lost & Found"));
        assertEquals(AskIntent.BAG_NOT_ARRIVED, ask("I lost my bag").intent);
    }

    @Test
    public void somethingStolenFromTheBag_isPilferage() {
        Answer answer = ask("Something was stolen from my bag");
        assertEquals(AskIntent.BAG_PILFERED, answer.intent);
        assertTrue(answer.text, answer.text.contains("within " + baggage.deadlines.damageDays + " days"));
    }

    @Test
    public void withoutTheFactsFile_noTimeLimitIsQuoted() {
        Answer answer = AskEngine.standard().ask("My bag is damaged", null, LanguageHint.NONE, demoFacts());
        assertEquals(AskIntent.BAG_DAMAGED, answer.intent);
        assertTrue(answer.text, answer.text.contains("PIR"));
        assertFalse(answer.text, answer.text.contains(" days"));
    }

    @Test
    public void bagProblemsInEveryLanguage() {
        assertEquals(AskIntent.BAG_DAMAGED, ask("Bag toot gaya").intent);
        assertEquals(AskLanguage.HINGLISH, ask("Mera bag toot gaya").language);
        assertEquals(AskIntent.BAG_DAMAGED, ask("मेरा बैग टूट गया").intent);
        assertEquals(AskIntent.BAG_DAMAGED, ask("माझी बॅग फुटली").intent);
        assertEquals(AskIntent.BAG_NOT_ARRIVED, ask("मेरा सामान नहीं आया").intent);
        assertEquals(AskIntent.BAG_NOT_ARRIVED, ask("माझी बॅग आली नाही").intent);
    }

    // ---- Lost things ----

    @Test
    public void lostSomething_givesCsmiaLostAndFoundWithItsCheckDate() {
        Answer answer = ask("I lost my headphones near security");
        assertEquals(AskIntent.LOST_ITEM, answer.intent);
        assertTrue(answer.text, answer.text.contains(baggage.lostFound.t2Location));
        assertTrue(answer.text, answer.text.contains("11 Oct 2026"));
        assertEquals(Answer.Action.OPEN_LOST_FOUND, answer.action);
        assertEquals(AskIntent.LOST_ITEM, ask("Where is lost and found?").intent);
        assertEquals(AskIntent.LOST_ITEM, ask("मेरा फोन खो गया").intent);
    }

    // ---- More places on the map ----

    @Test
    public void foodSecurityAndCheckInComeFromTheMap() {
        Answer food = ask("Where can I eat?");
        assertEquals(AskIntent.WHERE_IS, food.intent);
        assertTrue(food.text, food.text.contains("Food court"));
        assertTrue(food.text, food.text.contains("demo map"));
        assertTrue(ask("Where is security?").text.contains("Security check"));
        Answer dropBag = ask("Where do I drop my bag?");
        assertEquals(AskIntent.WHERE_IS, dropBag.intent);
        assertTrue(dropBag.text, dropBag.text.contains("Self bag drop"));
        assertEquals(AskIntent.WHERE_IS, ask("Khana kahan milega?").intent);
    }

    // ---- What the phone does not know: said honestly, never guessed ----

    @Test
    public void rulesAndAllowanceAreNotGuessed() {
        Answer powerBank = ask("Can I carry a power bank?");
        assertEquals(AskIntent.ITEM_RULES, powerBank.intent);
        assertTrue(powerBank.text, powerBank.text.contains("won't guess"));
        assertEquals(AskIntent.BAG_ALLOWANCE, ask("What is my baggage allowance?").intent);
        Answer wheelchair = ask("I need a wheelchair");
        assertEquals(AskIntent.ASSISTANCE, wheelchair.intent);
        assertTrue(wheelchair.text, wheelchair.text.contains("can't book"));
    }

    @Test
    public void gettingToTheAirport_usesTheSavedFlight() {
        Answer answer = ask("When should I leave for the airport?");
        assertEquals(AskIntent.GETTING_THERE, answer.intent);
        assertTrue(answer.text, answer.text.contains("IJ 512") && answer.text.contains("08:00"));
        assertEquals(Answer.Action.OPEN_HOME, answer.action);
    }

    // ---- Small talk and the fallback ----

    @Test
    public void greetingsAndThanks_butAGreetingNeverHidesTheQuestion() {
        assertEquals(AskIntent.GREETING, ask("hi").intent);
        assertEquals(AskIntent.THANKS, ask("thank you").intent);
        assertEquals(AskIntent.GATE, ask("hi, where is my gate?").intent);
        assertEquals(AskIntent.GREETING, ask("नमस्ते").intent);
    }

    @Test
    public void unknownQuestion_suggestsWhatToAsk() {
        Answer answer = ask("What is my seat number?");
        assertEquals(AskIntent.UNKNOWN, answer.intent);
        assertTrue(answer.text, answer.text.contains("Where can I eat?"));
    }

    @Test
    public void everyNewAnswer_isFilledInEveryLanguage() {
        String[] questions = {"My bag is damaged", "My bag didn't arrive", "Something was stolen from my bag",
                "I lost my phone", "Where can I eat?", "Where is security?", "Where is check-in?",
                "Where is the buggy stop?", "Where is baggage services?", "I need a wheelchair",
                "Can I carry a power bank?", "What is my baggage allowance?", "How do I get to the airport?", "hi",
                "thank you", "What is my seat number?"};
        for (AskLanguage language : AskLanguage.values()) {
            for (String question : questions) {
                Answer answer = engine.ask(question, language, LanguageHint.NONE, demoFacts());
                assertFalse(language + " / " + question + ": " + answer.text, answer.text.contains("{"));
                assertFalse(language + " / " + question, answer.text.trim().isEmpty());
            }
        }
    }
}
