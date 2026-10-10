package com.safarsaathi.backend.connection;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.safarsaathi.backend.MovableClockConfig;
import com.safarsaathi.backend.MovableClockConfig.MovableClock;

/**
 * GET /api/trips/{id}/connections end to end, on the seeded demo trips. The clock starts at 10 Oct 2026 17:30:05 in
 * India; the trip day is 11 Oct. Trip 3's connection (id 2) is in Doha: lands 19:35, next flight 21:55 (Doha time),
 * 60 minutes needed (a DEMO VALUE). Trip 2's (id 1) is in Delhi, which has no minimum connection time in our data.
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@AutoConfigureMockMvc
@Import(MovableClockConfig.class)
class ConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovableClock clock;

    @BeforeEach
    void startFromFreshDemoData() throws Exception {
        clock.set(MovableClockConfig.START);
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    @Test
    void dohaConnectionIsOnTrackWithEveryNumberSourced() throws Exception {
        mockMvc.perform(get("/api/trips/3/connections"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]", aMapWithSize(26)))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].tripId").value(3))
                .andExpect(jsonPath("$[0].inboundBlockId").value(12))
                .andExpect(jsonPath("$[0].outboundBlockId").value(13))
                .andExpect(jsonPath("$[0].inboundFlightNo").value("IJ 1907"))
                .andExpect(jsonPath("$[0].outboundFlightNo").value("MJ 31"))
                .andExpect(jsonPath("$[0].airport").value("DOH"))
                .andExpect(jsonPath("$[0].airportZone").value("Asia/Qatar"))
                .andExpect(jsonPath("$[0].connectionType").value("INTL_INTL"))
                .andExpect(jsonPath("$[0].terminalChange").value(nullValue())) // Doha terminal not given
                .andExpect(jsonPath("$[0].status").value("OK"))
                .andExpect(jsonPath("$[0].certainty").value("PREDICTED"))
                .andExpect(jsonPath("$[0].landsAt").value("2026-10-11T19:35:00+03:00"))
                .andExpect(jsonPath("$[0].nextLeavesAt").value("2026-10-11T21:55:00+03:00"))
                .andExpect(jsonPath("$[0].availableMinutes").value(140))
                .andExpect(jsonPath("$[0].neededMinutes").value(60))
                .andExpect(jsonPath("$[0].projectedSlackMinutes").value(80))
                .andExpect(jsonPath("$[0].mct.minutes").value(60))
                .andExpect(jsonPath("$[0].mct.kind").value("DEMO_VALUE"))
                .andExpect(jsonPath("$[0].mct.source").value("DEMO VALUE - not an official figure"))
                .andExpect(jsonPath("$[0].mct.sourceUrl").value(nullValue()))
                .andExpect(jsonPath("$[0].mct.lastChecked").value("2026-10-11"))
                .andExpect(jsonPath("$[0].transfer.minutes").value(40))
                .andExpect(jsonPath("$[0].transfer.kind").value("ESTIMATE"))
                .andExpect(jsonPath("$[0].gateClose.minutes").value(20))
                .andExpect(jsonPath("$[0].gateClose.kind").value("OFFICIAL"))
                .andExpect(jsonPath("$[0].gateClose.sourceUrl")
                        .value("https://dohahamadairport.com/airport-guide/at-the-airport/transfers"))
                .andExpect(jsonPath("$[0].ticketType").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].basedOnDemoValue").value(true))
                .andExpect(jsonPath("$[0].reason").value("You land in Doha at 19:35, your next flight leaves at 21:55. "
                        + "Doha needs at least 60 min (demo value). You have 140 min."))
                .andExpect(jsonPath("$[0].missConfirmedAt").value(nullValue()))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-10-10T17:30:05+05:30"))
                .andExpect(jsonPath("$[0].dataAsOf").value("2026-10-10T17:30:05+05:30"));
    }

    @Test
    void ninetyFiveMinutesLateIsLikelyMissedWithTheReasonInDohaTime() throws Exception {
        clock.advance(Duration.ofMinutes(10)); // the delay becomes known at 17:40:05

        mockMvc.perform(post("/api/demo/trigger-delay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\": 3, \"blockId\": 12, \"minutes\": 95}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trips/3/connections"))
                .andExpect(jsonPath("$[0].status").value("LIKELY_MISSED"))
                .andExpect(jsonPath("$[0].certainty").value("PREDICTED"))
                .andExpect(jsonPath("$[0].landsAt").value("2026-10-11T21:10:00+03:00"))
                .andExpect(jsonPath("$[0].availableMinutes").value(45))
                .andExpect(jsonPath("$[0].neededMinutes").value(60))
                .andExpect(jsonPath("$[0].projectedSlackMinutes").value(-15))
                .andExpect(jsonPath("$[0].basedOnDemoValue").value(true))
                .andExpect(jsonPath("$[0].reason").value("You land in Doha at 21:10, your next flight leaves at 21:55. "
                        + "Doha needs at least 60 min (demo value). You have 45 min."))
                .andExpect(jsonPath("$[0].dataAsOf").value("2026-10-10T17:40:05+05:30"));
    }

    @Test
    void delhiConnectionIsUnknownBecauseThereIsNoOfficialMinimum() throws Exception {
        mockMvc.perform(get("/api/trips/2/connections"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].airport").value("DEL"))
                .andExpect(jsonPath("$[0].connectionType").value("DOM_DOM"))
                .andExpect(jsonPath("$[0].terminalChange").value(false)) // T3 to T3
                .andExpect(jsonPath("$[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].certainty").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].neededMinutes").value(nullValue()))
                .andExpect(jsonPath("$[0].projectedSlackMinutes").value(nullValue()))
                .andExpect(jsonPath("$[0].mct").value(nullValue()))
                .andExpect(jsonPath("$[0].gateClose.kind").value("ESTIMATE")) // Delhi states none: team estimate
                .andExpect(jsonPath("$[0].ticketType").value("SINGLE_BOOKING"))
                .andExpect(jsonPath("$[0].basedOnDemoValue").value(false))
                .andExpect(jsonPath("$[0].reason").value("You land in Delhi at 08:20, your next flight leaves at 10:15. "
                        + "OnBoard has no official minimum connection time for Delhi (domestic to domestic), so it "
                        + "can't tell whether 115 min is enough. Ask the airline."));
    }

    @Test
    void tripWithoutConnectionsGivesAnEmptyList() throws Exception {
        mockMvc.perform(get("/api/trips/1/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unknownTripIs404ProblemDetails() throws Exception {
        mockMvc.perform(get("/api/trips/99/connections"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Trip 99 not found"));
    }
}
