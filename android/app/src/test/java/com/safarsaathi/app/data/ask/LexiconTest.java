package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class LexiconTest {

    private final Lexicon lexicon = Lexicon.get();

    private Set<Concept> concepts(String question) {
        Set<Concept> found = EnumSet.noneOf(Concept.class);
        for (Lexicon.Hit hit : lexicon.find(AskText.words(question))) {
            found.addAll(hit.concepts);
        }
        return found;
    }

    @Test
    public void allFiles_loadWithoutUnknownConcepts() {
        assertEquals(1, lexicon.find(AskText.words("gate")).size());
    }

    @Test
    public void longestPhraseFirst_eachWordUsedOnce() {
        // "help desk" is one HELP_DESK, not HELP + something.
        assertEquals(EnumSet.of(Concept.WHERE, Concept.HELP_DESK), concepts("where is the help desk"));
        // "on time" is ON_TIME, not WHEN.
        assertEquals(EnumSet.of(Concept.FLIGHT, Concept.ON_TIME), concepts("is my flight on time"));
    }

    @Test
    public void marathiEndings_areCutOff() {
        assertEquals(EnumSet.of(Concept.GATE), concepts("गेटवर"));
        assertEquals(EnumSet.of(Concept.FLIGHT), concepts("फ्लाइटचे"));
        assertEquals(EnumSet.of(Concept.FLIGHT), concepts("flightchi"));
        assertEquals(EnumSet.of(Concept.PASSPORT), concepts("पासपोर्टची"));
    }

    @Test
    public void smallTypos_inLongWords_areForgiven() {
        assertEquals(EnumSet.of(Concept.BOARDING), concepts("bording"));
        assertEquals(EnumSet.of(Concept.BAG), concepts("baggege"));
    }

    @Test
    public void shortWords_mustMatchExactly() {
        // "kab" (when) must never become "cab".
        assertEquals(EnumSet.of(Concept.WHEN), concepts("kab"));
        assertTrue(Collections.disjoint(concepts("kab"), EnumSet.of(Concept.CAB)));
    }

    @Test
    public void markerWords_areNeverTypos() {
        // "there" is an English marker, so it is not read as a typo of "where".
        assertTrue(concepts("is there a delay").contains(Concept.DELAY));
        assertFalse(concepts("is there a delay").contains(Concept.WHERE));
    }

    @Test
    public void get_isOnlyAPhrase_soEnglishGetIsNotGate() {
        assertEquals(EnumSet.of(Concept.CAB), concepts("get me a cab"));
        assertEquals(EnumSet.of(Concept.GATE, Concept.WHERE), concepts("mera get kidhar hai"));
    }

    @Test
    public void markerCount_countsExactWords() {
        List<String> words = AskText.words("Majhi bag kuthe aahe?");
        assertEquals(3, lexicon.markerCount("mr_latn", words));
        assertEquals(0, lexicon.markerCount("hinglish", words));
    }

    @Test
    public void unknownConceptInAFile_failsLoudly() {
        Properties broken = new Properties();
        broken.setProperty("GAET", "gate");
        try {
            Lexicon.fromFiles(Collections.singletonMap("en", broken));
            fail("a typo in a concept name must not be ignored");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("GAET"));
        }
    }

    @Test
    public void phrasesLongerThanFourWords_failLoudly() {
        Properties broken = new Properties();
        broken.setProperty("GATE", "where is my gate now please");
        try {
            Lexicon.fromFiles(Collections.singletonMap("en", broken));
            fail("phrases over 4 words are never tried, so they must not be accepted");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    @Test
    public void editDistance_countsSwapsAsOne() {
        assertEquals(1, Lexicon.distance("baord", "board", 2));
        assertEquals(1, Lexicon.distance("gat", "gate", 2));
        assertEquals(3, Lexicon.distance("abcdef", "uvwxyz", 2)); // gives up above the limit
    }
}
