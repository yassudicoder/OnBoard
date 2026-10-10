package com.safarsaathi.routing.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.EdgeKind;
import com.safarsaathi.routing.map.MapNode;
import com.safarsaathi.routing.map.NodeType;
import com.safarsaathi.routing.map.Zone;

/**
 * The route finder on the BOM-T2 demo map (mock-data/airports/BOM-T2/map.json, on the backend's test classpath):
 * quickest path, step-free, security, walking speeds, and the progress maths the navigation screen uses.
 */
class RouterTest {

    static AirportMap map;
    static Router router;

    @BeforeAll
    static void loadMap() throws IOException {
        map = bomT2();
        router = new Router(map);
    }

    static AirportMap bomT2() throws IOException {
        try (InputStream in = RouterTest.class.getClassLoader()
                .getResourceAsStream("mock-data/airports/BOM-T2/map.json")) {
            assertThat(in).as("map.json on the classpath").isNotNull();
            return new ObjectMapper().readValue(in, AirportMap.class);
        }
    }

    static List<String> ids(Route route) {
        List<String> ids = new ArrayList<>();
        for (MapNode node : route.nodes) {
            ids.add(node.id);
        }
        return ids;
    }

    @Test
    void entryToGate_isTheShortestWay_throughSecurity() {
        Route route = router.route("entry_4", "gate_32", WalkProfile.normal());

        assertThat(ids(route)).containsExactly("entry_4", "checkin_d", "checkin_c", "departure_board", "bag_drop",
                "security", "duty_free", "hub", "pier_c_sign", "gate_31", "gate_32");
        assertThat(route.passesCheckpoint()).isTrue();
        // Lengths from the map: 30.4 + 35 + 26.9 + 20 + 15 + 25 + 30 + 58.3 + 20 + 20 metres.
        assertThat(route.totalMetres).isCloseTo(280.6, within(0.05));
        assertThat(route.totalSeconds).isCloseTo(280.6 / 1.2, within(0.05));
    }

    @Test
    void travelator_isUsedWhenItIsQuicker_andOnlyInItsDirection() {
        Route out = router.route("pier_c_sign", "gate_33", WalkProfile.normal());
        Route back = router.route("gate_33", "pier_c_sign", WalkProfile.normal());

        assertThat(out.legs).extracting(leg -> leg.edge.kind).containsExactly(EdgeKind.TRAVELATOR);
        assertThat(out.totalSeconds).isCloseTo(65 / (1.2 + 0.6), within(0.01));
        assertThat(back.legs).extracting(leg -> leg.edge.kind).containsOnly(EdgeKind.WALK);
    }

    @Test
    void stepFree_takesTheLift_insteadOfStairs() {
        Route normal = router.route("gate_32", "belt_2", WalkProfile.normal());
        Route stepFree = router.route("gate_32", "belt_2", WalkProfile.normal().withStepFree(true));

        assertThat(normal.legs).extracting(leg -> leg.edge.kind).contains(EdgeKind.STAIRS);
        assertThat(stepFree.legs).extracting(leg -> leg.edge.kind)
                .contains(EdgeKind.LIFT).doesNotContain(EdgeKind.STAIRS, EdgeKind.ESCALATOR);
        assertThat(stepFree.totalSeconds).isGreaterThan(normal.totalSeconds); // the lift wait
    }

    @Test
    void noRoute_skipsSecurity_fromAnyLandsidePlaceToAnyGate() {
        for (MapNode from : map.nodes) {
            if (from.zone != Zone.LANDSIDE || !"DEP".equals(from.floor)) {
                continue;
            }
            for (MapNode gate : map.nodes) {
                if (gate.type != NodeType.GATE) {
                    continue;
                }
                Route route = router.route(from.id, gate.id, WalkProfile.normal());
                assertThat(route).as(from.id + " -> " + gate.id).isNotNull();
                assertThat(route.passesCheckpoint()).as(from.id + " -> " + gate.id).isTrue();
            }
        }
    }

    @Test
    void airsideBackToLandside_neverGoesBackThroughSecurity() {
        Route route = router.route("hub", "entry_1", WalkProfile.normal().withStepFree(true));

        assertThat(route.passesCheckpoint()).isFalse();
        assertThat(ids(route)).contains("arr_exit", "gt_lobby"); // out through arrivals, as the one-way walkways say
    }

    @Test
    void slowerPace_sameWay_moreTime() {
        Route normal = router.route("entry_1", "gate_45", WalkProfile.normal());
        Route slow = router.route("entry_1", "gate_45", WalkProfile.slow());

        assertThat(ids(slow)).isEqualTo(ids(normal));
        assertThat(slow.totalSeconds).isGreaterThan(normal.totalSeconds * 1.3);
    }

    @Test
    void sameStartAndDestination_isJustTheArrival() {
        Route route = router.route("gate_32", "gate_32", WalkProfile.normal());

        assertThat(route.legs).isEmpty();
        assertThat(route.totalMetres).isZero();
        assertThat(route.steps).extracting(step -> step.maneuver).containsExactly(Maneuver.ARRIVE);
    }

    @Test
    void unknownPlace_isRefused() {
        assertThatThrownBy(() -> router.route("entry_9", "gate_32", WalkProfile.normal()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entry_9");
    }

    @Test
    void sameQuestion_sameAnswer() {
        for (int i = 0; i < 5; i++) {
            assertThat(ids(router.route("entry_1", "gate_43", WalkProfile.normal())))
                    .isEqualTo(ids(router.route("entry_1", "gate_43", WalkProfile.normal())));
        }
    }

    @Test
    void secondsFrom_listsTheNearestPlacesFirst() {
        Map<MapNode, Double> times = router.secondsFrom("hub", WalkProfile.normal());

        MapNode nearestWashroom = null;
        for (MapNode node : times.keySet()) {
            if (node.type == NodeType.WASHROOM) {
                nearestWashroom = node;
                break;
            }
        }
        assertThat(nearestWashroom.id).isEqualTo("washroom_hub");
        assertThat(times.get(map.node("hub"))).isZero();
        assertThat(times.get(map.node("washroom_hub"))).isLessThan(times.get(map.node("washroom_c")));
    }

    // ---- Progress maths ----

    @Test
    void pointAt_movesAlongTheWalkways() {
        Route route = router.route("entry_4", "gate_32", WalkProfile.normal());

        RoutePoint start = route.pointAt(0);
        assertThat(start.x).isEqualTo(135);
        assertThat(start.y).isEqualTo(0);
        RoutePoint halfWayAlongCheckIn = route.pointAt(30.4 + 17.5); // checkin_d (140,30) -> checkin_c (105,30)
        assertThat(halfWayAlongCheckIn.x).isCloseTo(122.5, within(0.1));
        assertThat(halfWayAlongCheckIn.y).isCloseTo(30, within(0.01));
        assertThat(halfWayAlongCheckIn.bearing).isCloseTo(270, within(0.01)); // walking west
        RoutePoint end = route.pointAt(route.totalMetres + 50);
        assertThat(end.x).isEqualTo(30);
        assertThat(end.y).isEqualTo(180);
    }

    @Test
    void pointAt_changesFloorHalfWayThroughALift() {
        Route route = router.route("gate_32", "belt_2", WalkProfile.normal().withStepFree(true));
        double liftStart = -1;
        for (RouteLeg leg : route.legs) {
            if (leg.edge.kind == EdgeKind.LIFT) {
                liftStart = leg.startMetres;
            }
        }

        assertThat(route.pointAt(liftStart + 1).floor).isEqualTo("DEP");
        assertThat(route.pointAt(liftStart + 4).floor).isEqualTo("ARR");
    }

    @Test
    void secondsAndMetres_convertBothWays() {
        Route route = router.route("gate_32", "belt_2", WalkProfile.normal().withStepFree(true));

        for (double metres = 0; metres < route.totalMetres; metres += 7.3) {
            assertThat(route.metresAt(route.secondsAt(metres))).isCloseTo(metres, within(0.001));
        }
        assertThat(route.secondsAt(route.totalMetres)).isCloseTo(route.totalSeconds, within(0.001));
    }

    @Test
    void metresAtNode_andStepIndex() {
        Route route = router.route("entry_4", "gate_32", WalkProfile.normal());

        assertThat(route.metresAtNode("entry_4")).isZero();
        assertThat(route.metresAtNode("security")).isCloseTo(30.4 + 35 + 26.9 + 20 + 15, within(0.05));
        assertThat(route.metresAtNode("gate_41")).isEqualTo(-1);
        assertThat(route.steps.get(route.stepIndexAt(0)).maneuver).isEqualTo(Maneuver.DEPART);
        assertThat(route.steps.get(route.stepIndexAt(route.metresAtNode("security") + 1)).maneuver)
                .isEqualTo(Maneuver.SECURITY);
        assertThat(route.steps.get(route.stepIndexAt(route.totalMetres)).maneuver).isEqualTo(Maneuver.ARRIVE);
        assertThat(route.nextNodeAt(31).id).isEqualTo("checkin_c");
    }
}
