package com.safarsaathi.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.safarsaathi.backend.MovableClockConfig;
import com.safarsaathi.backend.MovableClockConfig.MovableClock;
import com.safarsaathi.backend.trip.DisruptionEventRepository;
import com.safarsaathi.backend.trip.DisruptionKind;
import com.safarsaathi.backend.watcher.MockFlightStatusProvider;

/**
 * The demo controls end to end: HTTP request -> mock flight status -> watcher -> ripple engine -> database -> answer,
 * on the seeded demo trips in an in-memory database. The clock starts at 10 Oct 2026 17:30:05 in India, so the trip
 * day is 11 Oct 2026 and every free-change window is still open: the expected times below are exact.
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@AutoConfigureMockMvc
@Import(MovableClockConfig.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovableClock clock;

    @Autowired
    private DisruptionEventRepository eventRepository;

    @Autowired
    private MockFlightStatusProvider mockFlightStatus;

    /** Every test starts from fresh demo data (version 1, all OK, no events) at the start time. */
    @BeforeEach
    void startFromFreshDemoData() throws Exception {
        clock.set(MovableClockConfig.START);
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    @Test
    void delayOf90MinutesRipplesThroughTripOne() throws Exception {
        clock.advance(Duration.ofMinutes(10)); // so updatedAt (17:40:05) differs from createdAt (17:30:05)

        triggerDelay(1, 1, 90)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(6)))
                .andExpect(jsonPath("$.tripId").value(1))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.changedBlockId").value(1))
                .andExpect(jsonPath("$.kind").value("DELAY"))
                // The changed flight first, then every block down the chain.
                .andExpect(jsonPath("$.outcomes[*].blockId", contains(1, 2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.outcomes[*].status",
                        contains("OK", "AUTO_ADJUSTED", "AUTO_ADJUSTED", "NEEDS_USER", "OK", "OK")))
                .andExpect(jsonPath("$.outcomes[0]", aMapWithSize(7)))
                .andExpect(jsonPath("$.outcomes[0].title").value("Flight IJ 512 Mumbai to Bengaluru"))
                .andExpect(jsonPath("$.outcomes[0].newStart").value("2026-10-11T09:30:00+05:30"))
                .andExpect(jsonPath("$.outcomes[0].newEnd").value("2026-10-11T11:15:00+05:30"))
                .andExpect(jsonPath("$.outcomes[0].minutesLate").value(90))
                .andExpect(jsonPath("$.outcomes[0].reason").value("Now 09:30 to 11:15, 90 minutes later than planned."))
                // Bags and cab moved for free.
                .andExpect(jsonPath("$.outcomes[1].newStart").value("2026-10-11T11:15:00+05:30"))
                .andExpect(jsonPath("$.outcomes[1].newEnd").value("2026-10-11T11:35:00+05:30"))
                .andExpect(jsonPath("$.outcomes[2].title").value("Cab pickup to the Whitefield office"))
                .andExpect(jsonPath("$.outcomes[2].newStart").value("2026-10-11T11:35:00+05:30"))
                .andExpect(jsonPath("$.outcomes[2].newEnd").value("2026-10-11T11:35:00+05:30"))
                .andExpect(jsonPath("$.outcomes[2].reason")
                        .value("Moved from 10:05 to 11:35, free of charge, to fit the new plan."))
                // The anchor meeting is not moved: the traveller decides. 11:35 + 75 min ride = 12:50, 50 min late.
                .andExpect(jsonPath("$.outcomes[3].minutesLate").value(50))
                .andExpect(jsonPath("$.outcomes[3].newStart").value(nullValue()))
                .andExpect(jsonPath("$.outcomes[3].newEnd").value(nullValue()))
                .andExpect(jsonPath("$.outcomes[3].reason", containsString("50 minutes late")))
                // Hotel (check-in window) and dinner stay as they are.
                .andExpect(jsonPath("$.outcomes[4].newStart").value(nullValue()))
                .andExpect(jsonPath("$.outcomes[5].newStart").value(nullValue()))
                .andExpect(jsonPath("$.brokenLinks", hasSize(1)))
                .andExpect(jsonPath("$.brokenLinks[0].fromBlockId").value(3))
                .andExpect(jsonPath("$.brokenLinks[0].toBlockId").value(4))
                .andExpect(jsonPath("$.brokenLinks[0].minutesShort").value(50));

        // The database (what the app reads) says the same.
        mockMvc.perform(get("/api/trips/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.createdAt").value("2026-10-10T17:30:05+05:30"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-10T17:40:05+05:30"))
                .andExpect(jsonPath("$.blocks[*].id", contains(1, 2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.blocks[*].status",
                        contains("OK", "AUTO_ADJUSTED", "AUTO_ADJUSTED", "NEEDS_USER", "OK", "OK")))
                .andExpect(jsonPath("$.blocks[*].startTime", contains(
                        "2026-10-11T09:30:00+05:30", "2026-10-11T11:15:00+05:30", "2026-10-11T11:35:00+05:30",
                        "2026-10-11T12:00:00+05:30", "2026-10-11T14:00:00+05:30", "2026-10-11T20:00:00+05:30")))
                .andExpect(jsonPath("$.blocks[*].endTime", contains(
                        "2026-10-11T11:15:00+05:30", "2026-10-11T11:35:00+05:30", "2026-10-11T11:35:00+05:30",
                        "2026-10-11T13:00:00+05:30", "2026-10-11T14:15:00+05:30", "2026-10-11T21:30:00+05:30")));

        assertThat(eventRepository.findByTripIdOrderByIdAsc(1L)).singleElement().satisfies(event -> {
            assertThat(event.getKind()).isEqualTo(DisruptionKind.DELAY);
            assertThat(event.getBlockId()).isEqualTo(1L);
            assertThat(event.getDetail()).isEqualTo("Delayed by 90 minutes (mock flight status)");
            assertThat(event.getDetectedAt()).isEqualTo(MovableClockConfig.START.plus(Duration.ofMinutes(10)));
        });
        assertThat(mockFlightStatus.pendingCount()).isZero(); // the reported change was used up
        mockMvc.perform(get("/api/trips/2")).andExpect(jsonPath("$.version").value(1)); // other trip untouched
    }

    @Test
    void secondDelayAddsToTheCurrentTimes() throws Exception {
        triggerDelay(1, 1, 90).andExpect(status().isOk());

        triggerDelay(1, 1, 90)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.outcomes[0].newStart").value("2026-10-11T11:00:00+05:30"))
                .andExpect(jsonPath("$.outcomes[0].newEnd").value("2026-10-11T12:45:00+05:30"))
                .andExpect(jsonPath("$.outcomes[*].status",
                        contains("OK", "AUTO_ADJUSTED", "AUTO_ADJUSTED", "NEEDS_USER", "OK", "OK")))
                .andExpect(jsonPath("$.outcomes[2].newStart").value("2026-10-11T13:05:00+05:30"))
                .andExpect(jsonPath("$.outcomes[3].minutesLate").value(140))
                .andExpect(jsonPath("$.brokenLinks[0].minutesShort").value(140));

        assertThat(eventRepository.findByTripIdOrderByIdAsc(1L)).hasSize(2);
    }

    @Test
    void cancellingTheFlightLeavesEveryLaterBlockToTheTraveller() throws Exception {
        triggerCancel(1, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(1))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.changedBlockId").value(1))
                .andExpect(jsonPath("$.kind").value("CANCEL"))
                .andExpect(jsonPath("$.outcomes[*].blockId", contains(1, 2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.outcomes[*].status", contains(
                        "CANCELLED", "NEEDS_USER", "NEEDS_USER", "NEEDS_USER", "NEEDS_USER", "NEEDS_USER")))
                .andExpect(jsonPath("$.outcomes[*].minutesLate", everyItem(is(0))))
                .andExpect(jsonPath("$.outcomes[0].newStart").value(nullValue()))
                .andExpect(jsonPath("$.outcomes[0].reason").value("Cancelled."))
                .andExpect(jsonPath("$.outcomes[1].reason")
                        .value("Depends on a cancelled booking earlier in the trip, so you decide."))
                .andExpect(jsonPath("$.brokenLinks", hasSize(0)));

        // The flight keeps its times (the timeline still shows when it was), only its status changes.
        mockMvc.perform(get("/api/trips/1"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.blocks[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.blocks[0].startTime").value("2026-10-11T08:00:00+05:30"))
                .andExpect(jsonPath("$.blocks[*].status", contains(
                        "CANCELLED", "NEEDS_USER", "NEEDS_USER", "NEEDS_USER", "NEEDS_USER", "NEEDS_USER")));
        assertThat(eventRepository.findByTripIdOrderByIdAsc(1L)).singleElement()
                .satisfies(event -> assertThat(event.getKind()).isEqualTo(DisruptionKind.CANCEL));

        // A cancelled flight cannot be delayed or cancelled again: 409, and nothing changes.
        triggerDelay(1, 1, 30)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Block 1 is already cancelled, so it cannot be delayed or cancelled"));
        triggerCancel(1, 1).andExpect(status().isConflict());
        mockMvc.perform(get("/api/trips/1")).andExpect(jsonPath("$.version").value(2));
        assertThat(mockFlightStatus.pendingCount()).isZero();
    }

    @Test
    void missedConnectionOnTripTwo() throws Exception {
        // Pune-Delhi lands at 09:20 instead of 08:20; with the 75-minute connection time the 10:15 Leh flight is
        // 20 minutes short. Flights never move by themselves, so the traveller decides.
        triggerDelay(2, 7, 60)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(2))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.outcomes[*].blockId", contains(7, 8, 9, 10, 11)))
                .andExpect(jsonPath("$.outcomes[*].status",
                        contains("OK", "NEEDS_USER", "AUTO_ADJUSTED", "AUTO_ADJUSTED", "OK")))
                .andExpect(jsonPath("$.outcomes[1].minutesLate").value(20))
                .andExpect(jsonPath("$.brokenLinks[0].fromBlockId").value(7))
                .andExpect(jsonPath("$.brokenLinks[0].toBlockId").value(8))
                .andExpect(jsonPath("$.brokenLinks[0].minutesShort").value(20));

        mockMvc.perform(get("/api/trips/1")).andExpect(jsonPath("$.version").value(1)); // trip 1 untouched
    }

    @Test
    void resetRestoresVersionOneAllOkAndANewCreatedAt() throws Exception {
        triggerDelay(1, 1, 90).andExpect(status().isOk());
        mockFlightStatus.reportDelay(2, 15); // a reported change nobody checked yet: reset must forget it
        clock.advance(Duration.ofHours(1));

        mockMvc.perform(post("/api/demo/reset"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.trips").value(2))
                .andExpect(jsonPath("$.message").value("Demo data reloaded"));

        String newCreatedAt = "2026-10-10T18:30:05+05:30"; // the reset time, not the first load (17:30:05)
        mockMvc.perform(get("/api/trips/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.createdAt").value(newCreatedAt))
                .andExpect(jsonPath("$.updatedAt").value(newCreatedAt))
                .andExpect(jsonPath("$.blocks[*].id", contains(1, 2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.blocks[*].status", everyItem(is("OK"))))
                .andExpect(jsonPath("$.blocks[0].startTime").value("2026-10-11T08:00:00+05:30"));
        mockMvc.perform(get("/api/trips/2"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.createdAt").value(newCreatedAt));
        assertThat(OffsetDateTime.parse(newCreatedAt).toInstant()).isAfter(MovableClockConfig.START);
        assertThat(eventRepository.count()).isZero(); // the events went with the old trips
        assertThat(mockFlightStatus.pendingCount()).isZero();
    }

    @Test
    void unknownTripOrBlockIs404() throws Exception {
        triggerDelay(99, 1, 90)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Trip 99 not found"))
                .andExpect(jsonPath("$.instance").value("/api/demo/trigger-delay"));
        triggerDelay(1, 99, 90)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Block 99 not found in trip 1"));
        // Block 7 exists, but it belongs to trip 2.
        triggerDelay(1, 7, 90)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Block 7 not found in trip 1"));
        triggerCancel(99, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trip 99 not found"));
        triggerCancel(2, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Block 1 not found in trip 2"));

        assertNothingChanged();
    }

    @Test
    void badRequestsAre400() throws Exception {
        triggerDelay(1, 1, 0)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("minutes must be more than 0, but it is 0"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        triggerDelay(1, 1, -5).andExpect(status().isBadRequest());
        triggerDelay(1, 1, 1441)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("at most 1440")));
        postJson("/api/demo/trigger-delay", "{\"tripId\":1,\"blockId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("required")));
        postJson("/api/demo/trigger-cancel", "{\"tripId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("required")));
        postJson("/api/demo/trigger-delay", "this is not JSON")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        postJson("/api/demo/trigger-delay", "{\"tripId\":1,\"blockId\":1,\"minutes\":\"lots\"}")
                .andExpect(status().isBadRequest());

        assertNothingChanged();
    }

    /** After a rejected request: both trips still at version 1, no events, nothing waiting in the mock. */
    private void assertNothingChanged() throws Exception {
        mockMvc.perform(get("/api/trips/1")).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/trips/2")).andExpect(jsonPath("$.version").value(1));
        assertThat(eventRepository.count()).isZero();
        assertThat(mockFlightStatus.pendingCount()).isZero();
    }

    private ResultActions triggerDelay(long tripId, long blockId, int minutes) throws Exception {
        return postJson("/api/demo/trigger-delay",
                "{\"tripId\":" + tripId + ",\"blockId\":" + blockId + ",\"minutes\":" + minutes + "}");
    }

    private ResultActions triggerCancel(long tripId, long blockId) throws Exception {
        return postJson("/api/demo/trigger-cancel", "{\"tripId\":" + tripId + ",\"blockId\":" + blockId + "}");
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
