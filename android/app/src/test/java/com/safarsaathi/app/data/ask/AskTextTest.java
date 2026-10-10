package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class AskTextTest {

    @Test
    public void lowercase_punctuationGone_spacesSingle() {
        assertEquals("mera gate kahan hai", AskText.normalize("  Mera GATE, kahan hai?? "));
    }

    @Test
    public void tripleLetters_countOnce_doubleLettersStay() {
        assertEquals("kahan", AskText.normalize("kahaaan"));
        assertEquals("baggage aahe", AskText.normalize("baggage aahe"));
    }

    @Test
    public void devanagari_nasalMarksAndNuktaVariants_lookAlike() {
        // कहाँ (chandrabindu), कहां (anusvara) and कहा are typed interchangeably.
        assertEquals(AskText.normalize("कहा"), AskText.normalize("कहाँ"));
        assertEquals(AskText.normalize("कहा"), AskText.normalize("कहां"));
        // फ़्लाइट with and without the nukta dot.
        assertEquals(AskText.normalize("फ्लाइट"), AskText.normalize("फ़्लाइट"));
    }

    @Test
    public void devanagari_vowelSignsStayInsideTheWord_dandaIsPunctuation() {
        assertEquals(Arrays.asList("माझी", "बॅग", "कुठे", "आहे"), AskText.words("माझी बॅग कुठे आहे।"));
    }

    @Test
    public void devanagariDigits_becomeAsciiDigits() {
        assertEquals("गेट 32", AskText.normalize("गेट ३२"));
    }

    @Test
    public void accents_doNotSplitWords() {
        assertEquals("cafe", AskText.normalize("café"));
    }

    @Test
    public void script_isMostlyDevanagari() {
        assertTrue(AskText.isMostlyDevanagari("मेरा gate कहाँ है"));
        assertFalse(AskText.isMostlyDevanagari("Mera gate kahan hai"));
        assertFalse(AskText.isMostlyDevanagari(null));
    }

    @Test
    public void nullAndBlank_giveNoWords() {
        assertEquals("", AskText.normalize(null));
        assertTrue(AskText.words("  ?! ").isEmpty());
    }
}
