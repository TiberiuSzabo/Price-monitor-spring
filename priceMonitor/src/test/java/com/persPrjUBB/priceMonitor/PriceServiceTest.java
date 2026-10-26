package com.persPrjUBB.priceMonitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
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
}
