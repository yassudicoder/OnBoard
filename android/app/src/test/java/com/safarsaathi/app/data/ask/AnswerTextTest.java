package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Every reply language has every sentence, with the same placeholders as English, so code can fill them alike. */
public class AnswerTextTest {

    @Test
    public void everyLanguage_hasExactlyTheEnglishKeys() {
        Set<String> english = new TreeSet<>(AnswerText.of(AskLanguage.EN).sentences().stringPropertyNames());
        for (AskLanguage language : AskLanguage.values()) {
            Set<String> own = new TreeSet<>(AnswerText.of(language).sentences().stringPropertyNames());
            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(own);
            Set<String> extra = new TreeSet<>(own);
            extra.removeAll(english);
            assertTrue(language + " is missing " + missing, missing.isEmpty());
            assertTrue(language + " has unknown keys " + extra, extra.isEmpty());
        }
    }

    @Test
    public void everySentence_hasTheSamePlaceholdersInEveryLanguage() {
        AnswerText english = AnswerText.of(AskLanguage.EN);
        for (AskLanguage language : AskLanguage.values()) {
            AnswerText own = AnswerText.of(language);
            for (String key : english.sentences().stringPropertyNames()) {
                assertEquals(language + " " + key, sorted(AnswerText.placeholders(english.get(key))),
                        sorted(AnswerText.placeholders(own.get(key))));
            }
        }
    }

    @Test
    public void noSentence_hasADigitOtherThanTerminal2() {
        // GOLDEN RULE: gates, times and belts come from data, never from the sentence. The only number allowed is the
        // terminal's name ("Terminal 2", "टर्मिनल 2").
        for (AskLanguage language : AskLanguage.values()) {
            AnswerText text = AnswerText.of(language);
            for (String key : text.sentences().stringPropertyNames()) {
                String withoutTerminal = text.get(key).replaceAll("(Terminal|टर्मिनल) 2", "");
                assertTrue(language + " " + key + " has a number: " + text.get(key),
                        !withoutTerminal.matches(".*[0-9].*"));
            }
        }
    }

    @Test
    public void fill_putsValuesIn() {
        assertEquals("Your gate for IJ 512 is 32.",
                AnswerText.of(AskLanguage.EN).fill("gate.known", "flight", "IJ 512", "gate", "32"));
    }

    @Test
    public void fill_withAMissingValue_throws_soNoReplyEverShowsAPlaceholder() {
        try {
            AnswerText.of(AskLanguage.HI).fill("gate.known", "flight", "IJ 512");
            fail("a sentence must never be shown with {gate} in it");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("gate"));
        }
    }

    private static List<String> sorted(List<String> names) {
        List<String> copy = new ArrayList<>(names);
        Collections.sort(copy);
        return copy;
    }
}
