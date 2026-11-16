package com.persPrjUBB.priceMonitor;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
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
                .thenReturn(scraped("100.00"))
                .thenReturn(scraped("89.99"));

        prices.track(URL);
        Product p = prices.track(URL);

        assertEquals(1, products.count());
        assertEquals(new BigDecimal("89.99"), p.getCurrentPrice());
        List<PricePoint> history = prices.history(p.getId());
        assertEquals(List.of(new BigDecimal("100.00"), new BigDecimal("89.99")),
                history.stream().map(PricePoint::getPrice).toList());
    }

    @Test
    void pricesComeBackFromTheDatabaseExactlyAsStored() {
        // values that have no exact binary floating-point representation
        when(scraper.scrapeProduct(URL))
                .thenReturn(scraped("0.10"))
                .thenReturn(scraped("0.20"))
                .thenReturn(scraped("1234567.89"));

        prices.track(URL);
        prices.track(URL);
        Long id = prices.track(URL).getId();

        List<BigDecimal> stored = prices.history(id).stream().map(PricePoint::getPrice).toList();
        assertEquals(List.of(new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("1234567.89")), stored);
        assertEquals(new BigDecimal("0.30"), stored.get(0).add(stored.get(1)), "sums are exact");
        assertEquals(new BigDecimal("1234567.89"), products.findById(id).orElseThrow().getCurrentPrice());
    }

    @Test
    void firstCheckAndUnchangedPriceAreNotPriceChanges() {
        when(scraper.scrapeProduct(URL)).thenReturn(scraped("100.00"));
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
    void samePriceWithDifferentScaleIsNotAChange() {
        // 3499.9, 3499.90 and 3499.900 are one price. BigDecimal.equals says they differ
        // (it compares scale too), so detection has to use compareTo.
        when(scraper.scrapeProduct(URL))
                .thenReturn(scraped("3499.9"))
                .thenReturn(scraped("3499.90"))
                .thenReturn(scraped("3499.900"));
        double dropsBefore = priceChanges("drop");
        double increasesBefore = priceChanges("increase");

        prices.track(URL);
        prices.track(URL);
        Product product = prices.track(URL);

        assertEquals(dropsBefore, priceChanges("drop"));
        assertEquals(increasesBefore, priceChanges("increase"));
        assertEquals(0, new BigDecimal("3499.90").compareTo(product.getCurrentPrice()));
        assertEquals(new BigDecimal("3499.90"), products.findByUrl(URL).orElseThrow().getCurrentPrice(),
                "the column normalises every value to two decimals");
    }

    @Test
    void detectsPriceDropsAndIncreases() {
        when(scraper.scrapeProduct(URL))
                .thenReturn(scraped("100.00"))
                .thenReturn(scraped("89.99"))
                .thenReturn(scraped("89.99"))
                .thenReturn(scraped("120.00"));
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
        assertEquals(new BigDecimal("120.00"), products.findByUrl(URL).orElseThrow().getCurrentPrice());
    }

    @Test
    void oneCentIsAPriceChange() {
        when(scraper.scrapeProduct(URL))
                .thenReturn(scraped("3499.99"))
                .thenReturn(scraped("3499.98"));
        double dropsBefore = priceChanges("drop");

        prices.track(URL);
        prices.track(URL);

        assertEquals(dropsBefore + 1, priceChanges("drop"));
    }

    @Test
    void discoverSkipsPagesThatFail() {
        String listing = "https://www.emag.ro/search/x";
        String ok = "https://www.emag.ro/ok/pd/1/";
        String broken = "https://www.emag.ro/broken/pd/2/";
        when(scraper.discoverProducts(listing)).thenReturn(List.of(ok, broken));
        when(scraper.scrapeProduct(ok)).thenReturn(new ScrapedProduct(ok, "Ok", new BigDecimal("10.00")));
        when(scraper.scrapeProduct(broken)).thenThrow(new ScrapeException("no price"));

        assertEquals(1, prices.trackAllFrom(listing));
        assertEquals(1, products.count());
    }

    @Test
    void untrackRemovesProductAndHistory() {
        when(scraper.scrapeProduct(URL)).thenReturn(scraped("50.00"));
        Long id = prices.track(URL).getId();

        prices.untrack(id);

        assertEquals(0, products.count());
        assertEquals(0, pricePoints.count());
        assertThrows(ProductNotFoundException.class, () -> prices.history(id));
    }

    private static ScrapedProduct scraped(String price) {
        return new ScrapedProduct(URL, "Produs", new BigDecimal(price));
    }

    // the registry is shared by every test in the context, so assertions compare before/after
    private double priceChanges(String direction) {
        return registry.get("pricemonitor.price.changes").tag("direction", direction).counter().count();
    }
}
