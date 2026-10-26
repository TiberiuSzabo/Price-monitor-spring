package com.persPrjUBB.priceMonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final ScraperService scraper;
    private final ProductRepository products;
    private final PricePointRepository pricePoints;
    private final Clock clock;

    public PriceService(ScraperService scraper, ProductRepository products,
                        PricePointRepository pricePoints, Clock clock) {
        this.scraper = scraper;
        this.products = products;
        this.pricePoints = pricePoints;
        this.clock = clock;
    }

    @Transactional
    public Product track(String url) {
        return record(scraper.scrapeProduct(url));
    }

    public int trackAllFrom(String listingUrl) {
        int ok = 0;
        for (String link : scraper.discoverProducts(listingUrl)) {
            try {
                track(link);
                ok++;
            } catch (ScrapeException | IllegalArgumentException e) {
                log.warn("Skipped {}: {}", link, e.getMessage());
            }
        }
        return ok;
    }

    @Transactional
    public void refresh(Product product) {
        record(scraper.scrapeProduct(product.getUrl()));
    }

    @Transactional(readOnly = true)
    public List<Product> all() {
        return products.findAll();
    }

    @Transactional(readOnly = true)
    public List<PricePoint> history(Long productId) {
        if (!products.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return pricePoints.findByProductIdOrderByCheckedAtAsc(productId);
    }

    @Transactional
    public void untrack(Long productId) {
        if (!products.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        pricePoints.deleteByProductId(productId);
        products.deleteById(productId);
    }

    private Product record(ScrapedProduct scraped) {
        Instant now = clock.instant();
        Product product = products.findByUrl(scraped.url()).orElseGet(() -> new Product(scraped.url()));
        Double previous = product.getCurrentPrice();

        product.setTitle(scraped.title());
        product.setCurrentPrice(scraped.price());
        product.setLastChecked(now);
        product = products.save(product);
        pricePoints.save(new PricePoint(product, scraped.price(), now));

        if (previous != null && previous.doubleValue() != scraped.price()) {
            log.info("Price change for '{}': {} -> {} RON", product.getTitle(), previous, scraped.price());
        }
        return product;
    }
}
