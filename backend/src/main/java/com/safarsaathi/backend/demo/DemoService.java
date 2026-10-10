package com.safarsaathi.backend.demo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.safarsaathi.backend.trip.Block;
import com.safarsaathi.backend.watcher.DisruptionResult;
import com.safarsaathi.backend.watcher.DisruptionService;
import com.safarsaathi.backend.watcher.FlightWatcher;
import com.safarsaathi.backend.watcher.MockFlightStatusProvider;

/**
 * The demo buttons, behind {@link DemoController}. They play the airline:
 * <ul>
 *   <li>trigger a delay or cancellation: tell the {@link MockFlightStatusProvider} about it, then let the
 *       {@link FlightWatcher} check that block AT ONCE, so the answer shows the ripple without waiting up to 30 s
 *       for the timer. It is the same path a real delay takes (provider, watcher, DisruptionService);</li>
 *   <li>reset: forget the reported changes and reload the demo trips.</li>
 * </ul>
 * Each runs inside {@link FlightWatcher#runExclusively}, so the timer's check never runs in the middle of it.
 */
@Service
public class DemoService {

    /** Longest delay one press may add: one day. Enough for every demo story, and keeps the numbers sane. */
    static final int MAX_DELAY_MINUTES = 1440;

    private final FlightWatcher flightWatcher;
    private final MockFlightStatusProvider mockFlightStatus;
    private final DisruptionService disruptionService;
    private final DemoTripSeeder seeder;

    public DemoService(FlightWatcher flightWatcher, MockFlightStatusProvider mockFlightStatus,
                       DisruptionService disruptionService, DemoTripSeeder seeder) {
        this.flightWatcher = flightWatcher;
        this.mockFlightStatus = mockFlightStatus;
        this.disruptionService = disruptionService;
        this.seeder = seeder;
    }

    /**
     * Block {@code blockId} of trip {@code tripId} runs {@code minutes} later than its current times; the ripple runs
     * at once. The boxed parameters may be null (a field missing from the JSON): that is a 400.
     */
    public DisruptionResult triggerDelay(Long tripId, Long blockId, Integer minutes) {
        if (tripId == null || blockId == null || minutes == null) {
            throw badRequest("tripId, blockId and minutes are all required, e.g. {\"tripId\": 1, \"blockId\": 1, "
                    + "\"minutes\": 90}");
        }
        if (minutes <= 0) {
            throw badRequest("minutes must be more than 0, but it is " + minutes);
        }
        if (minutes > MAX_DELAY_MINUTES) {
            throw badRequest("minutes can be at most " + MAX_DELAY_MINUTES + " (one day), but it is " + minutes);
        }
        return flightWatcher.runExclusively(() -> {
            // Check the ids first (404, 409): a change reported for a wrong block would wait in the mock forever.
            Block block = disruptionService.findBlockToChange(tripId, blockId);
            mockFlightStatus.reportDelay(block.getId(), minutes);
            return checkNow(block);
        });
    }

    /** Block {@code blockId} of trip {@code tripId} is cancelled; the ripple runs at once. */
    public DisruptionResult triggerCancel(Long tripId, Long blockId) {
        if (tripId == null || blockId == null) {
            throw badRequest("tripId and blockId are both required, e.g. {\"tripId\": 1, \"blockId\": 1}");
        }
        return flightWatcher.runExclusively(() -> {
            Block block = disruptionService.findBlockToChange(tripId, blockId);
            mockFlightStatus.reportCancel(block.getId());
            return checkNow(block);
        });
    }

    /**
     * Back to the start: forget the reported changes and reload the demo trips (fresh dates, version 1, all OK,
     * a new createdAt; their disruption events go with the old trips).
     *
     * @return how many trips were loaded (2)
     */
    public int reset() {
        return flightWatcher.runExclusively(() -> {
            mockFlightStatus.clear();
            return seeder.deleteAllAndReseed();
        });
    }

    /** Lets the watcher check the block with the mock provider, which now holds exactly the change just reported. */
    private DisruptionResult checkNow(Block block) {
        return flightWatcher.checkBlock(block, mockFlightStatus)
                .orElseThrow(() -> new IllegalStateException("The mock flight status lost the change for block "
                        + block.getId())); // cannot happen: we hold the watcher's lock since reporting it
    }

    private static ResponseStatusException badRequest(String detail) {
        // Becomes a 400 Problem Details answer with this text as "detail" (like TripNotFoundException does for 404).
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
