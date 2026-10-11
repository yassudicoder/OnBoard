package com.safarsaathi.app.data.baggage;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * The checked facts behind the Help tab, read from {@code baggage-facts.json} (repo-root mock-data/baggage, shipped
 * inside the app as an asset). Facts from official pages live in that file with their source and "last checked" date,
 * never in code (CLAUDE.md, "never invent facts"). Plain Java, so the unit tests read the real file.
 */
public final class BaggageFacts {

    /** File name inside the app's assets (app/build.gradle.kts adds mock-data/baggage). */
    public static final String ASSET = "baggage-facts.json";

    public int version;
    public Deadlines deadlines = new Deadlines();
    public Pir pir = new Pir();
    public LostFound lostFound = new LostFound();
    public List<Step> escalation = new ArrayList<>();

    /** Written-complaint time limits (Montreal Convention). */
    public static final class Deadlines {
        public int damageDays;
        public int delayDays;
        public int lostAfterDays;
        public String rule = "";
        public String damageQuote = "";
        public String delayQuote = "";
        public String lostQuote = "";
        public String sourceUrl = "";
        public String sourceNote = "";
        public String appliesTo = "";
        public String lastChecked = "";
    }

    /** Where to report a mishandled bag before leaving the baggage hall. */
    public static final class Pir {
        public String advice = "";
        public String sourceUrl = "";
        public String sourceNote = "";
        public String lastChecked = "";
    }

    /** The airport's Lost &amp; Found (things lost inside the terminal). */
    public static final class LostFound {
        public String name = "";
        public String t2Location = "";
        public String t1Location = "";
        public String hours = "";
        public String keptFor = "";
        public List<String> toCollect = new ArrayList<>();
        public String portalUrl = "";
        public String portalNote = "";
        public String sourceUrl = "";
        public String lastChecked = "";
        public boolean verified;
    }

    /** One escalation step (information only: the traveller contacts them, the app never does). */
    public static final class Step {
        public String title = "";
        public String detail = "";
        public String url = "";
        public String sourceNote = "";
        public String lastChecked = "";
    }

    /** Reads the JSON file. A broken file throws, so a typo is found in the unit tests, not on stage. */
    public static BaggageFacts parse(Reader reader) {
        BaggageFacts facts = new Gson().fromJson(reader, BaggageFacts.class);
        if (facts == null || facts.deadlines == null || facts.deadlines.damageDays <= 0
                || facts.deadlines.delayDays <= 0 || facts.lostFound == null) {
            throw new JsonParseException("baggage-facts.json is missing its deadlines or lostFound section");
        }
        return facts;
    }
}
