package com.safarsaathi.backend.airport;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/airports/{code}/{terminal}/map through MockMvc: the contract with the Android app. */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@AutoConfigureMockMvc
class AirportMapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bomT2_mapWithFloorsPlacesWalkwaysAnchorsAndAreas() throws Exception {
        mockMvc.perform(get("/api/airports/BOM/T2/map"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"BOM-T2-v2\""))
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(jsonPath("$.airport").value("BOM"))
                .andExpect(jsonPath("$.terminal").value("T2"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.note").value("Demo schematic, not an official airport map"))
                .andExpect(jsonPath("$.floors.length()").value(2))
                .andExpect(jsonPath("$.nodes.length()").value(50))
                .andExpect(jsonPath("$.edges.length()").value(56))
                .andExpect(jsonPath("$.anchors.length()").value(8))
                .andExpect(jsonPath("$.nodes[?(@.id == 'gate_32')].names.mr").value("गेट 32"))
                .andExpect(jsonPath("$.areas.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.areas.features.length()").value(7));
    }

    @Test
    void caseDoesNotMatter() throws Exception {
        mockMvc.perform(get("/api/airports/bom/t2/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.airport").value("BOM"));
    }

    @Test
    void unchangedMap_isNotSentAgain() throws Exception {
        mockMvc.perform(get("/api/airports/BOM/T2/map").header("If-None-Match", "\"BOM-T2-v2\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void unknownTerminal_is404ProblemDetails() throws Exception {
        mockMvc.perform(get("/api/airports/DEL/T1/map"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No map for airport DEL terminal T1 (the pilot has BOM T2 only)"));
    }
}
