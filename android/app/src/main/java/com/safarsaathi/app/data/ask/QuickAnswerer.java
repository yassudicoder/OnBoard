package com.safarsaathi.app.data.ask;

import androidx.annotation.Nullable;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.data.baggage.BaggageFacts;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.ui.trip.TripOverview;
import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.MapFloor;
import com.safarsaathi.routing.map.NodeType;
import com.safarsaathi.routing.map.Zone;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;

/**
 * Layer 1, Quick answers: turns an intent into a reply from fixed sentences ({@link AnswerText}) and the phone's
 * saved data ({@link TripFacts}). No AI model, no internet, so it also works in airplane mode.
 *
 * <p>GOLDEN RULES, enforced here:
 * <ul>
 *   <li>Every gate, time, belt and flight number in a reply comes from {@link TripFacts}, inserted by this code.
 *       Missing data picks the "not announced yet" sentence; nothing is ever guessed.</li>
 *   <li>Estimates say "estimate"; the terminal map says it is a demo map.</li>
 *   <li>Travel documents: only the vault reader's summaries ({@link DocumentFacts}: type, first name, dates,
 *       countries, status), never numbers or images; a locked vault is asked to unlock first.</li>
 * </ul>
 * Plain Java, checked by QuickAnswererTest in every reply language.
 */
public final class QuickAnswerer {

    /** At most this many places in one "where is" reply; the map shows the rest. */
    static final int MAX_PLACES = 4;

    /**
     * The checked bag-problem and Lost &amp; Found facts (mock-data/baggage/baggage-facts.json, each with its source and
     * date), or null when the screen did not load them: then those answers point to the Help tab instead of quoting.
     */
    @Nullable
    private final BaggageFacts baggage;

    public QuickAnswerer() {
        this(null);
    }

    public QuickAnswerer(@Nullable BaggageFacts baggage) {
        this.baggage = baggage;
    }

    private static final DateTimeFormatter HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm");

    /** The reply to a matched question. */
    public Answer answer(IntentMatch match, AskLanguage language, TripFacts facts) {
        AnswerText text = AnswerText.of(language);
        Reply reply = reply(match, text, facts);
        return new Answer(reply.text, language, match.intent, match.confidence, Answer.Source.PHONE, reply.asOf,
                reply.action, !match.intent.quickAnswer && match.intent != AskIntent.UNKNOWN);
    }

    /** The words, "as of" time and button of one reply, before they become an {@link Answer}. */
    private static final class Reply {
        final String text;
        @Nullable
        final Instant asOf;
        @Nullable
        final Answer.Action action;

        Reply(String text, @Nullable Instant asOf, @Nullable Answer.Action action) {
            this.text = text;
            this.asOf = asOf;
            this.action = action;
        }
    }

    private Reply reply(IntentMatch match, AnswerText text, TripFacts facts) {
        switch (match.intent) {
            case GATE:
                return gate(text, facts);
            case BOARDING_TIME:
                return boarding(text, facts, false);
            case BOARDING_CLOSE:
                return boarding(text, facts, true);
            case FLIGHT_STATUS:
                return flightStatus(text, facts);
            case CONNECTION_STATUS:
                return connection(text, facts);
            case BAG_STATUS:
                return bagStatus(text, facts);
            case BAGGAGE_BELT:
                return belt(text, facts);
            case NEXT_STEP:
                return nextStep(text, facts);
            case DOCUMENT_EXPIRY:
                return documents(text, facts, match, false);
            case DOCUMENT_MISSING:
                return documents(text, facts, match, true);
            case WHERE_IS:
                return whereIs(text, facts, match);
            case HELP:
                return new Reply(text.get("help.menu"), null, null);
            case SEARCH_FLIGHTS:
            case SEARCH_HOTELS:
                return new Reply(text.get("cloud.search"), null, null);
            case CAB:
                return new Reply(text.get("cloud.cab"), null, Answer.Action.OPEN_HOME);
            case GREETING:
                return new Reply(text.get("greeting"), null, null);
            case THANKS:
                return new Reply(text.get("thanks"), null, null);
            case BAG_DAMAGED:
                return bagProblem(text, facts, "bag.problem_damaged", "bag.deadline_damage");
            case BAG_NOT_ARRIVED:
                return bagProblem(text, facts, "bag.problem_not_arrived", "bag.deadline_delay");
            case BAG_PILFERED:
                return bagProblem(text, facts, "bag.problem_pilfered", "bag.deadline_pilfered");
            case LOST_ITEM:
                return lostItem(text);
            case ASSISTANCE:
                return assistance(text, facts);
            case ITEM_RULES:
                return new Reply(text.get("rules.carry"), null, null);
            case BAG_ALLOWANCE:
                return new Reply(text.get("rules.allowance"), null, null);
            case GETTING_THERE:
                return gettingThere(text, facts);
            case UNKNOWN:
            default:
                return new Reply(text.get("unknown"), null, null);
        }
    }

    // ---- Gate and boarding ----

    private Reply gate(AnswerText text, TripFacts facts) {
        Reply problem = flightProblem(text, facts);
        if (problem != null) {
            return problem;
        }
        TripFacts.Flight flight = facts.flight;
        TripFacts.GateInfo gate = facts.gate;
        if (gate == null || gate.gate == null) {
            return new Reply(text.fill("gate.not_announced", "flight", flight.name,
                    "departs", time(flight.departs, text, facts)), facts.asOf, Answer.Action.OPEN_MAP);
        }
        String sentence = gate.previousGate != null
                ? text.fill("gate.changed", "flight", flight.name, "gate", gate.gate, "previous", gate.previousGate)
                : text.fill("gate.known", "flight", flight.name, "gate", gate.gate);
        if (gate.boardingClose != null) {
            sentence += " " + text.fill(gate.boardingEstimate ? "gate.boarding_close_estimate" : "gate.boarding_close",
                    "close", time(gate.boardingClose, text, facts));
        }
        return new Reply(sentence, gateAsOf(facts), Answer.Action.NAVIGATE);
    }

    private Reply boarding(AnswerText text, TripFacts facts, boolean close) {
        Reply problem = flightProblem(text, facts);
        if (problem != null) {
            return problem;
        }
        TripFacts.Flight flight = facts.flight;
        Instant when = facts.gate == null ? null : close ? facts.gate.boardingClose : facts.gate.boardingStart;
        String kind = close ? "boarding.close" : "boarding.start";
        if (when == null) {
            return new Reply(text.fill(kind + "_unknown", "flight", flight.name,
                    "departs", time(flight.departs, text, facts)), facts.asOf, null);
        }
        String key = facts.gate.boardingEstimate ? kind + "_estimate" : kind;
        return new Reply(text.fill(key, "flight", flight.name, close ? "close" : "start", time(when, text, facts)),
                gateAsOf(facts), null);
    }

    // ---- Flight and connection ----

    private Reply flightStatus(AnswerText text, TripFacts facts) {
        Reply problem = noFlight(text, facts);
        if (problem != null) {
            return problem;
        }
        TripFacts.Flight flight = facts.flight;
        String departs = time(flight.departs, text, facts);
        String arrives = time(flight.arrives, text, facts);
        switch (flight.status) {
            case BlockEntity.STATUS_CANCELLED:
                return new Reply(text.fill("flight.cancelled", "flight", flight.name), facts.asOf,
                        Answer.Action.OPEN_HOME);
            case BlockEntity.STATUS_NEEDS_USER:
                return new Reply(text.fill("flight.needs_you", "flight", flight.name), facts.asOf,
                        Answer.Action.OPEN_HOME);
            case BlockEntity.STATUS_DONE:
                return new Reply(text.fill("flight.done", "flight", flight.name), facts.asOf, null);
            case TripOverview.DISPLAY_DELAYED:
                return new Reply(text.fill("flight.delayed", "flight", flight.name, "departs", departs,
                        "planned", time(flight.plannedDeparts, text, facts), "arrives", arrives), facts.asOf, null);
            case BlockEntity.STATUS_AUTO_ADJUSTED:
                return new Reply(text.fill("flight.changed", "flight", flight.name, "departs", departs,
                        "arrives", arrives), facts.asOf, null);
            default:
                return new Reply(text.fill("flight.on_plan", "flight", flight.name, "departs", departs,
                        "arrives", arrives), facts.asOf, null);
        }
    }

    private Reply connection(AnswerText text, TripFacts facts) {
        Reply problem = noFlight(text, facts);
        if (problem != null) {
            return problem;
        }
        TripFacts.Flight first = facts.flight;
        TripFacts.Flight next = facts.connection;
        if (next == null) {
            return new Reply(text.fill("connection.none", "flight", first.name), facts.asOf, null);
        }
        if (BlockEntity.STATUS_CANCELLED.equals(first.status)) {
            return new Reply(text.fill("connection.first_cancelled", "flight", first.name, "next", next.name),
                    facts.asOf, Answer.Action.OPEN_HOME);
        }
        String arrives = time(first.arrives, text, facts);
        String departs = time(next.departs, text, facts);
        long gapMinutes = Duration.between(first.arrives, next.departs).toMinutes();
        if (gapMinutes < 0) {
            // A fact, not a prediction: the first flight now lands after the second one leaves.
            return new Reply(text.fill("connection.too_late", "flight", first.name, "arrives", arrives,
                    "next", next.name, "departs", departs), facts.asOf, Answer.Action.OPEN_HOME);
        }
        // How much time is ENOUGH depends on the airport's minimum connection time, which the phone does not have:
        // the sentence says so instead of guessing.
        return new Reply(text.fill("connection.gap", "flight", first.name, "arrives", arrives, "next", next.name,
                "departs", departs, "gap", duration(gapMinutes, text)), facts.asOf, null);
    }

    // ---- Bags ----

    private Reply bagStatus(AnswerText text, TripFacts facts) {
        TripFacts.BagInfo bag = facts.bag;
        if (bag == null) {
            return new Reply(text.get("bag.none"), null, Answer.Action.OPEN_BAGS);
        }
        String key = "bag.status_" + bag.status;
        String status = text.has(key) ? text.get(key) : bag.status;
        String sentence = text.fill("bag.status", "tag", bag.tagEnd, "status", status);
        if (bag.belt != null && facts.flight != null) {
            sentence += " " + text.fill("belt.known", "flight", facts.flight.name, "belt", bag.belt);
        }
        return new Reply(sentence, bag.asOf, Answer.Action.OPEN_BAGS);
    }

    private Reply belt(AnswerText text, TripFacts facts) {
        Reply problem = noFlight(text, facts);
        if (problem != null) {
            return problem;
        }
        TripFacts.Flight flight = facts.flight;
        if (facts.bag != null && facts.bag.belt != null) {
            return new Reply(text.fill("belt.known", "flight", flight.name, "belt", facts.bag.belt),
                    facts.bag.asOf, null);
        }
        return new Reply(text.fill("belt.unknown", "flight", flight.name), facts.asOf, null);
    }

    // ---- What next ----

    private Reply nextStep(AnswerText text, TripFacts facts) {
        if (!facts.tripSaved) {
            return new Reply(text.get("trip.missing"), null, Answer.Action.OPEN_HOME);
        }
        TripFacts.Flight flight = facts.flight;
        if (flight != null && (BlockEntity.STATUS_CANCELLED.equals(flight.status)
                || BlockEntity.STATUS_NEEDS_USER.equals(flight.status))) {
            return flightStatus(text, facts); // that IS the next step: decide what to do about the flight
        }
        BlockEntity next = facts.nextUp;
        if (next == null) {
            return new Reply(text.get("next.nothing"), facts.asOf, null);
        }
        if (flight != null && next.id == flight.block.id && flight.departs.isAfter(facts.now)) {
            String departs = time(flight.departs, text, facts);
            String from = flight.from != null ? flight.from : flight.name;
            if (facts.gate != null && facts.gate.gate != null) {
                return new Reply(text.fill("next.airport_gate", "flight", flight.name, "departs", departs,
                        "from", from, "gate", facts.gate.gate), facts.asOf, Answer.Action.NAVIGATE);
            }
            return new Reply(text.fill("next.airport", "flight", flight.name, "departs", departs, "from", from),
                    facts.asOf, Answer.Action.OPEN_MAP);
        }
        return new Reply(text.fill("next.block", "title", next.title, "time", time(next.startTime, text, facts)),
                facts.asOf, null);
    }

    // ---- Travel documents (summaries from the Document Vault's read-only reader) ----

    /**
     * Expiry / "is it saved?" answers. Everything comes from {@link DocumentFacts}: the vault decides each status,
     * this code only words it. Locked vault: ask to unlock first (button), never a guess.
     *
     * @param askedIfSaved true for "is my visa saved?" / "am I missing a document?", false for expiry questions
     */
    private Reply documents(AnswerText text, TripFacts facts, IntentMatch match, boolean askedIfSaved) {
        DocumentFacts documents = facts.documents;
        if (documents.access == DocumentFacts.Access.NOT_AVAILABLE) {
            return new Reply(text.get("documents.not_available"), null, null);
        }
        if (documents.access == DocumentFacts.Access.LOCKED) {
            return new Reply(text.get("documents.locked"), null, Answer.Action.UNLOCK_VAULT);
        }
        String trip = documents.tripTitle != null ? documents.tripTitle : text.get("documents.your_vault");
        List<DocumentFacts.Doc> wanted = new ArrayList<>();
        for (DocumentFacts.Doc doc : documents.docs) {
            if (match.document == null || kindOf(doc.type) == match.document) {
                wanted.add(doc);
            }
        }
        // Which documents a country REQUIRES is not known on the phone, so "saved?" questions say what is saved (or
        // not) and add that note; they never say a document is required or that nothing is missing.
        String note = askedIfSaved ? " " + text.get("documents.requirements_note") : "";
        if (match.document != null && wanted.isEmpty()) {
            return new Reply(text.fill("documents.none_of_type", "document", text.get("doc.kind_" + match.document.name()),
                    "trip", trip) + note, null, Answer.Action.OPEN_VAULT);
        }
        if (match.document == null && askedIfSaved) {
            // "Am I missing anything?": only the documents that need attention, or "all ready".
            List<DocumentFacts.Doc> problems = new ArrayList<>();
            for (DocumentFacts.Doc doc : wanted) {
                if (doc.status != DocumentFacts.Status.READY) {
                    problems.add(doc);
                }
            }
            if (problems.isEmpty()) {
                return new Reply(text.fill("documents.all_ready", "count", String.valueOf(wanted.size()), "trip", trip)
                        + note, null, Answer.Action.OPEN_VAULT);
            }
            wanted = problems;
        }
        List<String> lines = new ArrayList<>();
        for (DocumentFacts.Doc doc : wanted) {
            lines.add(documentLine(doc, text, facts));
        }
        String key = match.document == null && askedIfSaved ? "documents.attention" : "documents.list";
        return new Reply(text.fill(key, "trip", trip, "items", String.join(text.get("list.separator") + " ", lines))
                + text.get("sentence.end") + note, null, Answer.Action.OPEN_VAULT);
    }

    /** "Visa for Rwanda (Paramvir): valid until 20 Oct 2026, ready". */
    private static String documentLine(DocumentFacts.Doc doc, AnswerText text, TripFacts facts) {
        String name = text.has("doc." + doc.type) ? text.get("doc." + doc.type) : text.get("doc.OTHER");
        boolean identity = "PASSPORT".equals(doc.type) || "ID_CARD".equals(doc.type); // "passport for India" reads oddly
        if (!doc.countries.isEmpty() && !identity) {
            name = text.fill("doc.for_country", "document", name, "country", countryName(doc.countries.get(0), text));
        }
        LocalDate lastDay = doc.lastValidDay();
        String validity = lastDay == null ? text.get("doc.no_date")
                : text.fill("doc.valid_until", "date", date(lastDay, text));
        String status = doc.status == DocumentFacts.Status.EXPIRES_SOON && lastDay != null
                ? text.fill("status.EXPIRES_SOON", "days", String.valueOf(ChronoUnit.DAYS.between(today(facts), lastDay)))
                : text.get("status." + doc.status.name());
        String line = doc.holderFirstName == null
                ? text.fill("doc.line", "document", name, "validity", validity, "status", status)
                : text.fill("doc.line_named", "document", name, "name", doc.holderFirstName, "validity", validity,
                "status", status);
        return doc.sample ? line + " (" + text.get("documents.sample") + ")" : line;
    }

    /** Which of the three askable kinds a vault document type is (VISA covers e-visas and transit visas). */
    @Nullable
    private static IntentMatch.Document kindOf(String type) {
        if ("PASSPORT".equals(type)) {
            return IntentMatch.Document.PASSPORT;
        }
        if ("VISA".equals(type) || "E_VISA".equals(type) || "TRANSIT_VISA".equals(type)) {
            return IntentMatch.Document.VISA;
        }
        if ("INSURANCE".equals(type)) {
            return IntentMatch.Document.INSURANCE;
        }
        return null;
    }

    /** "Rwanda" / "रवांडा": the country's name in the reply language, from the phone's own country list. */
    private static String countryName(String iso2, AnswerText text) {
        try {
            String name = new Locale.Builder().setRegion(iso2).build().getDisplayCountry(locale(text.language()));
            return name.isEmpty() ? iso2 : name;
        } catch (IllformedLocaleException e) {
            return iso2; // not a country code: show it as it is rather than fail the answer
        }
    }

    private static String date(LocalDate day, AnswerText text) {
        return day.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale(text.language())));
    }

    private static LocalDate today(TripFacts facts) {
        return facts.now.atZone(AppConfig.TRIP_ZONE).toLocalDate();
    }

    // ---- Places in the terminal ----

    private Reply whereIs(AnswerText text, TripFacts facts, IntentMatch match) {
        if (facts.map == null) {
            return new Reply(text.get("where.no_map"), null, Answer.Action.OPEN_MAP);
        }
        IntentMatch.Place kind = match.place == null ? IntentMatch.Place.WASHROOM : match.place;
        List<TerminalPlaces.Place> places = placesFor(kind, facts.map, match.gateNumber);
        if (places.isEmpty()) {
            return new Reply(text.get("where.none"), null, Answer.Action.OPEN_MAP);
        }
        String lang = text.language().mapLanguage;
        // One place: always say its name ("Food court near Central hall"); several with one name: just where they are.
        boolean sameNames = places.size() > 1;
        for (TerminalPlaces.Place place : places) {
            sameNames &= place.node.name(lang).equals(places.get(0).node.name(lang));
        }
        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_PLACES, places.size()); i++) {
            items.add(placeLine(places.get(i), sameNames, text, facts));
        }
        StringBuilder sentence = new StringBuilder(text.get("where.header_" + kind.name()));
        if (TerminalPlaces.hasGate(facts.map, match.gateNumber)) {
            sentence.append(' ').append(text.fill("where.closest_to_gate", "gate", match.gateNumber));
        }
        sentence.append(text.get("list.start")).append(' ')
                .append(String.join(text.get("list.separator") + " ", items))
                .append(text.get("sentence.end"));
        sentence.append(' ').append(text.get("where.demo_map"));
        return new Reply(sentence.toString(), null, Answer.Action.OPEN_MAP);
    }

    /** The map's places of one kind. Most kinds are one node type; food and check-in need a little more. */
    private static List<TerminalPlaces.Place> placesFor(IntentMatch.Place kind, AirportMap map,
                                                        @Nullable String gateNumber) {
        switch (kind) {
            case FOOD: {
                // The demo map has no food node type: its food court is a landmark (id "food_court").
                List<TerminalPlaces.Place> food = new ArrayList<>();
                for (TerminalPlaces.Place place : TerminalPlaces.find(map, NodeType.LANDMARK, gateNumber)) {
                    if (place.node.id != null && place.node.id.contains("food")) {
                        food.add(place);
                    }
                }
                return food;
            }
            case CHECKIN: {
                List<TerminalPlaces.Place> checkIn = new ArrayList<>(TerminalPlaces.find(map, NodeType.BAG_DROP, gateNumber));
                checkIn.addAll(TerminalPlaces.find(map, NodeType.CHECKIN_ROW, gateNumber));
                return checkIn;
            }
            case BUGGY:
                return TerminalPlaces.find(map, NodeType.BUGGY_STOP, gateNumber);
            default: // WASHROOM, HELP_DESK, LIFT, SECURITY and BAGGAGE_SERVICES have the node type's name
                return TerminalPlaces.find(map, NodeType.valueOf(kind.name()), gateNumber);
        }
    }

    // ---- Bag problems, lost things, help, rules (added after the demo probe) ----

    /**
     * Damaged / not arrived / something stolen from the checked bag: what to do before leaving the baggage hall (a
     * PIR), the written-complaint time limit from baggage-facts.json (sourced, never typed into a sentence), where the
     * baggage services counter is on the demo map, and the Help tab that makes the claim pack.
     */
    private Reply bagProblem(AnswerText text, TripFacts facts, String problemKey, String deadlineKey) {
        StringBuilder sentence = new StringBuilder(text.get(problemKey));
        if (baggage != null && baggage.deadlines.damageDays > 0 && baggage.deadlines.delayDays > 0) {
            int days = "bag.deadline_delay".equals(deadlineKey)
                    ? baggage.deadlines.delayDays : baggage.deadlines.damageDays;
            sentence.append(' ').append(text.fill(deadlineKey, "days", String.valueOf(days),
                    "lost_days", String.valueOf(baggage.deadlines.lostAfterDays)));
            sentence.append(' ').append(text.get("bag.deadline_note"));
        }
        TerminalPlaces.Place counter = facts.map == null ? null
                : first(TerminalPlaces.find(facts.map, NodeType.BAGGAGE_SERVICES, null));
        if (counter != null) {
            sentence.append(' ').append(text.fill("bag.counter_where", "place", placeLine(counter, false, text, facts)));
        }
        sentence.append(' ').append(text.get("bag.claim_help"));
        if ("bag.problem_not_arrived".equals(problemKey)) {
            // "Lost my bag" can also mean a bag left in the terminal: point to Lost & Found too.
            sentence.append(' ').append(text.get("bag.or_lost_found"));
        }
        return new Reply(sentence.toString(), null, Answer.Action.OPEN_HELP);
    }

    /** Lost something at the airport: CSMIA Lost &amp; Found from baggage-facts.json, with its source date. */
    private Reply lostItem(AnswerText text) {
        if (baggage == null || baggage.lostFound.t2Location.isEmpty()) {
            return new Reply(text.get("lost.no_facts"), null, Answer.Action.OPEN_LOST_FOUND);
        }
        BaggageFacts.LostFound lost = baggage.lostFound;
        StringBuilder sentence = new StringBuilder(text.fill("lost.found", "name", lost.name,
                "location", lost.t2Location));
        if (!lost.hours.isEmpty()) {
            sentence.append(' ').append(text.fill("lost.hours", "hours", lost.hours));
        }
        sentence.append(' ').append(text.get("lost.portal")).append(' ').append(text.get("lost.plane"));
        try {
            sentence.append(' ').append(text.fill("lost.source", "date", date(LocalDate.parse(lost.lastChecked), text)));
        } catch (RuntimeException noDate) {
            // no checked date in the file: say nothing about it rather than invent one
        }
        return new Reply(sentence.toString(), null, Answer.Action.OPEN_LOST_FOUND);
    }

    /** Wheelchair / special assistance: OnBoard can't book it; the airline can; the nearest help desk on the map. */
    private Reply assistance(AnswerText text, TripFacts facts) {
        StringBuilder sentence = new StringBuilder(text.get("assist.wheelchair"));
        TerminalPlaces.Place desk = facts.map == null ? null
                : first(TerminalPlaces.find(facts.map, NodeType.HELP_DESK, null));
        if (desk != null) {
            sentence.append(' ').append(text.fill("assist.desk", "place", placeLine(desk, false, text, facts)));
        }
        return new Reply(sentence.toString(), null, Answer.Action.OPEN_MAP);
    }

    /** Getting to the airport: the flight's time and airport from the trip, and the directions on Home. */
    private Reply gettingThere(AnswerText text, TripFacts facts) {
        TripFacts.Flight flight = facts.flight;
        if (flight == null) {
            return new Reply(text.get("there.directions"), null, Answer.Action.OPEN_HOME);
        }
        String first = flight.from == null
                ? text.fill("there.flight_no_from", "flight", flight.name, "departs", time(flight.departs, text, facts))
                : text.fill("there.flight", "flight", flight.name, "departs", time(flight.departs, text, facts),
                "from", flight.from);
        return new Reply(first + " " + text.get("there.directions"), facts.asOf, Answer.Action.OPEN_HOME);
    }

    @Nullable
    private static TerminalPlaces.Place first(List<TerminalPlaces.Place> places) {
        return places.isEmpty() ? null : places.get(0);
    }

    /** "near Gate 32 (Departures, after security)", or "Lift A near Duty Free (...)" when names differ. */
    private static String placeLine(TerminalPlaces.Place place, boolean sameNames, AnswerText text,
                                    TripFacts facts) {
        String lang = text.language().mapLanguage;
        MapFloor floor = facts.map.floor(place.node.floor);
        String floorName = floor == null ? place.node.floor : floor.name(lang);
        String zone = text.get(zoneKey(place));
        if (place.near == null) {
            return text.fill("where.item_no_near", "name", place.node.name(lang), "floor", floorName, "zone", zone);
        }
        String near = place.near.name(lang);
        return sameNames
                ? text.fill("where.item_plain", "near", near, "floor", floorName, "zone", zone)
                : text.fill("where.item", "name", place.node.name(lang), "near", near, "floor", floorName,
                "zone", zone);
    }

    /** "after security" on a floor with a security check; in arrivals, "inside" (baggage hall side) or "outside". */
    private static String zoneKey(TerminalPlaces.Place place) {
        boolean airside = place.node.zone == Zone.AIRSIDE;
        if (place.floorHasSecurity) {
            return airside ? "zone.after_security" : "zone.before_security";
        }
        return airside ? "zone.arrivals_inside" : "zone.arrivals_outside";
    }

    // ---- Shared pieces ----

    /** "Trip not on the phone" or "no flight in the trip", or null when there is a flight to talk about. */
    @Nullable
    private static Reply noFlight(AnswerText text, TripFacts facts) {
        if (!facts.tripSaved) {
            return new Reply(text.get("trip.missing"), null, Answer.Action.OPEN_HOME);
        }
        if (facts.flight == null) {
            return new Reply(text.get("flight.none"), facts.asOf, null);
        }
        return null;
    }

    /** {@link #noFlight}, plus: a cancelled flight has no gate or boarding, so say it is cancelled instead. */
    @Nullable
    private Reply flightProblem(AnswerText text, TripFacts facts) {
        Reply problem = noFlight(text, facts);
        if (problem != null) {
            return problem;
        }
        if (BlockEntity.STATUS_CANCELLED.equals(facts.flight.status)) {
            return new Reply(text.fill("flight.cancelled", "flight", facts.flight.name), facts.asOf,
                    Answer.Action.OPEN_HOME);
        }
        return null;
    }

    @Nullable
    private static Instant gateAsOf(TripFacts facts) {
        return facts.gate != null && facts.gate.asOf != null ? facts.gate.asOf : facts.asOf;
    }

    /**
     * A time in Indian time (CLAUDE.md): "07:35" today, "07:35 tomorrow" (in the reply language), else with the
     * date ("14 Oct, 07:35"). Public so the screen writes the chip's "as of" time the same way.
     */
    public static String time(Instant instant, AnswerText text, TripFacts facts) {
        return time(instant, text, facts.now);
    }

    public static String time(Instant instant, AnswerText text, Instant now) {
        ZonedDateTime when = instant.atZone(AppConfig.TRIP_ZONE);
        LocalDate today = now.atZone(AppConfig.TRIP_ZONE).toLocalDate();
        String hoursMinutes = when.format(HOURS_MINUTES);
        LocalDate day = when.toLocalDate();
        if (day.equals(today)) {
            return hoursMinutes;
        }
        if (day.equals(today.plusDays(1))) {
            return text.fill("time.tomorrow", "time", hoursMinutes);
        }
        if (day.equals(today.minusDays(1))) {
            return text.fill("time.yesterday", "time", hoursMinutes);
        }
        String date = when.format(DateTimeFormatter.ofPattern("d MMM", locale(text.language())));
        return text.fill("time.other_day", "date", date, "time", hoursMinutes);
    }

    /** "1 h 20 min", in the reply language. */
    static String duration(long minutes, AnswerText text) {
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return text.fill("duration.m", "m", String.valueOf(rest));
        }
        if (rest == 0) {
            return text.fill("duration.h", "h", String.valueOf(hours));
        }
        return text.fill("duration.h_m", "h", String.valueOf(hours), "m", String.valueOf(rest));
    }

    private static Locale locale(AskLanguage language) {
        switch (language) {
            case HI:
                return Locale.forLanguageTag("hi-IN");
            case MR:
                return Locale.forLanguageTag("mr-IN");
            default:
                return Locale.forLanguageTag("en-IN");
        }
    }
}
