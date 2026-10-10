package com.safarsaathi.app.data.ask;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The words Ask OnBoard understands, read from one file per way of writing (next to this class):
 * <ul>
 *   <li>{@code lexicon_en.properties}: English;</li>
 *   <li>{@code lexicon_hi.properties}: Hindi in Devanagari ("मेरा गेट कहाँ है");</li>
 *   <li>{@code lexicon_mr.properties}: Marathi in Devanagari ("माझी बॅग कुठे आहे");</li>
 *   <li>{@code lexicon_hinglish.properties}: Hindi in English letters ("mera gate kahan hai");</li>
 *   <li>{@code lexicon_mr_latn.properties}: Marathi in English letters ("majhi bag kuthe aahe").</li>
 * </ul>
 * Each line is {@code CONCEPT = word, short phrase, ...}. The special line {@code MARKERS} lists everyday words that
 * show which language a question is in ("hai", "aahe", "the"); {@link LanguagePicker} counts them.
 *
 * <p>All files are used together when looking for concepts, because travellers mix languages ("Doha connection miss
 * hoga kya?"). Finding concepts in a question ({@link #find}):
 * <ol>
 *   <li>longest phrase first: "help desk" is one HELP_DESK, not HELP + something; every word is used once;</li>
 *   <li>a word with a grammar ending: Marathi glues words together ("गेटवर" = on the gate, "flightchi" = of the
 *       flight), so a known ending is cut off and the rest looked up;</li>
 *   <li>small typos: a word of 5+ letters may differ by one letter from a lexicon word ("bording", "baggege"), 9+
 *       letters by two. Shorter words must match exactly, or "kab" (when) would turn into "cab".</li>
 * </ol>
 */
public final class Lexicon {

    /** The lexicon files, by name ("lexicon_" + name + ".properties"). */
    public static final String[] FILES = {"en", "hi", "mr", "hinglish", "mr_latn"};

    static final String MARKERS = "MARKERS";

    /** Longest phrase allowed in the files, in words (a longer one fails the tests). */
    private static final int MAX_PHRASE_WORDS = 4;

    /**
     * Grammar endings glued to a word, Marathi mostly ("चा/ची/चे" = of, "वर" = on, "मध्ये" = in, "ला" = to), in
     * Devanagari and in English letters. Longest first, so "च्या" is tried before "चा".
     */
    private static final String[] ENDINGS = {
            "मध्ये", "पर्यंत", "साठी", "ाच्या", "ेच्या", "च्या", "कडे", "ाचा", "ाची", "ाचे", "ेचा", "ेची", "ेचे",
            "ाला", "ेला", "ाने", "ावर", "ेवर", "ात", "ेत", "चा", "ची", "चे", "ला", "ना", "वर", "ने", "त",
            "madhye", "madhe", "paryant", "sathi", "echya", "chya", "kade", "echa", "echi", "eche", "cha", "chi",
            "che", "var", "la", "ne"};

    private static Lexicon cached;

    /** Normalized phrase -> the concepts it stands for. */
    private final Map<String, Set<Concept>> phrases;
    /** Single-word entries, the ones a typo may be forgiven for. */
    private final List<String> singleWords;
    /** File name -> its marker words (normalized). */
    private final Map<String, Set<String>> markers;

    private Lexicon(Map<String, Set<Concept>> phrases, Map<String, Set<String>> markers) {
        this.phrases = phrases;
        this.markers = markers;
        List<String> words = new ArrayList<>();
        for (String phrase : phrases.keySet()) {
            if (phrase.indexOf(' ') < 0) {
                words.add(phrase);
            }
        }
        this.singleWords = words;
    }

    /** The lexicon from the files next to this class (read once, then kept). */
    public static synchronized Lexicon get() {
        if (cached == null) {
            Map<String, Properties> files = new LinkedHashMap<>();
            for (String name : FILES) {
                files.put(name, load("lexicon_" + name + ".properties"));
            }
            cached = fromFiles(files);
        }
        return cached;
    }

    /**
     * Builds a lexicon from already-read files (tests use this). An unknown concept name throws, so a typo in a file
     * fails the tests instead of silently ignoring a line.
     */
    static Lexicon fromFiles(Map<String, Properties> files) {
        Map<String, Set<Concept>> phrases = new HashMap<>();
        Map<String, Set<String>> markers = new HashMap<>();
        for (Map.Entry<String, Properties> file : files.entrySet()) {
            Set<String> fileMarkers = new HashSet<>();
            for (String key : file.getValue().stringPropertyNames()) {
                List<String> entries = entries(file.getValue().getProperty(key));
                if (MARKERS.equals(key)) {
                    fileMarkers.addAll(entries);
                    continue;
                }
                Concept concept;
                try {
                    concept = Concept.valueOf(key.trim());
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("lexicon_" + file.getKey() + ": unknown concept " + key, e);
                }
                for (String entry : entries) {
                    if (entry.split(" ").length > MAX_PHRASE_WORDS) {
                        throw new IllegalStateException("lexicon_" + file.getKey() + ": phrase too long: " + entry);
                    }
                    Set<Concept> concepts = phrases.get(entry);
                    if (concepts == null) {
                        concepts = EnumSet.noneOf(Concept.class);
                        phrases.put(entry, concepts);
                    }
                    concepts.add(concept);
                }
            }
            markers.put(file.getKey(), fileMarkers);
        }
        return new Lexicon(phrases, markers);
    }

    /** One concept (or a few, for phrases listed under two concepts) found in the question. */
    public static final class Hit {
        public final Set<Concept> concepts;
        /** Position of the hit's first word in the question (to know which place came first, say). */
        public final int position;
        /** The question's word(s) that matched. */
        public final String text;

        Hit(Set<Concept> concepts, int position, String text) {
            this.concepts = concepts;
            this.position = position;
            this.text = text;
        }

        @Override
        public String toString() {
            return text + "=" + concepts;
        }
    }

    /** The concepts in these (normalized) words, in the order they appear. See the class comment for the rules. */
    public List<Hit> find(List<String> words) {
        List<Hit> hits = new ArrayList<>();
        int i = 0;
        while (i < words.size()) {
            int used = 0;
            // 1. Longest phrase first.
            for (int length = Math.min(MAX_PHRASE_WORDS, words.size() - i); length >= 1; length--) {
                String phrase = join(words, i, length);
                Set<Concept> concepts = phrases.get(phrase);
                if (concepts != null) {
                    hits.add(new Hit(concepts, i, phrase));
                    used = length;
                    break;
                }
            }
            if (used == 0) {
                // 2. and 3.: a single word with a grammar ending or a small typo.
                String word = words.get(i);
                String known = withoutEnding(word);
                if (known == null) {
                    known = closeSpelling(word);
                }
                if (known != null) {
                    hits.add(new Hit(phrases.get(known), i, word));
                }
                used = 1;
            }
            i += used;
        }
        return hits;
    }

    /** How many of these words are markers of the given file's language (exact matches only). */
    public int markerCount(String file, List<String> words) {
        Set<String> fileMarkers = markers.get(file);
        if (fileMarkers == null) {
            return 0;
        }
        int count = 0;
        for (String word : words) {
            if (fileMarkers.contains(word)) {
                count++;
            }
        }
        return count;
    }

    /** The lexicon word left after cutting a known grammar ending off, or null. */
    private String withoutEnding(String word) {
        for (String ending : ENDINGS) {
            if (word.length() > ending.length() + 2 && word.endsWith(ending)) {
                String stem = word.substring(0, word.length() - ending.length());
                if (phrases.containsKey(stem)) {
                    return stem;
                }
            }
        }
        return null;
    }

    /**
     * The lexicon word this one is a small typo of, or null. Ties go to the first word found. A marker word is a real
     * word of its own, never a typo ("there" is not "where").
     */
    private String closeSpelling(String word) {
        if (word.length() < 5 || isNumber(word) || isMarker(word)) {
            return null;
        }
        int allowed = word.length() >= 9 ? 2 : 1;
        String best = null;
        int bestDistance = allowed + 1;
        for (String candidate : singleWords) {
            if (Math.abs(candidate.length() - word.length()) > allowed || candidate.length() < 4) {
                continue;
            }
            int distance = distance(word, candidate, allowed);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Edit distance (letters added, removed, changed, or two neighbours swapped), giving up early once it is
     * certainly more than {@code limit}.
     */
    static int distance(String a, String b, int limit) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, d[i - 2][j - 2] + 1);
                }
                d[i][j] = value;
                rowMin = Math.min(rowMin, value);
            }
            if (rowMin > limit) {
                return limit + 1;
            }
        }
        return d[a.length()][b.length()];
    }

    private boolean isMarker(String word) {
        for (Set<String> fileMarkers : markers.values()) {
            if (fileMarkers.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumber(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (!Character.isDigit(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String join(List<String> words, int from, int length) {
        StringBuilder phrase = new StringBuilder(words.get(from));
        for (int k = 1; k < length; k++) {
            phrase.append(' ').append(words.get(from + k));
        }
        return phrase.toString();
    }

    /** "gate, Gate No., boarding gate" -> normalized entries, empty ones dropped. */
    private static List<String> entries(String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> entries = new ArrayList<>();
        for (String part : value.split(",")) {
            String entry = AskText.normalize(part);
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** Reads a UTF-8 .properties file next to this class (Java's default for .properties is Latin-1). */
    static Properties load(String fileName) {
        try (InputStream in = Lexicon.class.getResourceAsStream(fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " is missing from the app");
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + fileName, e);
        }
    }
}
