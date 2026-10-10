package com.safarsaathi.app.data.vault;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.vault.db.TravelDocumentEntity;
import com.safarsaathi.app.data.vault.db.TripVaultEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rules behind {@link VaultSummaryReader}: turns saved documents into {@link DocumentSummary} objects that leave
 * out everything sensitive, and works out each one's {@link SummaryStatus}. Plain Java, unit-tested
 * (VaultSummariesTest).
 */
public final class VaultSummaries {

    /** "Expires soon" = within 90 days: the first of the vault's expiry reminders (90, 30, 7 days). */
    public static final int EXPIRES_SOON_DAYS = 90;

    /** Kinds of document whose last valid day counts as "expiry" when no expiry date is printed. */
    private static final Set<DocType> LAST_DAY_IS_EXPIRY = EnumSet.of(DocType.VISA, DocType.E_VISA,
            DocType.TRANSIT_VISA, DocType.ENTRY_FORM, DocType.INSURANCE, DocType.VACCINATION_CERT);

    /** What the reader knows about one saved document. */
    public static final class DocumentFacts {
        final TravelDocumentEntity document;
        /** The name of the traveller the document is filed under (null when none). */
        @Nullable
        final String travellerName;
        final int pages;
        final int missingPages;

        public DocumentFacts(TravelDocumentEntity document, @Nullable String travellerName, int pages,
                             int missingPages) {
            this.document = document;
            this.travellerName = travellerName;
            this.pages = pages;
            this.missingPages = missingPages;
        }
    }

    private VaultSummaries() {
    }

    /**
     * @param offlineReadyAt when the trip last passed "make offline-ready" (null = never)
     * @param unlocked       false = only hotel name and address of hotel bookings come back
     */
    public static List<DocumentSummary> build(List<DocumentFacts> facts, @Nullable Instant offlineReadyAt,
                                              boolean unlocked, LocalDate today) {
        // A document filed under two legs arrives twice: list it once.
        Map<String, DocumentFacts> unique = new LinkedHashMap<>();
        for (DocumentFacts fact : facts) {
            unique.putIfAbsent(fact.document.id, fact);
        }
        List<DocumentSummary> summaries = new ArrayList<>();
        for (DocumentFacts fact : unique.values()) {
            TravelDocumentEntity doc = fact.document;
            if (!unlocked) {
                if (doc.type == DocType.HOTEL) {
                    summaries.add(new DocumentSummary(null, DocType.HOTEL, null, null, null, null,
                            Collections.emptyList(), null, doc.sample, doc.placeName, doc.address, true));
                }
                continue;
            }
            boolean hotel = doc.type == DocType.HOTEL;
            summaries.add(new DocumentSummary(doc.id, doc.type, firstName(doc.holderName), doc.expiryDate,
                    doc.validFrom, doc.validTo, countries(doc.coversCountries),
                    status(fact, offlineReadyAt, today), doc.sample,
                    hotel ? doc.placeName : null, hotel ? doc.address : null, false));
        }
        return summaries;
    }

    /** Worst first, see {@link SummaryStatus}. */
    static SummaryStatus status(DocumentFacts fact, @Nullable Instant offlineReadyAt, LocalDate today) {
        TravelDocumentEntity doc = fact.document;
        if (fact.missingPages > 0) {
            return SummaryStatus.MISSING;
        }
        LocalDate lastDay = doc.expiryDate != null ? doc.expiryDate
                : LAST_DAY_IS_EXPIRY.contains(doc.type) ? doc.validTo : null;
        if (lastDay != null && lastDay.isBefore(today)) {
            return SummaryStatus.EXPIRED;
        }
        if (fact.travellerName != null && doc.holderName != null && !sameName(fact.travellerName, doc.holderName)) {
            return SummaryStatus.MISMATCH;
        }
        // "Expires soon" only for documents with a printed expiry date (passport, ID card). A visa or an insurance is
        // MEANT to end around the trip; whether it covers the trip is the pre-departure check's job (V4).
        if (doc.expiryDate != null && !doc.expiryDate.isAfter(today.plusDays(EXPIRES_SOON_DAYS))) {
            return SummaryStatus.EXPIRES_SOON;
        }
        if (offlineReadyAt == null || doc.createdAt.isAfter(offlineReadyAt)) {
            return SummaryStatus.NOT_OFFLINE_YET;
        }
        return SummaryStatus.READY;
    }

    /**
     * Same person? Ignores case, spacing and punctuation, the order of the words, and extra (middle) names: every
     * word of the shorter name must appear in the longer one. "Paramvir Singh" = "SINGH PARAMVIR" =
     * "Paramvir Kumar Singh", but not "Paramjit Singh".
     */
    public static boolean sameName(String a, String b) {
        List<String> first = words(a);
        List<String> second = words(b);
        if (first.isEmpty() || second.isEmpty()) {
            return true; // nothing to compare
        }
        List<String> shorter = first.size() <= second.size() ? first : second;
        List<String> longer = shorter == first ? second : first;
        return longer.containsAll(shorter);
    }

    /** "PARAMVIR SINGH" becomes "Paramvir": the first word only, never the full name. */
    @Nullable
    static String firstName(@Nullable String holderName) {
        List<String> words = words(holderName);
        if (words.isEmpty()) {
            return null;
        }
        String word = words.get(0);
        return word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * The trip other features should talk about: the one whose dates include today, else the next one to start, else
     * the one that ended most recently, else (no dates at all) the newest folder. Null when the vault has no trips.
     */
    @Nullable
    public static TripRef currentTrip(List<TripVaultEntity> trips, boolean unlocked, LocalDate today) {
        TripVaultEntity during = null;
        TripVaultEntity next = null;
        TripVaultEntity last = null;
        TripVaultEntity newest = null;
        for (TripVaultEntity trip : trips) {
            if (newest == null || trip.createdAt.isAfter(newest.createdAt)) {
                newest = trip;
            }
            if (trip.startDate == null || trip.endDate == null) {
                continue;
            }
            if (!trip.startDate.isAfter(today) && !trip.endDate.isBefore(today)) {
                during = trip;
            } else if (trip.startDate.isAfter(today)) {
                if (next == null || trip.startDate.isBefore(next.startDate)) {
                    next = trip;
                }
            } else if (last == null || trip.endDate.isAfter(last.endDate)) {
                last = trip;
            }
        }
        TripVaultEntity chosen = during != null ? during : next != null ? next : last != null ? last : newest;
        return chosen == null ? null
                : new TripRef(chosen.id, unlocked ? chosen.title : null, chosen.startDate, chosen.endDate,
                        chosen.sample);
    }

    private static List<String> countries(@Nullable String commaSeparated) {
        if (commaSeparated == null || commaSeparated.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> codes = new ArrayList<>();
        for (String code : commaSeparated.split(",")) {
            if (!code.trim().isEmpty()) {
                codes.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        return codes;
    }

    private static List<String> words(@Nullable String name) {
        if (name == null) {
            return Collections.emptyList();
        }
        String cleaned = name.toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}]+", " ").trim();
        return cleaned.isEmpty() ? Collections.emptyList() : Arrays.asList(cleaned.split(" "));
    }
}
