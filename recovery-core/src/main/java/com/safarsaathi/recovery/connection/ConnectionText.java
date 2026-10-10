package com.safarsaathi.recovery.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.safarsaathi.recovery.rules.ConnectionType;
import com.safarsaathi.recovery.rules.ValueKind;

/**
 * The words for connection reasons in one language. The sentences live in resource files next to this class
 * ({@code connection_text_en.properties}; Hindi and Marathi follow in C3/C4), so a translator edits text, not code.
 * A language without a file uses English. Same idea as routing-core's InstructionText.
 */
final class ConnectionText {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, ConnectionText> CACHE = new HashMap<>();

    private final String language;
    private final Properties templates;
    private final DateTimeFormatter dayFormat;

    private ConnectionText(String language, Properties templates) {
        this.language = language;
        this.templates = templates;
        this.dayFormat = DateTimeFormatter.ofPattern(templates.getProperty("date_pattern", "d MMM"),
                Locale.forLanguageTag(language));
    }

    /** The sentences for a language code ("en", "hi", "mr"); English when there is no file for it. */
    static synchronized ConnectionText forLanguage(String language) {
        String key = language == null ? "en" : language;
        ConnectionText cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Properties templates = load(key);
        ConnectionText text;
        if (templates != null) {
            text = new ConnectionText(key, templates);
        } else {
            Properties english = load("en");
            if (english == null) {
                throw new IllegalStateException("connection_text_en.properties is missing from recovery-core");
            }
            text = new ConnectionText("en", english);
        }
        CACHE.put(key, text);
        return text;
    }

    private static Properties load(String language) {
        try (InputStream in = ConnectionText.class.getResourceAsStream(
                "connection_text_" + language + ".properties")) {
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
    String language() {
        return language;
    }

    /**
     * The sentence {@code key} with its {name} parts filled in. {@code namesAndValues} = "city", "Doha",
     * "lands", "21:10", ... Plain text replacement on purpose: Java's MessageFormat would treat the apostrophe
     * in "can't" as a special character.
     */
    String sentence(String key, String... namesAndValues) {
        String sentence = templates.getProperty(key);
        if (sentence == null) {
            throw new IllegalStateException("No sentence \"" + key + "\" in connection_text properties");
        }
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            sentence = sentence.replace("{" + namesAndValues[i] + "}", namesAndValues[i + 1]);
        }
        return sentence;
    }

    /** " (demo value)", " (estimate)", or "" for an official value. */
    String label(ValueKind kind) {
        return kind == null ? "" : templates.getProperty("label_" + kind.name(), "");
    }

    /** "international to international"; "this kind of connection" when the type is not known. */
    String typeName(ConnectionType type) {
        return templates.getProperty(type == null ? "type_unknown" : "type_" + type.name());
    }

    /** "21:55", or "00:40 on 12 Oct" when the time is not on {@code referenceDay} (both in the same local zone). */
    String time(ZonedDateTime time, LocalDate referenceDay) {
        String clock = CLOCK.format(time);
        if (time.toLocalDate().equals(referenceDay)) {
            return clock;
        }
        return sentence("time_on_day", "time", clock, "date", dayFormat.format(time));
    }
}
