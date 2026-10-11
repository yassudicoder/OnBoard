package com.safarsaathi.app.data.baggage;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;

/**
 * The written-complaint deadline for a bag problem, worked out from the dates the traveller typed and the time limits
 * in baggage-facts.json (Montreal Convention, Art. 31 and 17). Plain Java, unit tested.
 *
 * <ul>
 *   <li>DAMAGE: date received + damageDays (7).</li>
 *   <li>PILFERAGE: the same limit as damage, and the screen says "confirm with the airline" (the Convention names
 *       damage, not missing items).</li>
 *   <li>DELAY, bag delivered: delivery date + delayDays (21).</li>
 *   <li>DELAY, still missing: no complaint deadline yet; instead the day (landing + lostAfterDays) after which the bag
 *       may be treated as lost.</li>
 * </ul>
 * The app shows these as dates to act by, never as a promise of any outcome.
 */
public final class ClaimDeadline {

    /** What the date means. */
    public enum Kind {
        /** Complain in writing by {@link #date}. */
        COMPLAIN_BY,
        /** The bag is still missing: from {@link #date} it may be treated as lost. */
        LOST_FROM
    }

    public final Kind kind;
    public final LocalDate date;
    /** Days from today until {@link #date}: 0 = today, negative = already passed. */
    public final long daysLeft;

    private ClaimDeadline(Kind kind, LocalDate date, long daysLeft) {
        this.kind = kind;
        this.date = date;
        this.daysLeft = daysLeft;
    }

    private static final DateTimeFormatter TYPED = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    /** "11/10/2026" (also "1/10/2026" after padding) to a date, or null when empty or not a real date. */
    @Nullable
    public static LocalDate parseDate(@Nullable String typed) {
        if (typed == null || typed.trim().isEmpty()) {
            return null;
        }
        String[] parts = typed.trim().split("/");
        if (parts.length != 3) {
            return null;
        }
        String padded = pad(parts[0]) + "/" + pad(parts[1]) + "/" + parts[2];
        try {
            return LocalDate.parse(padded, TYPED);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String pad(String part) {
        return part.length() == 1 ? "0" + part : part;
    }

    /** The deadline for this draft, or null while the needed date is missing or not a real date. */
    @Nullable
    public static ClaimDeadline of(ClaimDraft draft, BaggageFacts.Deadlines limits, LocalDate today) {
        LocalDate date = parseDate(draft.date);
        if (draft.issue() == BagIssue.DELAY) {
            LocalDate delivered = parseDate(draft.deliveredDate);
            if (delivered != null) {
                return at(Kind.COMPLAIN_BY, delivered.plusDays(limits.delayDays), today);
            }
            return date == null ? null : at(Kind.LOST_FROM, date.plusDays(limits.lostAfterDays), today);
        }
        return date == null ? null : at(Kind.COMPLAIN_BY, date.plusDays(limits.damageDays), today);
    }

    private static ClaimDeadline at(Kind kind, LocalDate date, LocalDate today) {
        return new ClaimDeadline(kind, date, ChronoUnit.DAYS.between(today, date));
    }
}
