package com.safarsaathi.app.data.baggage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;

/** Written-complaint deadlines (Montreal Convention limits from baggage-facts.json). */
public class ClaimDeadlineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private static BaggageFacts.Deadlines limits() {
        BaggageFacts.Deadlines limits = new BaggageFacts.Deadlines();
        limits.damageDays = 7;
        limits.delayDays = 21;
        limits.lostAfterDays = 21;
        return limits;
    }

    private static ClaimDraft draft(BagIssue issue, String date, String delivered) {
        ClaimDraft draft = new ClaimDraft();
        draft.issue = issue.name();
        draft.date = date;
        draft.deliveredDate = delivered;
        return draft;
    }

    @Test
    public void damageIsSevenDaysFromReceipt() {
        ClaimDeadline deadline = ClaimDeadline.of(draft(BagIssue.DAMAGE, "11/10/2026", ""), limits(), TODAY);
        assertEquals(ClaimDeadline.Kind.COMPLAIN_BY, deadline.kind);
        assertEquals(LocalDate.of(2026, 10, 18), deadline.date);
        assertEquals(7, deadline.daysLeft);
    }

    @Test
    public void pilferageUsesTheDamageLimit() {
        ClaimDeadline deadline = ClaimDeadline.of(draft(BagIssue.PILFERAGE, "9/10/2026", ""), limits(), TODAY);
        assertEquals(LocalDate.of(2026, 10, 16), deadline.date);
        assertEquals(5, deadline.daysLeft);
    }

    @Test
    public void deliveredLateBagIsTwentyOneDaysFromDelivery() {
        ClaimDeadline deadline = ClaimDeadline.of(draft(BagIssue.DELAY, "01/10/2026", "05/10/2026"), limits(), TODAY);
        assertEquals(ClaimDeadline.Kind.COMPLAIN_BY, deadline.kind);
        assertEquals(LocalDate.of(2026, 10, 26), deadline.date);
    }

    @Test
    public void missingBagShowsWhenItMayBeTreatedAsLost() {
        ClaimDeadline deadline = ClaimDeadline.of(draft(BagIssue.DELAY, "11/10/2026", ""), limits(), TODAY);
        assertEquals(ClaimDeadline.Kind.LOST_FROM, deadline.kind);
        assertEquals(LocalDate.of(2026, 11, 1), deadline.date);
        assertEquals(21, deadline.daysLeft);
    }

    @Test
    public void passedDeadlineHasNegativeDaysLeft() {
        ClaimDeadline deadline = ClaimDeadline.of(draft(BagIssue.DAMAGE, "01/10/2026", ""), limits(), TODAY);
        assertEquals(-3, deadline.daysLeft);
    }

    @Test
    public void missingOrImpossibleDateGivesNoDeadline() {
        assertNull(ClaimDeadline.of(draft(BagIssue.DAMAGE, "", ""), limits(), TODAY));
        assertNull(ClaimDeadline.of(draft(BagIssue.DAMAGE, "31/02/2026", ""), limits(), TODAY));
        assertNull(ClaimDeadline.of(draft(BagIssue.DAMAGE, "2026-10-11", ""), limits(), TODAY));
        assertNull(ClaimDeadline.of(draft(BagIssue.DELAY, "", ""), limits(), TODAY));
    }

    @Test
    public void bagTagNeedsTenDigits() {
        assertEquals(true, ClaimDraft.isValidBagTag(""));
        assertEquals(true, ClaimDraft.isValidBagTag("0098 123456"));
        assertEquals(false, ClaimDraft.isValidBagTag("12345"));
        assertEquals(false, ClaimDraft.isValidBagTag("00981234567"));
    }
}
