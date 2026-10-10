package com.safarsaathi.app.data.ride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Reads the real bundled file (repo-root mock-data/airports/ride-points.json, which the app ships as an asset) and
 * checks the honesty rules: every point has a source and a check date, unverified points are approximate, and
 * the times are worked out as documented.
 */
public class RidePointsTest {

    private static RidePoints realFile() throws IOException {
        return RidePoints.parse(new String(Files.readAllBytes(
                Paths.get("../../mock-data/airports/" + RidePoints.ASSET)), StandardCharsets.UTF_8));
    }

    @Test
    public void pilotTerminal_hasADropoffPoint_withSourceAndCheckDate() throws IOException {
        RidePoints.Point t2 = realFile().dropoff("BOM", "T2");

        assertNotNull(t2);
        assertEquals("Mumbai T2", t2.buttonName());
        assertTrue(t2.sourceUrl.startsWith("https://"));
        assertNotNull(t2.lastChecked);
        assertNotNull(t2.lat);
        assertNotNull(t2.lng);
    }

    @Test
    public void unverifiedPoint_becomesAnApproximatePlace() throws IOException {
        RidePoints.Point t2 = realFile().dropoff("bom", "t2"); // case does not matter

        assertFalse("nobody has checked T2's point on site yet", t2.verified);
        assertTrue(t2.toPlace().approximate);
    }

    @Test
    public void leaveBy_isDepartureMinusTimeAtAirportMinusRide() throws IOException {
        RidePoints.LeaveByRule rule = realFile().leaveByRule("BOM", "T2");
        Instant departs0800Ist = Instant.parse("2026-10-12T02:30:00Z");

        assertNotNull(rule);
        // 08:00 - 90 min at the airport - 20 min ride = 06:10 IST (00:40 UTC)
        assertEquals(Instant.parse("2026-10-12T00:40:00Z"), rule.leaveBy(departs0800Ist));
    }

    @Test
    public void alternativeFromANearbyTerminal_getsARideWithTheEstimate() throws IOException {
        RidePoints.NearbyRide toT1 = realFile().nearby("BOM", "T2", "BOM", "T1");

        assertNotNull(toT1);
        assertEquals("BOM T1", toT1.to.buttonName());
        assertEquals(35, toT1.estimateMinutes);
    }

    @Test
    public void alternativeFromTheSameTerminal_orAnUnlistedAirport_getsNoRide() throws IOException {
        RidePoints points = realFile();

        assertNull(points.nearby("BOM", "T2", "BOM", "T2"));
        assertNull(points.nearby("BOM", "T2", "DEL", "T3"));
    }

    @Test
    public void pointWithoutASource_isIgnored() {
        RidePoints points = RidePoints.parse("{\"points\": [{\"id\": \"X\", \"airport\": \"BOM\", \"terminal\": \"T2\","
                + " \"kind\": \"DROPOFF\", \"name\": \"Somewhere\", \"lat\": 19.1, \"lng\": 72.9}]}");

        assertNull(points.dropoff("BOM", "T2"));
    }

    @Test
    public void missingVerifiedFlag_meansNotVerified() {
        RidePoints points = RidePoints.parse("{\"points\": [{\"id\": \"X\", \"airport\": \"BOM\", \"terminal\": \"T2\","
                + " \"kind\": \"DROPOFF\", \"name\": \"Somewhere\", \"sourceUrl\": \"https://example.org\"}]}");

        assertTrue(points.dropoff("BOM", "T2").toPlace().approximate);
    }

    @Test
    public void brokenFile_givesNoPoints_insteadOfACrash() {
        RidePoints points = RidePoints.parse("{ not json");

        assertNull(points.dropoff("BOM", "T2"));
        assertNull(points.leaveByRule("BOM", "T2"));
        assertNull(points.nearby("BOM", "T2", "BOM", "T1"));
    }
}
