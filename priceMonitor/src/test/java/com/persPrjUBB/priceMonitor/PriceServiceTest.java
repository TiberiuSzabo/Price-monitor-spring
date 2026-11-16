package com.persPrjUBB.priceMonitor;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PriceServiceTest {

    private static final String URL = "https://www.emag.ro/produs/pd/X1/";

    @MockitoBean
    ScraperService scraper;

    @Autowired
    PriceService prices;

    @Autowired
    ProductRepository products;

    @Autowired
    PricePointRepository pricePoints;

    @Autowired
    MeterRegistry registry;

    @BeforeEach
    void clean() {
        pricePoints.deleteAll();
        products.deleteAll();
    }

    @Test
    void sameUrlKeepsOneProductAndBuildsHistory() {
        when(scraper.scrapeProduct(URL))
                .thenReturn(new ScrapedProduct(URL, "Produs", 100.0))
                .thenReturn(new ScrapedProduct(URL, "Produs", 89.99));

        prices.track(URL);
        Product p = prices.track(URL);

        assertEquals(1, products.count());
        assertEquals(89.99, p.getCurrentPrice(), 0.001);
        List<PricePoint> history = prices.history(p.getId());
        assertEquals(List.of(100.0, 89.99), history.stream().map(PricePoint::getPrice).toList());
    }

    @Test
    void firstCheckAndUnchangedPriceAreNotPriceChanges() {
        when(scraper.scrapeProduct(URL)).thenReturn(new ScrapedProduct(URL, "Produs", 100.0));
        double dropsBefore = priceChanges("drop");
        double increasesBefore = priceChanges("increase");

        prices.track(URL);
        prices.track(URL);

        assertEquals(dropsBefore, priceChanges("drop"));
        assertEquals(increasesBefore, priceChanges("increase"));
        assertEquals(2, prices.history(products.findByUrl(URL).orElseThrow().getId()).size(),
                "every check is still recorded in the history");
    }

    @Test
    void detectsPriceDropsAndIncreases() {
        when(scraper.scrapeProduct(URL))
                .thenReturn(new ScrapedProduct(URL, "Produs", 100.0))
                .thenReturn(new ScrapedProduct(URL, "Produs", 89.99))
                .thenReturn(new ScrapedProduct(URL, "Produs", 89.99))
                .thenReturn(new ScrapedProduct(URL, "Produs", 120.0));
        double dropsBefore = priceChanges("drop");
        double increasesBefore = priceChanges("increase");

        prices.track(URL);
        Product product = prices.track(URL);
        assertEquals(dropsBefore + 1, priceChanges("drop"), "100 -> 89.99 is a drop");
        assertEquals(increasesBefore, priceChanges("increase"));

        prices.refresh(product);
        assertEquals(dropsBefore + 1, priceChanges("drop"), "same price again is not a change");

        prices.refresh(product);
        assertEquals(dropsBefore + 1, priceChanges("drop"));
        assertEquals(increasesBefore + 1, priceChanges("increase"), "89.99 -> 120 is an increase");
        assertEquals(120.0, products.findByUrl(URL).orElseThrow().getCurrentPrice(), 0.001);
    }

    @Test
    void discoverSkipsPagesThatFail() {
        String listing = "https://www.emag.ro/search/x";
        String ok = "https://www.emag.ro/ok/pd/1/";
        String broken = "https://www.emag.ro/broken/pd/2/";
        when(scraper.discoverProducts(listing)).thenReturn(List.of(ok, broken));
        when(scraper.scrapeProduct(ok)).thenReturn(new ScrapedProduct(ok, "Ok", 10.0));
        when(scraper.scrapeProduct(broken)).thenThrow(new ScrapeException("no price"));

        assertEquals(1, prices.trackAllFrom(listing));
        assertEquals(1, products.count());
    }

    @Test
    void untrackRemovesProductAndHistory() {
        when(scraper.scrapeProduct(URL)).thenReturn(new ScrapedProduct(URL, "Produs", 50.0));
        Long id = prices.track(URL).getId();

        prices.untrack(id);

        assertEquals(0, products.count());
        assertEquals(0, pricePoints.count());
        assertThrows(ProductNotFoundException.class, () -> prices.history(id));
    }

    // the registry is shared by every test in the context, so assertions compare before/after
    private double priceChanges(String direction) {
        return registry.get("pricemonitor.price.changes").tag("direction", direction).counter().count();
    }
}
