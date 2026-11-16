package com.persPrjUBB.priceMonitor;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts a scrape cycle on demand instead of waiting for the schedule:
 *
 * <pre>curl -X POST http://localhost:9001/actuator/scrapecycle</pre>
 *
 * It is an actuator endpoint on purpose: it lives on the management port, so it is
 * not reachable through the public API. The cycle is handed to the same single-thread
 * scheduler that runs the periodic check, so a manual run can never overlap a
 * scheduled one - it just waits its turn.
 */
@Component
@Endpoint(id = "scrapecycle")
public class ScrapeCycleEndpoint {

    private final PriceScheduler priceScheduler;
    private final TaskScheduler taskScheduler;
    private final AtomicBoolean queued = new AtomicBoolean();

    public ScrapeCycleEndpoint(PriceScheduler priceScheduler, TaskScheduler taskScheduler) {
        this.priceScheduler = priceScheduler;
        this.taskScheduler = taskScheduler;
    }

    @WriteOperation
    public Map<String, String> trigger() {
        if (!queued.compareAndSet(false, true)) {
            return Map.of("status", "already queued");
        }
        taskScheduler.schedule(() -> {
            queued.set(false);
            priceScheduler.checkAll();
        }, Instant.now());
        return Map.of("status", "queued");
    }
}
