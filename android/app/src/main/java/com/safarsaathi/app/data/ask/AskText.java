package com.safarsaathi.app.data.ask;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Turns a typed or spoken question into simple lowercase words, the same way for questions and for lexicon entries,
 * so "Mera GATE kahaan hai??" and "mera gate kahan hai" look alike to the {@link IntentMatcher}.
 *
 * <p>What it does, in order:
 * <ul>
 *   <li>Unicode NFC, so the same Devanagari letter typed two ways becomes one form.</li>
 *   <li>Lowercase (English letters).</li>
 *   <li>Devanagari spelling variants that people mix freely: the nukta dot (फ़ = फ), the chandrabindu and anusvara
 *       nasal marks (कहाँ = कहां = कहा), and invisible joiners are dropped. Devanagari digits become 0-9.</li>
 *   <li>Punctuation (also the Devanagari full stop "।") becomes a space.</li>
 *   <li>The same English letter typed 3 or more times in a row counts once ("kahaaan" = "kahan").</li>
 * </ul>
 * Plain Java, checked by AskTextTest.
 */
public final class AskText {

    private static final char NUKTA = '़';
    private static final char CHANDRABINDU = 'ँ';
    private static final char ANUSVARA = 'ं';
    private static final char ZERO_WIDTH_NON_JOINER = '‌';
    private static final char ZERO_WIDTH_JOINER = '‍';

    private AskText() {
    }

    /** The question as clean words separated by single spaces ("" for null or blank). */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        // NFC keeps "फ़" as one letter (U+095E); NFD splits it into फ + nukta, so the nukta can be dropped.
        String decomposed = Normalizer.normalize(nfc, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (c == NUKTA || c == CHANDRABINDU || c == ANUSVARA
                    || c == ZERO_WIDTH_NON_JOINER || c == ZERO_WIDTH_JOINER) {
                continue;
            }
            if (c >= '०' && c <= '९') {
                out.append((char) ('0' + (c - '०'))); // Devanagari digit
            } else if (Character.isLetterOrDigit(c) || isDevanagariSign(c)) {
                out.append(c);
            } else if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue; // an accent split off by NFD ("café" -> "cafe"), not a word break
            } else {
                out.append(' '); // punctuation, symbols, "।", line breaks
            }
        }
        String recomposed = Normalizer.normalize(out.toString(), Normalizer.Form.NFC);
        return collapseRepeats(recomposed).trim().replaceAll(" +", " ");
    }

    /** The question's words, after {@link #normalize}. */
    public static List<String> words(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> words = new ArrayList<>();
        Collections.addAll(words, normalized.split(" "));
        return words;
    }

    /** True when the text has more Devanagari letters than English letters. */
    public static boolean isMostlyDevanagari(String text) {
        if (text == null) {
            return false;
        }
        int devanagari = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'ऀ' && c <= 'ॿ') {
                devanagari++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
            }
        }
        return devanagari > latin;
    }

    /**
     * Vowel signs and the virama are "marks", not letters, in Java; without this they would become spaces and break
     * every Devanagari word apart.
     */
    private static boolean isDevanagariSign(char c) {
        return c >= 'ऀ' && c <= 'ॿ' && Character.getType(c) != Character.OTHER_PUNCTUATION;
    }

    /** "kahaaan" -> "kahan": a run of 3+ identical English letters becomes one (2 stay: "baggage", "aahe"). */
    private static String collapseRepeats(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int run = 1;
            while (i + run < text.length() && text.charAt(i + run) == c) {
                run++;
            }
            boolean latinLetter = c >= 'a' && c <= 'z';
            if (latinLetter && run >= 3) {
                out.append(c);
            } else {
                for (int k = 0; k < run; k++) {
                    out.append(c);
                }
            }
            i += run;
        }
        return out.toString();
    }
}
