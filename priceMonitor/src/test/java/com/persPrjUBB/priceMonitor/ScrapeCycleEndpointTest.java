package com.persPrjUBB.priceMonitor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ScrapeCycleEndpointTest {

    private final PriceScheduler priceScheduler = mock(PriceScheduler.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ScrapeCycleEndpoint endpoint = new ScrapeCycleEndpoint(priceScheduler, taskScheduler);

    @Test
    void queuesOneCycleAndIgnoresRepeatedTriggersUntilItStarts() {
        assertEquals("queued", endpoint.trigger().get("status"));
        assertEquals("already queued", endpoint.trigger().get("status"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, times(1)).schedule(task.capture(), any(Instant.class));
        verify(priceScheduler, never()).checkAll();

        task.getValue().run();

        verify(priceScheduler).checkAll();
        assertEquals("queued", endpoint.trigger().get("status"), "a new cycle can be queued once the previous one started");
    }
}
