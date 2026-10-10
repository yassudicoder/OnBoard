package com.safarsaathi.app.data.ask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Works out what the traveller is asking, from the concepts the {@link Lexicon} found. Plain Java, no AI model:
 * checked by IntentMatcherTest and the golden phrase set (IntentGoldenSetTest, 90% or better).
 *
 * <p>How: every rule below says "these concepts together mean that intent, with this score". A rule matches when each
 * of its groups has at least one concept in the question. The highest score wins. Examples:
 * <ul>
 *   <li>"Boarding kitne baje band hogi?" = BOARDING + WHEN + CLOSE: "boarding closes" (9) beats "boarding time" (8).</li>
 *   <li>"Get me a cab to my hotel" = CAB + HOTEL: "cab" (10) beats "find a hotel" (9).</li>
 *   <li>"Washroom near gate 32" = WASHROOM + GATE: "where is" (8) beats "gate" (7).</li>
 * </ul>
 * Because the rules only see concepts, they are the same for every language.
 */
public final class IntentMatcher {

    /** At or above this score, with no close rival, the match is HIGH confidence. */
    static final int HIGH_SCORE = 6;
    /** A rule of another intent this close to the winner's score is a rival (see {@link #hasCloseRival}). */
    static final int HIGH_LEAD = 2;

    private static final class Rule {
        final AskIntent intent;
        final int score;
        final List<Set<Concept>> groups;

        Rule(AskIntent intent, int score, List<Set<Concept>> groups) {
            this.intent = intent;
            this.score = score;
            this.groups = groups;
        }

        boolean matches(Set<Concept> found) {
            for (Set<Concept> group : groups) {
                if (Collections.disjoint(group, found)) {
                    return false;
                }
            }
            return true;
        }

        /** The concepts of the question this rule used. */
        Set<Concept> concepts(Set<Concept> found) {
            Set<Concept> used = EnumSet.noneOf(Concept.class);
            for (Set<Concept> group : groups) {
                for (Concept concept : group) {
                    if (found.contains(concept)) {
                        used.add(concept);
                    }
                }
            }
            return used;
        }
    }

    /** Words that ask HOW something is wanted, not WHAT it is about; they fit any topic. */
    private static final Set<Concept> QUESTION_WORDS =
            EnumSet.of(Concept.WHEN, Concept.WHERE, Concept.GO, Concept.STATUS, Concept.START);

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // Requests the phone can't fulfil itself. Highest, so "cab to my hotel" or "flights to London" never turn
        // into a gate or flight-status answer.
        rule(AskIntent.CAB, 10, any(Concept.CAB));
        rule(AskIntent.SEARCH_HOTELS, 9, any(Concept.HOTEL));
        rule(AskIntent.SEARCH_FLIGHTS, 9, any(Concept.SEARCH, Concept.BOOK), any(Concept.FLIGHT));

        // Boarding and gate.
        rule(AskIntent.BOARDING_CLOSE, 9, any(Concept.BOARDING, Concept.GATE), any(Concept.CLOSE));
        rule(AskIntent.BOARDING_TIME, 8, any(Concept.BOARDING), any(Concept.WHEN, Concept.START));
        rule(AskIntent.BOARDING_TIME, 6, any(Concept.BOARDING));
        rule(AskIntent.GATE, 7, any(Concept.GATE));

        // The flight and the connection.
        rule(AskIntent.CONNECTION_STATUS, 9, any(Concept.CONNECTION),
                any(Concept.MISS, Concept.DELAY, Concept.STATUS, Concept.WHEN, Concept.ON_TIME));
        rule(AskIntent.CONNECTION_STATUS, 8, any(Concept.CONNECTION));
        rule(AskIntent.FLIGHT_STATUS, 8, any(Concept.DELAY, Concept.CANCEL, Concept.ON_TIME));
        rule(AskIntent.FLIGHT_STATUS, 7, any(Concept.FLIGHT), any(Concept.STATUS, Concept.WHEN, Concept.ARRIVE));
        rule(AskIntent.FLIGHT_STATUS, 6, any(Concept.ARRIVE, Concept.MISS));
        rule(AskIntent.FLIGHT_STATUS, 5, any(Concept.FLIGHT));

        // Bags. "Is my bag delayed?" is about the bag (9), not the flight (8); "where do I collect my bag?" is about
        // the belt (9), "where is my bag?" about the bag (8).
        rule(AskIntent.BAGGAGE_BELT, 9, any(Concept.BELT));
        rule(AskIntent.BAGGAGE_BELT, 9, any(Concept.BAG), any(Concept.COLLECT));
        rule(AskIntent.BAG_STATUS, 9, any(Concept.BAG), any(Concept.DELAY, Concept.STATUS, Concept.MISS));
        rule(AskIntent.BAG_STATUS, 8, any(Concept.BAG), any(Concept.WHERE));
        rule(AskIntent.BAG_STATUS, 7, any(Concept.BAG));

        // What next.
        rule(AskIntent.NEXT_STEP, 8, any(Concept.WHAT_TO_DO));
        rule(AskIntent.NEXT_STEP, 7, any(Concept.WHERE), any(Concept.GO));
        rule(AskIntent.NEXT_STEP, 6, any(Concept.NEXT));

        // Documents (answered from the vault's summaries). "Is my visa saved?" / "Am I missing a document?" is about
        // what the vault holds (9); a document with "expire" / "valid" is about its dates (9); a document alone too (6).
        rule(AskIntent.DOCUMENT_MISSING, 9, any(Concept.PASSPORT, Concept.VISA, Concept.INSURANCE, Concept.DOCUMENT),
                any(Concept.SAVED, Concept.MISS));
        rule(AskIntent.DOCUMENT_EXPIRY, 9, any(Concept.PASSPORT, Concept.VISA, Concept.INSURANCE, Concept.DOCUMENT),
                any(Concept.EXPIRY));
        rule(AskIntent.DOCUMENT_EXPIRY, 6, any(Concept.PASSPORT, Concept.VISA, Concept.INSURANCE, Concept.DOCUMENT));

        // Places in the terminal, and general help.
        rule(AskIntent.WHERE_IS, 8, any(Concept.WASHROOM, Concept.HELP_DESK, Concept.LIFT));
        rule(AskIntent.HELP, 6, any(Concept.HELP));
    }

    private final Lexicon lexicon;

    public IntentMatcher(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    /** What this question asks for. Never null: nothing matched = UNKNOWN with confidence NONE. */
    public IntentMatch match(String question) {
        List<String> words = AskText.words(question);
        List<Lexicon.Hit> hits = lexicon.find(words);
        Set<Concept> found = EnumSet.noneOf(Concept.class);
        for (Lexicon.Hit hit : hits) {
            found.addAll(hit.concepts);
        }

        List<Rule> matching = new ArrayList<>();
        Rule best = null;
        for (Rule rule : RULES) {
            if (rule.matches(found)) {
                matching.add(rule);
                if (best == null || rule.score > best.score) {
                    best = rule; // a tie keeps the rule listed first
                }
            }
        }
        if (best == null) {
            return IntentMatch.none();
        }
        boolean high = best.score >= HIGH_SCORE && !hasCloseRival(best, matching, found);
        IntentMatch.Place place = best.intent == AskIntent.WHERE_IS ? firstPlace(hits) : null;
        boolean aboutDocuments = best.intent == AskIntent.DOCUMENT_EXPIRY || best.intent == AskIntent.DOCUMENT_MISSING;
        return new IntentMatch(best.intent, high ? IntentMatch.Confidence.HIGH : IntentMatch.Confidence.MEDIUM,
                best.score, place, gateNumber(words), aboutDocuments ? firstDocument(hits) : null);
    }

    /**
     * True when another intent's rule came close (within {@link #HIGH_LEAD}) AND saw a topic the winner ignored: real
     * doubt. "Is there a lift near gate 32?" = where-is (lift) vs gate: doubt. "When does boarding close?" = boarding
     * close vs boarding time: no doubt, the boarding-time rule only saw "boarding" (which the winner uses too) and the
     * question word "when". Question words never make a rival: they fit any topic.
     */
    private static boolean hasCloseRival(Rule best, List<Rule> matching, Set<Concept> found) {
        Set<Concept> explained = best.concepts(found);
        for (Rule rival : matching) {
            if (rival.intent == best.intent || best.score - rival.score >= HIGH_LEAD) {
                continue;
            }
            Set<Concept> rivalTopics = rival.concepts(found);
            rivalTopics.removeAll(QUESTION_WORDS);
            if (!explained.containsAll(rivalTopics)) {
                return true;
            }
        }
        return false;
    }

    /** The first place word in the question ("lift near the washroom" = lift). */
    private static IntentMatch.Place firstPlace(List<Lexicon.Hit> hits) {
        for (Lexicon.Hit hit : hits) {
            if (hit.concepts.contains(Concept.WASHROOM)) {
                return IntentMatch.Place.WASHROOM;
            }
            if (hit.concepts.contains(Concept.HELP_DESK)) {
                return IntentMatch.Place.HELP_DESK;
            }
            if (hit.concepts.contains(Concept.LIFT)) {
                return IntentMatch.Place.LIFT;
            }
        }
        return null;
    }

    /** The first document named in the question ("passport", "visa", "insurance"), or null for documents in general. */
    private static IntentMatch.Document firstDocument(List<Lexicon.Hit> hits) {
        for (Lexicon.Hit hit : hits) {
            if (hit.concepts.contains(Concept.PASSPORT)) {
                return IntentMatch.Document.PASSPORT;
            }
            if (hit.concepts.contains(Concept.VISA)) {
                return IntentMatch.Document.VISA;
            }
            if (hit.concepts.contains(Concept.INSURANCE)) {
                return IntentMatch.Document.INSURANCE;
            }
        }
        return null;
    }

    /** A 1 to 3 digit number in the question, read as a gate number ("gate 32", "32 number gate"), or null. */
    private static String gateNumber(List<String> words) {
        for (String word : words) {
            if (word.length() <= 3 && word.matches("[0-9]+")) {
                return word;
            }
        }
        return null;
    }

    @SafeVarargs
    private static void rule(AskIntent intent, int score, Set<Concept>... groups) {
        RULES.add(new Rule(intent, score, Arrays.asList(groups)));
    }

    private static Set<Concept> any(Concept first, Concept... rest) {
        return EnumSet.of(first, rest);
    }
}
