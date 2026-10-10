package com.safarsaathi.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.safarsaathi.backend.FixedClockConfig;
import com.safarsaathi.backend.trip.TripRepository;

/**
 * APP_MODE=prod: the demo controls do not exist, so nobody can delay, cancel or reset trips through them, while the
 * health and trip APIs work as usual. (Demo mode is covered by DemoControllerTest.)
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"},
        properties = "safarsaathi.mode=prod") // beats the mode=demo line in no-keys.properties
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class DemoControlsProdModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TripRepository tripRepository;

    @Test
    void demoControlsAreNotCreatedInProdMode() {
        assertThat(context.getBeanNamesForType(DemoController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DemoService.class)).isEmpty();
    }

    @Test
    void everyDemoMutationAnswers404AndChangesNothing() throws Exception {
        mockMvc.perform(post("/api/demo/trigger-delay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":1,\"blockId\":1,\"minutes\":90}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/demo/trigger-cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":1,\"blockId\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/demo/reset"))
                .andExpect(status().isNotFound());

        // The seeded trip is untouched: still version 1.
        assertThat(tripRepository.findById(1L).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void healthAndTripApisStillWork() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.mode").value("prod"));
        mockMvc.perform(get("/api/trips/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
