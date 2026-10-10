package com.safarsaathi.app.data.airport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.routing.map.AirportMapValidator;
import com.safarsaathi.routing.map.NodeType;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Reading the real bundled map files (repo-root mock-data/airports/BOM-T2, the same files the app ships as assets)
 * with Gson into routing-core's model. Unit tests run with android/app as the working folder.
 */
public class MapJsonTest {

    private static String read(String file) throws IOException {
        return new String(Files.readAllBytes(Paths.get("../../mock-data/airports/BOM-T2/" + file)),
                StandardCharsets.UTF_8);
    }

    private static MapJson.MapData bundled() throws IOException {
        return MapJson.parse(MapJson.combine(read("map.json"), read("areas.geojson")));
    }

    @Test
    public void bundledMap_readsIntoTheSharedModel_withoutMistakes() throws IOException {
        MapJson.MapData data = bundled();

        assertTrue(AirportMapValidator.problems(data.map).isEmpty());
        assertEquals("BOM", data.map.airport);
        assertEquals(2, data.map.version);
        assertFalse(data.map.verified);
        assertEquals("Demo schematic, not an official airport map", data.map.note);
        assertEquals(50, data.map.nodes.size());
        assertEquals(NodeType.GATE, data.map.gate("41").type);
        assertEquals("हरवले-सापडले", data.map.node("lost_found").name("mr"));
    }

    @Test
    public void areas_areReadPerFloor() throws IOException {
        MapJson.MapData data = bundled();

        assertEquals(7, data.areas.size());
        assertEquals(5, data.areasOnFloor("DEP").size());
        assertEquals(2, data.areasOnFloor("ARR").size());
        MapArea pierC = data.areasOnFloor("DEP").get(3);
        assertEquals("Pier C", pierC.name("en"));
        assertEquals(MapArea.KIND_PIER, pierC.kind);
        assertEquals(5, pierC.points.size()); // closed ring: the first point again at the end
    }

    @Test(expected = IllegalArgumentException.class)
    public void notAMap_isRefused() {
        MapJson.parse("{\"airport\":\"BOM\",\"nodes\":[]}");
    }
}
