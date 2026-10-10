package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The golden phrase set (golden_phrases.tsv next to this test): at least 40 questions in English, Hindi, Marathi and
 * Hinglish, each with the intent and reply language we expect. The Quick answers must get 90% or better of both
 * right. Misses are printed, so a failing run shows what to add to the lexicon files.
 *
 * <p>Also checks the quick-question chips on the Ask screen: those are 100%, because tapping one must never give the
 * wrong answer.
 */
public class IntentGoldenSetTest {

    private static final double TARGET = 0.90;
    private static final int MIN_PHRASES = 40;

    private final AskEngine engine = AskEngine.standard();

    private static final class Golden {
        final AskIntent intent;
        final AskLanguage language;
        final String question;

        Golden(AskIntent intent, AskLanguage language, String question) {
            this.intent = intent;
            this.language = language;
            this.question = question;
        }
    }

    @Test
    public void goldenSet_intentsAndLanguages_atLeast90Percent() throws IOException {
        List<Golden> phrases = load();
        assertTrue("need " + MIN_PHRASES + "+ phrases, have " + phrases.size(), phrases.size() >= MIN_PHRASES);

        int intentsRight = 0;
        int languagesRight = 0;
        List<String> misses = new ArrayList<>();
        for (Golden golden : phrases) {
            IntentMatch match = engine.understand(golden.question);
            AskLanguage language = engine.language(golden.question, null, LanguageHint.NONE);
            if (match.intent == golden.intent) {
                intentsRight++;
            } else {
                misses.add("intent   " + golden.question + " -> " + match + ", expected " + golden.intent);
            }
            if (language == golden.language) {
                languagesRight++;
            } else {
                misses.add("language " + golden.question + " -> " + language + ", expected " + golden.language);
            }
        }
        double intentScore = intentsRight / (double) phrases.size();
        double languageScore = languagesRight / (double) phrases.size();
        System.out.printf(Locale.ROOT, "Golden set: %d phrases, intents %.1f%%, languages %.1f%%%n",
                phrases.size(), intentScore * 100, languageScore * 100);
        for (String miss : misses) {
            System.out.println("  MISS " + miss);
        }
        assertTrue("intent accuracy " + intentScore + " below " + TARGET + ": " + misses, intentScore >= TARGET);
        assertTrue("language accuracy " + languageScore + " below " + TARGET + ": " + misses,
                languageScore >= TARGET);
    }

    @Test
    public void goldenSet_coversAllFourLanguagesAndEveryQuickIntent() throws IOException {
        List<Golden> phrases = load();
        for (AskLanguage language : AskLanguage.values()) {
            int count = 0;
            for (Golden golden : phrases) {
                count += golden.language == language ? 1 : 0;
            }
            assertTrue(language + " has only " + count + " phrases", count >= 10);
        }
        for (AskIntent intent : AskIntent.values()) {
            if (!intent.quickAnswer) {
                continue;
            }
            boolean covered = false;
            for (Golden golden : phrases) {
                covered |= golden.intent == intent;
            }
            assertTrue(intent + " has no golden phrase", covered);
        }
    }

    @Test
    public void quickQuestionChips_alwaysGiveTheirOwnAnswer_inTheirOwnLanguage() {
        String[][] chips = {
                {"chip.gate", "GATE"},
                {"chip.boarding_close", "BOARDING_CLOSE"},
                {"chip.bag", "BAG_STATUS"},
                {"chip.flight", "FLIGHT_STATUS"},
                {"chip.washroom", "WHERE_IS"},
                {"chip.next", "NEXT_STEP"}};
        for (AskLanguage language : AskLanguage.values()) {
            AnswerText text = AnswerText.of(language);
            for (String[] chip : chips) {
                String question = text.get(chip[0]);
                IntentMatch match = engine.understand(question);
                assertEquals(language + " " + question, AskIntent.valueOf(chip[1]), match.intent);
                assertEquals(language + " " + question, IntentMatch.Confidence.HIGH, match.confidence);
                assertEquals(question, language, engine.language(question, null, LanguageHint.NONE));
            }
        }
    }

    private static List<Golden> load() throws IOException {
        List<Golden> phrases = new ArrayList<>();
        try (InputStream in = IntentGoldenSetTest.class.getResourceAsStream("golden_phrases.tsv")) {
            if (in == null) {
                throw new IllegalStateException("golden_phrases.tsv is missing");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t");
                if (columns.length != 3) {
                    throw new IllegalStateException("Expected 3 tab-separated columns: " + line);
                }
                phrases.add(new Golden(AskIntent.valueOf(columns[0]), AskLanguage.valueOf(columns[1]), columns[2]));
            }
        }
        return phrases;
    }
}
