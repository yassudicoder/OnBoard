package com.safarsaathi.app.data.ask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed answer sentences, one file per reply language next to this class ({@code answers_en.properties},
 * {@code _hi}, {@code _mr}, {@code _hinglish}), so a translator edits text, not code.
 *
 * <p>GOLDEN RULE: a sentence never contains a gate, time, belt or flight number itself. It has placeholders such as
 * {@code {gate}} and {@code {close}}, and code fills them from the phone's saved data
 * ({@link #fill}). When the data is missing, code picks the "not announced" sentence instead.
 *
 * <p>Placeholders use {@code {name}} rather than Java's MessageFormat {@code {0}}: names are easier for translators,
 * and MessageFormat treats an apostrophe ("don't") as a special character. A sentence missing in one language falls
 * back to English; AnswerTextTest checks that every language has every sentence with the same placeholders.
 */
public final class AnswerText {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)\\}");
    private static final Map<AskLanguage, AnswerText> CACHE = new EnumMap<>(AskLanguage.class);

    private final AskLanguage language;
    private final Properties sentences;
    private final Properties english;

    private AnswerText(AskLanguage language, Properties sentences, Properties english) {
        this.language = language;
        this.sentences = sentences;
        this.english = english;
    }

    /** The sentences for a reply language (read once, then kept). */
    public static synchronized AnswerText of(AskLanguage language) {
        AnswerText cached = CACHE.get(language);
        if (cached == null) {
            Properties english = Lexicon.load("answers_" + AskLanguage.EN.code + ".properties");
            Properties own = language == AskLanguage.EN
                    ? english
                    : Lexicon.load("answers_" + language.code + ".properties");
            cached = new AnswerText(language, own, english);
            CACHE.put(language, cached);
        }
        return cached;
    }

    public AskLanguage language() {
        return language;
    }

    /** The sentence for a key, with no placeholders filled ("" never: a missing key throws). */
    public String get(String key) {
        String text = sentences.getProperty(key);
        if (text == null) {
            text = english.getProperty(key);
        }
        if (text == null) {
            throw new IllegalArgumentException("No answer sentence \"" + key + "\" in answers_en.properties");
        }
        return text.trim();
    }

    /** True when this key exists (in this language or in English). */
    public boolean has(String key) {
        return sentences.getProperty(key) != null || english.getProperty(key) != null;
    }

    /**
     * The sentence for a key, with its placeholders filled. {@code values} are name/value pairs:
     * {@code fill("gate.known", "flight", "IJ 512", "gate", "32")}. A placeholder without a value throws, so a
     * sentence can never be shown with "{gate}" in it.
     */
    public String fill(String key, String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("values must be name/value pairs");
        }
        String sentence = get(key);
        Matcher matcher = PLACEHOLDER.matcher(sentence);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = null;
            for (int i = 0; i < values.length; i += 2) {
                if (values[i].equals(name)) {
                    value = values[i + 1];
                }
            }
            if (value == null) {
                throw new IllegalArgumentException("No value for {" + name + "} in \"" + key + "\"");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The placeholder names in a sentence, in order (for the consistency test). */
    static List<String> placeholders(String sentence) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(sentence);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** The raw file (tests compare the keys of all languages). */
    Properties sentences() {
        return sentences;
    }
}
