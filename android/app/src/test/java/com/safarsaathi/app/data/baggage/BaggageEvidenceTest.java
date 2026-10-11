package com.safarsaathi.app.data.baggage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Photo fingerprints, the staff QR text and the real baggage-facts.json file. */
public class BaggageEvidenceTest {

    @Test
    public void sha256MatchesTheStandardTestVector() throws Exception {
        // FIPS 180-2 test vector for "abc".
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                EvidenceHash.sha256(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    public void oneChangedByteChangesTheFingerprint() throws Exception {
        String a = EvidenceHash.sha256(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        String b = EvidenceHash.sha256(new ByteArrayInputStream(new byte[] {1, 2, 4}));
        assertFalse(a.equals(b));
    }

    @Test
    public void staffCodeListsTheFactsAndPhotoFingerprints() {
        ClaimDraft draft = new ClaimDraft();
        draft.issue = BagIssue.DAMAGE.name();
        draft.flightNo = "IJ 512";
        draft.bagTag = "0098 123456";
        draft.pirRef = "BOMIJ12345";
        draft.photos.add(new EvidencePhoto("a.jpg", EvidencePhoto.Stage.BEFORE,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "2026-10-11T01:00:00Z", null, "CAMERA"));
        String text = StaffCode.text(draft);
        assertTrue(text.contains("Issue: damaged bag"));
        assertTrue(text.contains("Flight: IJ 512"));
        assertTrue(text.contains("Bag tag: 0098123456"));
        assertTrue(text.contains("PIR: BOMIJ12345"));
        assertTrue(text.contains("Before photos (SHA-256, first 16): ba7816bf8f01cfea"));
        assertFalse("empty fields are left out", text.contains("Airline:"));
    }

    @Test
    public void factsFileHasSourcedDeadlinesAndLostAndFound() throws Exception {
        // Unit tests run in android/app; the file ships from the repo-root mock-data folder.
        try (Reader reader = new InputStreamReader(
                new FileInputStream("../../mock-data/baggage/" + BaggageFacts.ASSET), StandardCharsets.UTF_8)) {
            BaggageFacts facts = BaggageFacts.parse(reader);
            assertEquals(7, facts.deadlines.damageDays);
            assertEquals(21, facts.deadlines.delayDays);
            assertTrue(facts.deadlines.sourceUrl.startsWith("https://"));
            assertFalse(facts.deadlines.lastChecked.isEmpty());
            assertTrue(facts.lostFound.portalUrl.startsWith("https://"));
            assertTrue(facts.lostFound.sourceUrl.startsWith("https://"));
            assertFalse(facts.lostFound.toCollect.isEmpty());
            assertFalse(facts.escalation.isEmpty());
            for (BaggageFacts.Step step : facts.escalation) {
                assertFalse(step.title.isEmpty());
                assertFalse(step.lastChecked.isEmpty());
            }
        }
    }
}
