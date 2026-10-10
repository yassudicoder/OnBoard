package com.safarsaathi.routing.route;

import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.MapFloor;
import com.safarsaathi.routing.map.MapNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The words for route steps, in English, Hindi or Marathi, e.g. "Turn left at Central hall" / "मुख्य हॉल पर बाएँ
 * मुड़ें" / "मुख्य दालन जवळ डावीकडे वळा".
 *
 * <p>The sentences live in resource files next to this class ({@code instructions_en.properties}, {@code _hi},
 * {@code _mr}), so a translator edits text, not code. In a sentence, {0} is the place's name in the same language
 * and {1} the floor's name. A language without a file uses English.
 */
public final class InstructionText {

    private static final Map<String, InstructionText> CACHE = new HashMap<>();

    private final String language;
    private final Properties templates;

    private InstructionText(String language, Properties templates) {
        this.language = language;
        this.templates = templates;
    }

    /** The sentences for a language code ("en", "hi", "mr"); English when there is no file for it. */
    public static synchronized InstructionText forLanguage(String language) {
        String key = language == null ? "en" : language;
        InstructionText cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Properties templates = load(key);
        InstructionText text = templates == null ? forEnglish() : new InstructionText(key, templates);
        CACHE.put(key, text);
        return text;
    }

    private static InstructionText forEnglish() {
        Properties english = load("en");
        if (english == null) {
            throw new IllegalStateException("instructions_en.properties is missing from routing-core");
        }
        return new InstructionText("en", english);
    }

    private static Properties load(String language) {
        try (InputStream in = InstructionText.class.getResourceAsStream("instructions_" + language + ".properties")) {
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

    /** The language these sentences are in (the requested one, or "en" when it had no file). */
    public String language() {
        return language;
    }

    /** The instruction for a step, e.g. "Take Lift A down to Arrivals". */
    public String text(RouteStep step, AirportMap map) {
        String floorName = "";
        if (step.toFloor != null) {
            MapFloor floor = map.floor(step.toFloor);
            floorName = floor == null ? step.toFloor : floor.name(language);
        }
        return fill(template(step.maneuver.key()), step.landmark, floorName);
    }

    /** "You have arrived at Gate 32". */
    public String arrived(MapNode destination) {
        return fill(template("arrived"), destination, "");
    }

    private String template(String key) {
        String template = templates.getProperty(key);
        if (template == null) {
            throw new IllegalStateException("No instruction '" + key + "' for language " + language);
        }
        return template;
    }

    private String fill(String template, MapNode place, String floorName) {
        return template.replace("{0}", place == null ? "" : place.name(language)).replace("{1}", floorName);
    }
}
