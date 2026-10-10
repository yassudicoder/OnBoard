package com.safarsaathi.routing.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The BOM-T2 demo map (mock-data/airports/BOM-T2/map.json) is complete and consistent, and the validator catches each
 * kind of mistake. Runs with the backend's tests (./mvnw test), which put the mock-data folder on the classpath.
 */
class AirportMapValidatorTest {

    /** Strict reader: a misspelt field name in map.json fails here instead of being skipped. */
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /** A fresh copy of the map for every call, so a test can break it without affecting the others. */
    static AirportMap bomT2() throws IOException {
        try (InputStream in = AirportMapValidatorTest.class.getClassLoader()
                .getResourceAsStream("mock-data/airports/BOM-T2/map.json")) {
            assertThat(in).as("map.json on the classpath").isNotNull();
            return JSON.readValue(in, AirportMap.class);
        }
    }

    @Test
    void bomT2_hasNoMistakes() throws IOException {
        assertThat(AirportMapValidator.problems(bomT2())).isEmpty();
    }

    @Test
    void bomT2_hasWhatTheBriefAsksFor() throws IOException {
        AirportMap map = bomT2();
        Map<NodeType, Integer> counts = new EnumMap<>(NodeType.class);
        for (MapNode node : map.nodes) {
            counts.merge(node.type, 1, Integer::sum);
        }
        assertThat(map.nodes).hasSizeBetween(40, 70);
        assertThat(counts.get(NodeType.ENTRY)).isGreaterThanOrEqualTo(2);
        assertThat(counts.get(NodeType.CHECKIN_ROW)).isGreaterThanOrEqualTo(4);
        assertThat(counts.get(NodeType.GATE)).isGreaterThanOrEqualTo(10);
        assertThat(counts.get(NodeType.PIER_JUNCTION)).isEqualTo(2);
        assertThat(counts).containsKeys(NodeType.BAG_DROP, NodeType.SECURITY, NodeType.LIFT, NodeType.WASHROOM,
                NodeType.HELP_DESK, NodeType.BAGGAGE_SERVICES, NodeType.LOST_FOUND, NodeType.BAGGAGE_BELT);
        assertThat(map.floors).extracting(floor -> floor.id).containsExactly("DEP", "ARR");
        // Honest labelling (CLAUDE.md): our own drawing, not checked on site.
        assertThat(map.verified).isFalse();
        assertThat(map.note).isEqualTo("Demo schematic, not an official airport map");
    }

    @Test
    void bomT2_gatesAndQrAnchorsCanBeLookedUp() throws IOException {
        AirportMap map = bomT2();
        assertThat(map.gate("32").id).isEqualTo("gate_32");
        assertThat(map.gate("41").name("mr")).isEqualTo("गेट 41");
        assertThat(map.gate("99")).isNull();
        assertThat(map.anchorNode("BOM-T2-ENTRY-4").id).isEqualTo("entry_4");
        assertThat(map.anchorNode("NO-SUCH-QR")).isNull();
        assertThat(map.node("lost_found").hint("en")).contains("Ground Transportation Lobby");
        assertThat(map.nodesOnFloor("ARR")).extracting(node -> node.id).contains("baggage_services", "lost_found");
    }

    @Test
    void namesFallBackToEnglish() throws IOException {
        MapNode gate = bomT2().gate("32");
        assertThat(gate.name("ta")).isEqualTo("Gate 32"); // no Tamil yet
        assertThat(gate.name("hi")).isEqualTo("गेट 32");
    }

    // ---- The validator catches mistakes ----

    @Test
    void catches_duplicateIds_andWalkwaysToNowhere() throws IOException {
        AirportMap map = bomT2();
        map.nodes.get(1).id = map.nodes.get(0).id;
        map.edges.get(0).to = "no_such_place";

        assertThat(String.join("\n", AirportMapValidator.problems(map)))
                .contains("is used twice")
                .contains("joins a place that does not exist");
    }

    @Test
    void catches_aWrongLength() throws IOException {
        AirportMap map = bomT2();
        map.edges.get(0).distanceM += 25;

        assertThat(AirportMapValidator.problems(map)).anyMatch(problem -> problem.contains("m apart"));
    }

    @Test
    void catches_aWayAroundSecurity() throws IOException {
        AirportMap map = bomT2();
        MapEdge sneaky = copy(map.edges.get(0));
        sneaky.from = "checkin_a";
        sneaky.to = "gate_35"; // landside -> airside, no checkpoint
        sneaky.distanceM = Math.hypot(map.node("checkin_a").x - map.node("gate_35").x,
                map.node("checkin_a").y - map.node("gate_35").y);
        map.edges.add(sneaky);

        assertThat(AirportMapValidator.problems(map))
                .anyMatch(problem -> problem.contains("airside without passing security"));
    }

    @Test
    void catches_stairsMarkedStepFree_andAMissingMarathiName() throws IOException {
        AirportMap map = bomT2();
        for (MapEdge edge : map.edges) {
            if (edge.kind == EdgeKind.STAIRS) {
                edge.stepFree = true;
            }
        }
        map.node("gate_32").names.remove("mr");

        assertThat(String.join("\n", AirportMapValidator.problems(map)))
                .contains("so stepFree must be false")
                .contains("Place gate_32 has no name in language \"mr\"");
    }

    @Test
    void catches_anUnreachableGate() throws IOException {
        AirportMap map = bomT2();
        map.edges.removeIf(edge -> "pier_d_sign".equals(edge.to) && "hub".equals(edge.from));

        List<String> problems = AirportMapValidator.problems(map);
        assertThat(problems).contains("Gate 41 cannot be reached from Entry Gate 1.");
    }

    @Test
    void catches_anUnverifiedMapThatDoesNotSaySo() throws IOException {
        AirportMap map = bomT2();
        map.note = "Mumbai T2";

        assertThat(AirportMapValidator.problems(map)).contains("An unverified map must say \"Demo schematic\" in its note.");
    }

    private static MapEdge copy(MapEdge edge) {
        MapEdge copy = new MapEdge();
        copy.from = edge.from;
        copy.to = edge.to;
        copy.distanceM = edge.distanceM;
        copy.kind = edge.kind;
        copy.stepFree = edge.stepFree;
        copy.oneWay = edge.oneWay;
        copy.zone = edge.zone;
        copy.passesCheckpoint = edge.passesCheckpoint;
        return copy;
    }
}
