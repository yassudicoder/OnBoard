package com.safarsaathi.backend.trip;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.safarsaathi.backend.FixedClockConfig;
import com.safarsaathi.backend.demo.DemoTripSeeder;

/**
 * GET /api/trips/{id} through MockMvc (a pretend HTTP client), on the seeded demo data in an in-memory database.
 * The clock is stopped at 10 Oct 2026 17:30:05 in India, so the trip day is 11 Oct 2026 and every time below is exact.
 * These checks are the contract with the Android app: field names, order, nulls and the time format: each time in the
 * local time of its place (+05:30 in India, +03:00 in Doha, +01:00 in London in October).
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoTripSeeder seeder;

    /**
     * Start every test from fresh demo data (ids from 1, version 1, all OK). Test classes with exactly the same
     * annotations share one Spring context and so one database; a test elsewhere that triggers a delay must not
     * change what this class sees. (It also checks that the reloaded data looks exactly like the first load.)
     */
    @BeforeEach
    void reloadDemoTrips() {
        seeder.deleteAllAndReseed();
    }

    @Test
    void tripOneHasTheFullShape() throws Exception {
        mockMvc.perform(get("/api/trips/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // The trip itself: 12 fields.
                .andExpect(jsonPath("$", aMapWithSize(12)))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Client visit: Mumbai to Bengaluru"))
                .andExpect(jsonPath("$.purpose").value("BUSINESS"))
                .andExpect(jsonPath("$.persona").value("BUSINESS"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.travellers").value(1))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.createdAt").value("2026-10-10T17:30:05+05:30"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-10T17:30:05+05:30"))
                // Blocks: earliest start first.
                .andExpect(jsonPath("$.blocks", hasSize(6)))
                .andExpect(jsonPath("$.blocks[*].id", contains(1, 2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.blocks[*].type",
                        contains("FLIGHT", "BAG_CLAIM", "CAB", "MEETING", "HOTEL", "RESTAURANT")))
                .andExpect(jsonPath("$.blocks[*].status", contains("OK", "OK", "OK", "OK", "OK", "OK")))
                // The first block, every one of its 20 fields.
                .andExpect(jsonPath("$.blocks[0]", aMapWithSize(20)))
                .andExpect(jsonPath("$.blocks[0].id").value(1))
                .andExpect(jsonPath("$.blocks[0].tripId").value(1))
                .andExpect(jsonPath("$.blocks[0].type").value("FLIGHT"))
                .andExpect(jsonPath("$.blocks[0].title").value("Flight IJ 512 Mumbai to Bengaluru"))
                .andExpect(jsonPath("$.blocks[0].startTime").value("2026-10-11T08:00:00+05:30"))
                .andExpect(jsonPath("$.blocks[0].endTime").value("2026-10-11T09:45:00+05:30"))
                .andExpect(jsonPath("$.blocks[0].locationName").value("Mumbai Airport T2 (BOM)"))
                .andExpect(jsonPath("$.blocks[0].lat").value(19.0896))
                .andExpect(jsonPath("$.blocks[0].lng").value(72.8656))
                .andExpect(jsonPath("$.blocks[0].provider").value("MOCK_AIR"))
                .andExpect(jsonPath("$.blocks[0].providerRef").value("K7Q2ZM"))
                .andExpect(jsonPath("$.blocks[0].flex").value("FIXED"))
                .andExpect(jsonPath("$.blocks[0].anchor").value(false))
                .andExpect(jsonPath("$.blocks[0].status").value("OK"))
                // value(nullValue()) fails when the field is missing, so this proves it is there AND null.
                .andExpect(jsonPath("$.blocks[0].freeChangeUntilMinutes").value(nullValue()))
                .andExpect(jsonPath("$.blocks[0].changeFeeInr").value(2500))
                .andExpect(jsonPath("$.blocks[0].notes").value(not(nullValue())))
                .andExpect(jsonPath("$.blocks[0].startZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.blocks[0].endZone").value("Asia/Kolkata"))
                // A flight's own fields: 12 of them.
                .andExpect(jsonPath("$.blocks[0].flight", aMapWithSize(12)))
                .andExpect(jsonPath("$.blocks[0].flight.flightNo").value("IJ 512"))
                .andExpect(jsonPath("$.blocks[0].flight.depAirport").value("BOM"))
                .andExpect(jsonPath("$.blocks[0].flight.arrAirport").value("BLR"))
                .andExpect(jsonPath("$.blocks[0].flight.depTerminal").value("T2"))
                .andExpect(jsonPath("$.blocks[0].flight.arrTerminal").value("T1"))
                .andExpect(jsonPath("$.blocks[0].flight.depZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.blocks[0].flight.arrZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.blocks[0].flight.scheduledDep").value("2026-10-11T08:00:00+05:30"))
                .andExpect(jsonPath("$.blocks[0].flight.scheduledArr").value("2026-10-11T09:45:00+05:30"))
                .andExpect(jsonPath("$.blocks[0].flight.estimatedDep").value("2026-10-11T08:00:00+05:30"))
                .andExpect(jsonPath("$.blocks[0].flight.estimatedArr").value("2026-10-11T09:45:00+05:30"))
                .andExpect(jsonPath("$.blocks[0].flight.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.blocks[1].flight").value(nullValue())) // not a flight
                // A few more blocks: a null providerRef, the zero-length cab pickup, the anchor meeting.
                .andExpect(jsonPath("$.blocks[1].providerRef").value(nullValue()))
                .andExpect(jsonPath("$.blocks[2].startTime").value("2026-10-11T10:05:00+05:30"))
                .andExpect(jsonPath("$.blocks[2].endTime").value("2026-10-11T10:05:00+05:30"))
                .andExpect(jsonPath("$.blocks[2].freeChangeUntilMinutes").value(-30))
                .andExpect(jsonPath("$.blocks[3].anchor").value(true))
                .andExpect(jsonPath("$.blocks[3].flex").value("ASK_USER"))
                .andExpect(jsonPath("$.blocks[5].startTime").value("2026-10-11T20:00:00+05:30"))
                // Links: in id order, every field of the first one.
                .andExpect(jsonPath("$.links", hasSize(5)))
                .andExpect(jsonPath("$.links[*].id", contains(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$.links[0]", aMapWithSize(7)))
                .andExpect(jsonPath("$.links[0].id").value(1))
                .andExpect(jsonPath("$.links[0].tripId").value(1))
                .andExpect(jsonPath("$.links[0].fromBlockId").value(1))
                .andExpect(jsonPath("$.links[0].toBlockId").value(2))
                .andExpect(jsonPath("$.links[0].minGapMinutes").value(0))
                .andExpect(jsonPath("$.links[0].travelMinutes").value(0))
                .andExpect(jsonPath("$.links[0].travelMode").value("NONE"))
                .andExpect(jsonPath("$.links[2].travelMinutes").value(75))
                .andExpect(jsonPath("$.links[2].travelMode").value("CAB"));
    }

    @Test
    void tripTwoIsTheMarathiHoliday() throws Exception {
        mockMvc.perform(get("/api/trips/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Leh holiday via Delhi"))
                .andExpect(jsonPath("$.language").value("mr"))
                .andExpect(jsonPath("$.persona").value("ELDERLY"))
                .andExpect(jsonPath("$.purpose").value("HOLIDAY"))
                .andExpect(jsonPath("$.blocks[*].id", contains(7, 8, 9, 10, 11)))
                .andExpect(jsonPath("$.blocks[0].startTime").value("2026-10-11T06:10:00+05:30"))
                .andExpect(jsonPath("$.links[*].id", contains(6, 7, 8, 9)))
                // Between two flights the minimum connection time comes from the airport rules file, not the link.
                .andExpect(jsonPath("$.links[0].minGapMinutes").value(0));
    }

    @Test
    void tripThreeShowsEachTimeInItsAirportsLocalTime() throws Exception {
        mockMvc.perform(get("/api/trips/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Delhi to London via Doha"))
                .andExpect(jsonPath("$.language").value("hi"))
                .andExpect(jsonPath("$.blocks[*].id", contains(12, 13)))
                // Leaves Delhi 17:50 Delhi time, lands 19:35 Doha time.
                .andExpect(jsonPath("$.blocks[0].startTime").value("2026-10-11T17:50:00+05:30"))
                .andExpect(jsonPath("$.blocks[0].endTime").value("2026-10-11T19:35:00+03:00"))
                .andExpect(jsonPath("$.blocks[0].startZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.blocks[0].endZone").value("Asia/Qatar"))
                .andExpect(jsonPath("$.blocks[0].flight.flightNo").value("IJ 1907"))
                .andExpect(jsonPath("$.blocks[0].flight.arrTerminal").value(nullValue()))
                // Leaves Doha 21:55 Doha time, lands 03:10 London time the next morning.
                .andExpect(jsonPath("$.blocks[1].startTime").value("2026-10-11T21:55:00+03:00"))
                .andExpect(jsonPath("$.blocks[1].endTime").value("2026-10-12T03:10:00+01:00"))
                .andExpect(jsonPath("$.blocks[1].flight.arrZone").value("Europe/London"))
                .andExpect(jsonPath("$.links[0].minGapMinutes").value(0));
    }

    @Test
    void unknownTripIs404ProblemDetails() throws Exception {
        mockMvc.perform(get("/api/trips/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Trip 99 not found"))
                .andExpect(jsonPath("$.instance").value("/api/trips/99"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void idThatIsNotANumberIs400ProblemDetails() throws Exception {
        mockMvc.perform(get("/api/trips/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
