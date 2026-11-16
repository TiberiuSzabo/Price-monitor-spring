package com.persPrjUBB.priceMonitor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceSchedulerTest {

    private final PriceService prices = mock(PriceService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    // no pause between requests, so the test does not sleep
    private final PriceScheduler scheduler = new PriceScheduler(prices, new ScrapeMetrics(registry), 0);

    @Test
    void oneFailingProductDoesNotStopTheCycle() {
        Product slow = new Product("https://www.emag.ro/slow/pd/1/");
        Product fine = new Product("https://www.emag.ro/fine/pd/2/");
        when(prices.all()).thenReturn(List.of(slow, fine));
        doThrow(new ScrapeException(ScrapeException.Reason.TIMEOUT, "too slow")).when(prices).refresh(slow);

        scheduler.checkAll();

        verify(prices).refresh(slow);
        verify(prices).refresh(fine);
    }

    @Test
    void everyCycleIsTimed() {
        when(prices.all()).thenReturn(List.of());

        scheduler.checkAll();
        scheduler.checkAll();

        assertEquals(2, registry.get("pricemonitor.scrape.cycle").timer().count());
    }
}
