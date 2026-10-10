package com.safarsaathi.app.ui.navigate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.airport.MapJson;
import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.EdgeKind;
import com.safarsaathi.routing.map.NodeType;
import com.safarsaathi.routing.route.InstructionText;
import com.safarsaathi.routing.route.Maneuver;
import com.safarsaathi.routing.route.RouteLeg;
import com.safarsaathi.routing.route.WalkProfile;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The Navigate tab's state machine on the real bundled BOM-T2 demo map: preview, turn-by-turn, arrival, QR codes on
 * and off the route, detours, settings, and the turn check from the motion sensors.
 */
public class NavSessionTest {

    private AirportMap map;
    private NavSession session;

    @Before
    public void loadMap() throws IOException {
        String json = new String(Files.readAllBytes(Paths.get("../../mock-data/airports/BOM-T2/map.json")),
                StandardCharsets.UTF_8);
        String areas = new String(Files.readAllBytes(Paths.get("../../mock-data/airports/BOM-T2/areas.geojson")),
                StandardCharsets.UTF_8);
        map = MapJson.parse(MapJson.combine(json, areas)).map;
        session = new NavSession();
        session.setMap(map);
    }

    /** Entry Gate 4 → Gate 32, ready to start. */
    private void previewEntry4ToGate32() {
        session.setHere("entry_4", NavSession.HereSource.QR);
        assertEquals(NavSession.Event.NONE, session.setDestination("gate_32"));
    }

    @Test
    public void destinationWithoutPosition_asksWhereYouAre() {
        session.setDestination("gate_32");

        assertEquals(NavSession.Mode.PREVIEW, session.mode());
        assertNull(session.route());
        assertFalse(session.start());

        session.setHere("entry_4", NavSession.HereSource.CHOSEN);
        assertNotNull(session.route());
        assertEquals("entry_4", session.route().start().id);
    }

    @Test
    public void startThenWalk_bannerShowsTheNextTurn_withDistance() {
        previewEntry4ToGate32();
        assertTrue(session.start());

        // Just started: "Head towards Check-in row D", 30 m to the first turn.
        assertEquals(Maneuver.DEPART, session.bannerStep().maneuver);
        assertEquals(30.4, session.bannerDistance(), 0.05);
        assertFalse(session.positionIsEstimate()); // scanned a QR code

        session.walk(10);
        assertEquals(Maneuver.LEFT, session.bannerStep().maneuver);
        assertEquals("checkin_d", session.bannerStep().at.id);
        assertEquals(20.4, session.bannerDistance(), 0.05);
        assertTrue(session.positionIsEstimate()); // counted steps from here on
        assertNull(session.thenStep()); // the turn after it is 62 m further: no "Then"
        assertEquals("Turn left at Check-in row D",
                InstructionText.forLanguage("en").text(session.bannerStep(), map));
        assertEquals(session.route().totalMetres - 10, session.remainingMetres(), 0.01);
    }

    @Test
    public void demoWalk_reachesTheGate() {
        previewEntry4ToGate32();
        session.start();

        NavSession.Event last = NavSession.Event.NONE;
        for (int i = 0; i < 10_000 && session.mode() == NavSession.Mode.NAVIGATING; i++) {
            last = session.walkFor(0.5);
        }

        assertEquals(NavSession.Event.ARRIVED, last);
        assertEquals(NavSession.Mode.ARRIVED, session.mode());
        assertEquals("gate_32", session.here().id);
    }

    @Test
    public void qrCodeOnTheRoute_putsYouThereExactly() {
        previewEntry4ToGate32();
        session.start();
        session.walk(40); // counted steps

        assertEquals(NavSession.Event.POSITION_CONFIRMED,
                session.setHere("security", NavSession.HereSource.QR));
        assertEquals(session.route().metresAtNode("security"), session.progress(), 0.001);
        assertFalse(session.positionIsEstimate());
    }

    @Test
    public void qrCodeOffTheRoute_reroutesFromThere() {
        previewEntry4ToGate32();
        session.start();
        session.walk(150);

        assertEquals(NavSession.Event.REROUTED, session.setHere("pier_d_sign", NavSession.HereSource.QR));
        assertEquals("pier_d_sign", session.route().start().id);
        assertEquals("gate_32", session.route().destination().id);
        assertEquals(0, session.progress(), 0.001);
        assertEquals(NavSession.Mode.NAVIGATING, session.mode());
    }

    @Test
    public void stepFreeWhileWalking_reroutesWithTheLift() {
        session.setHere("gate_32", NavSession.HereSource.CHOSEN);
        session.setDestination("belt_2");
        session.start();

        assertEquals(NavSession.Event.ROUTE_UPDATED, session.setProfile(WalkProfile.normal().withStepFree(true)));
        boolean usesLift = false;
        for (RouteLeg leg : session.route().legs) {
            usesLift |= leg.edge.kind == EdgeKind.LIFT;
            assertTrue(leg.edge.stepFree);
        }
        assertTrue(usesLift);
    }

    @Test
    public void detour_toTheWashrooms_thenContinueToTheGate() {
        previewEntry4ToGate32();
        session.start();
        session.setHere("hub", NavSession.HereSource.QR);
        assertEquals("washroom_hub", session.nearest(NodeType.WASHROOM).id);

        session.detourTo("washroom_hub");
        assertEquals("washroom_hub", session.destination().id);
        assertEquals("gate_32", session.resumeDestination().id);
        while (session.mode() == NavSession.Mode.NAVIGATING) {
            session.walkFor(1);
        }
        assertEquals(NavSession.Mode.ARRIVED, session.mode());

        session.continueToResume();
        assertEquals(NavSession.Mode.NAVIGATING, session.mode());
        assertEquals("washroom_hub", session.route().start().id);
        assertEquals("gate_32", session.route().destination().id);
        assertNull(session.resumeDestination());
    }

    @Test
    public void turningTheExpectedWay_nearTheCorner_movesYouToIt() {
        previewEntry4ToGate32();
        session.start();
        session.onHeading(100); // walking north-ish, far from the turn: just remembered
        session.walk(12);       // 18 m before the left turn at Check-in row D

        assertEquals(NavSession.Event.NONE, session.onHeading(102)); // still straight on
        assertEquals(NavSession.Event.NONE, session.onHeading(170)); // a RIGHT turn: not the expected one
        session.onHeading(100);
        assertEquals(NavSession.Event.TURN_CONFIRMED, session.onHeading(20)); // 80° to the left
        assertEquals(30.9, session.progress(), 0.01);
        assertEquals(Maneuver.RIGHT, session.bannerStep().maneuver); // next: right at the departure board
    }

    @Test
    public void turnFarFromAnyCorner_isIgnored() {
        previewEntry4ToGate32();
        session.start();
        session.onHeading(0);

        assertEquals(NavSession.Event.NONE, session.onHeading(270)); // 30 m before the turn: too far
        assertEquals(0, session.progress(), 0.001);
    }

    @Test
    public void stop_whileWalking_keepsYouAtTheNearestPlace() {
        previewEntry4ToGate32();
        session.start();
        session.walk(30.4 + 30); // most of the way from Check-in row D to Check-in row C

        session.stop();

        assertEquals(NavSession.Mode.EXPLORE, session.mode());
        assertEquals("checkin_c", session.here().id);
        assertTrue(session.positionIsEstimate());
        assertNull(session.route());
    }

    @Test
    public void liftRide_bannerShowsTheLift_untilOnTheOtherFloor() {
        session.setProfile(WalkProfile.normal().withStepFree(true));
        session.setHere("gate_32", NavSession.HereSource.CHOSEN);
        session.setDestination("belt_2");
        session.start();
        double liftStart = 0;
        for (RouteLeg leg : session.route().legs) {
            if (leg.edge.kind == EdgeKind.LIFT) {
                liftStart = leg.startMetres;
            }
        }

        session.walk(liftStart + 2);

        assertEquals(Maneuver.LIFT_DOWN, session.bannerStep().maneuver);
        assertEquals("Take Lift A down to Arrivals", InstructionText.forLanguage("en").text(session.bannerStep(), map));
        session.walk(4);
        assertEquals("ARR", session.position().floor);
    }
}
