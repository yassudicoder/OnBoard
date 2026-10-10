package com.safarsaathi.backend.demo;

import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.safarsaathi.backend.watcher.DisruptionResult;

/**
 * The demo controls (docs/api.md, "Live in Phase 2: demo controls"). The /demo page calls them; so can curl.
 * <ul>
 *   <li>{@code POST /api/demo/trigger-delay} {@code {"tripId":1,"blockId":1,"minutes":90}}: the block runs 90 minutes
 *       later than its current times; answers what the ripple did ({@link DisruptionResult}).</li>
 *   <li>{@code POST /api/demo/trigger-cancel} {@code {"tripId":1,"blockId":1}}: the block is cancelled.</li>
 *   <li>{@code POST /api/demo/reset}: reload the demo trips, {@code {"trips":2,"message":"Demo data reloaded"}}.</li>
 * </ul>
 * Errors are Problem Details JSON: 404 unknown trip or block (or a block of another trip), 400 missing fields,
 * minutes of 0 or less, or a body that is not JSON, 409 a block that is already cancelled or done.
 *
 * <p>Demo mode only ({@link DemoModeOnly}): with APP_MODE=prod this controller is not created, so every
 * POST /api/demo/... answers 404.
 */
@RestController
@RequestMapping("/api/demo")
@Conditional(DemoModeOnly.class)
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/trigger-delay")
    public DisruptionResult triggerDelay(@RequestBody DelayRequest request) {
        return demoService.triggerDelay(request.tripId(), request.blockId(), request.minutes());
    }

    @PostMapping("/trigger-cancel")
    public DisruptionResult triggerCancel(@RequestBody CancelRequest request) {
        return demoService.triggerCancel(request.tripId(), request.blockId());
    }

    @PostMapping("/reset")
    public ResetResponse reset() {
        return new ResetResponse(demoService.reset(), "Demo data reloaded");
    }

    /**
     * The trigger-delay body. Boxed types (Long, Integer) so a missing field arrives as null and gets a clear 400,
     * instead of silently becoming 0.
     */
    public record DelayRequest(Long tripId, Long blockId, Integer minutes) {
    }

    /** The trigger-cancel body. */
    public record CancelRequest(Long tripId, Long blockId) {
    }

    /** The reset answer: how many trips were loaded, and a short message. */
    public record ResetResponse(int trips, String message) {
    }
}
