package com.persPrjUBB.priceMonitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * The scraper's business metrics, in one place so names and tags stay consistent.
 * Every series is registered at startup, so it is exported as 0 before the first
 * event - rate() and the failure-rate alert work from the first scrape.
 *
 * <pre>
 * pricemonitor_products_checked_total              scrape attempts
 * pricemonitor_scrape_failures_total{reason}       timeout | blocked | parse_error | other
 * pricemonitor_price_changes_total{direction}      drop | increase
 * pricemonitor_scrape_cycle_seconds_{count,sum,max} duration of one full pass
 * </pre>
 */
@Component
public class ScrapeMetrics {

    private final Counter productsChecked;
    private final Map<ScrapeException.Reason, Counter> failures = new EnumMap<>(ScrapeException.Reason.class);
    private final Counter priceDrops;
    private final Counter priceIncreases;
    private final Timer cycle;

    public ScrapeMetrics(MeterRegistry registry) {
        productsChecked = Counter.builder("pricemonitor.products.checked")
                .description("Product pages the scraper tried to read")
                .register(registry);

        for (ScrapeException.Reason reason : ScrapeException.Reason.values()) {
            failures.put(reason, Counter.builder("pricemonitor.scrape.failures")
                    .description("Product checks that failed, by cause")
                    .tag("reason", reason.tag())
                    .register(registry));
        }

        priceDrops = priceChangeCounter(registry, "drop");
        priceIncreases = priceChangeCounter(registry, "increase");

        cycle = Timer.builder("pricemonitor.scrape.cycle")
                .description("Duration of one pass over all tracked products")
                .register(registry);
    }

    private static Counter priceChangeCounter(MeterRegistry registry, String direction) {
        return Counter.builder("pricemonitor.price.changes")
                .description("Checks where the price differed from the previous one")
                .tag("direction", direction)
                .register(registry);
    }

    public void productChecked() {
        productsChecked.increment();
    }

    public void scrapeFailed(ScrapeException.Reason reason) {
        failures.get(reason).increment();
    }

    public void priceChanged(BigDecimal previous, BigDecimal current) {
        (current.compareTo(previous) < 0 ? priceDrops : priceIncreases).increment();
    }

    public void recordCycle(Runnable cycleRun) {
        cycle.record(cycleRun);
    }
}
