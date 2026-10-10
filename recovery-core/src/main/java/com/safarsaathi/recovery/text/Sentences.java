package com.safarsaathi.recovery.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Sentences in one language from a properties file next to a class, e.g. {@code alternative_text_en.properties} next
 * to AlternativeRanker. A translator edits text, not code; a language without a file uses English.
 *
 * <p>In a sentence, {name} is filled in by {@link #get(String, String...)}. Plain text replacement on purpose: Java's
 * MessageFormat would treat the apostrophe in "can't" as a special character.
 */
public final class Sentences {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, Sentences> CACHE = new HashMap<>();

    private final String language;
    private final Properties templates;
    private final DateTimeFormatter dayFormat;
    private final NumberFormat amounts;

    private Sentences(String language, Properties templates) {
        this.language = language;
        this.templates = templates;
        Locale locale = Locale.forLanguageTag(language);
        this.dayFormat = DateTimeFormatter.ofPattern(templates.getProperty("date_pattern", "d MMM"), locale);
        // Indian grouping (1,23,456) for rupee amounts, in every language.
        this.amounts = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"));
    }

    /**
     * The sentences of {@code baseName} ("alternative_text") next to {@code owner}, in {@code language} ("en", "hi",
     * "mr"); English when that language has no file.
     */
    public static synchronized Sentences load(Class<?> owner, String baseName, String language) {
        String lang = language == null ? "en" : language;
        String key = owner.getName() + "/" + baseName + "/" + lang;
        Sentences cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Properties templates = read(owner, baseName, lang);
        Sentences sentences;
        if (templates != null) {
            sentences = new Sentences(lang, templates);
        } else {
            Properties english = read(owner, baseName, "en");
            if (english == null) {
                throw new IllegalStateException(baseName + "_en.properties is missing next to " + owner.getName());
            }
            sentences = new Sentences("en", english);
        }
        CACHE.put(key, sentences);
        return sentences;
    }

    private static Properties read(Class<?> owner, String baseName, String language) {
        try (InputStream in = owner.getResourceAsStream(baseName + "_" + language + ".properties")) {
            if (in == null) {
                return null;
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException e) {
            return null;
        }
    }

    /** The language of these sentences: the requested one, or "en" when it had no file. */
    public String language() {
        return language;
    }

    /** The sentence {@code key} with its {name} parts filled in: get("arrives", "city", "London", ...). */
    public String get(String key, String... namesAndValues) {
        String sentence = templates.getProperty(key);
        if (sentence == null) {
            throw new IllegalStateException("No sentence \"" + key + "\" for language " + language);
        }
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            String value = namesAndValues[i + 1];
            sentence = sentence.replace("{" + namesAndValues[i] + "}", value == null ? "" : value);
        }
        return sentence;
    }

    /** "21:55", or "00:40 on 12 Oct" when the time is not on {@code referenceDay} (both in the same local zone). */
    public String time(ZonedDateTime time, LocalDate referenceDay) {
        String clock = CLOCK.format(time);
        if (referenceDay != null && time.toLocalDate().equals(referenceDay)) {
            return clock;
        }
        return get("time_on_day", "time", clock, "date", dayFormat.format(time));
    }

    /** Always with the day: "07:40 on 12 Oct". */
    public String timeWithDay(ZonedDateTime time) {
        return time(time, null);
    }

    /** "45 min", "9 h", "9 h 45 min". */
    public String duration(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return get("duration_minutes", "minutes", String.valueOf(rest));
        }
        if (rest == 0) {
            return get("duration_hours", "hours", String.valueOf(hours));
        }
        return get("duration_hours_minutes", "hours", String.valueOf(hours), "minutes", String.valueOf(rest));
    }

    /** "46,300" or "1,23,456" (rupees, Indian grouping). */
    public String amount(int rupees) {
        synchronized (amounts) { // NumberFormat is not thread-safe
            return amounts.format(rupees);
        }
    }
}
