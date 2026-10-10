package com.safarsaathi.backend.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * Calls GET /api/health through MockMvc: a pretend HTTP client that goes through all of Spring's web layer
 * without opening a real network port.
 *
 * <p>Fixed settings from no-keys.properties (demo mode, every key blank), so the test gives the same result on
 * every laptop, whatever is in .env or the environment (test properties beat both).
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthSaysUpAndListsAllSevenProviders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.app").value("onboard-backend"))
                .andExpect(jsonPath("$.mode").value("demo"))
                .andExpect(jsonPath("$.zone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.providers", aMapWithSize(7)))
                .andExpect(jsonPath("$.providers.flightStatus").value("MOCK"))
                .andExpect(jsonPath("$.providers.ai").value("MOCK"))
                .andExpect(jsonPath("$.providers.directions").value("MOCK"))
                .andExpect(jsonPath("$.providers.push").value("MOCK"))
                .andExpect(jsonPath("$.providers.calendar").value("MOCK"))
                .andExpect(jsonPath("$.providers.voice").value("MOCK"))
                .andExpect(jsonPath("$.providers.weather").value("REAL"));
    }

    @Test
    void timeIsNowWithTheIndianOffset() throws Exception {
        String body = mockMvc.perform(get("/api/health")).andReturn().getResponse().getContentAsString();
        String time = JsonPath.read(body, "$.time");

        OffsetDateTime parsed = OffsetDateTime.parse(time); // throws if it is not ISO-8601 with an offset
        assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.of("+05:30"));
        assertThat(Duration.between(parsed.toInstant(), Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }
}
