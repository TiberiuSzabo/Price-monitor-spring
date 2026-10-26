package com.persPrjUBB.priceMonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceScheduler.class);

    private final PriceService prices;
    private final long delayBetweenRequestsMs;

    public PriceScheduler(PriceService prices,
                          @Value("${pricemonitor.delay-between-requests-ms:5000}") long delayBetweenRequestsMs) {
        this.prices = prices;
        this.delayBetweenRequestsMs = delayBetweenRequestsMs;
    }

    @Scheduled(initialDelayString = "${pricemonitor.check-interval-ms:3600000}",
            fixedDelayString = "${pricemonitor.check-interval-ms:3600000}")
    public void checkAll() {
        List<Product> tracked = prices.all();
        log.info("Checking prices for {} products", tracked.size());

        int failed = 0;
        for (Product p : tracked) {
            try {
                prices.refresh(p);
            } catch (ScrapeException | IllegalArgumentException e) {
                failed++;
                log.warn("Could not check {}: {}", p.getUrl(), e.getMessage());
            }
            if (!sleep(delayBetweenRequestsMs)) {
                return;
            }
        }
        if (failed > 0 && failed == tracked.size()) {
            log.error("Every product failed this round - eMAG may have changed its markup or blocked the scraper");
        }
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
