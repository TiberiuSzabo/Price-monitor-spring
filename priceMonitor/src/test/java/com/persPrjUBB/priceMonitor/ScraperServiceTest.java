package com.persPrjUBB.priceMonitor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScraperServiceTest {

    private static final String URL = "https://www.emag.ro/telefon-test/pd/ABC123/";

    @Test
    void readsTitleAndPriceWithDecimals() {
        Document doc = Jsoup.parse("""
                <h1 class="page-title">
                    Telefon mobil Test, 256GB
                </h1>
                <p class="product-new-price" data-test="main-price">6.199<sup><small class="mf-decimal">,</small>99</sup> <span>Lei</span></p>
                """);

        ScrapedProduct p = ScraperService.parseProductPage(doc, URL);

        assertEquals("Telefon mobil Test, 256GB", p.title());
        assertEquals(6199.99, p.price(), 0.001);
    }

    @Test
    void usesMainPriceNotTheFirstPriceOnThePage() {
        Document doc = Jsoup.parse("""
                <h1 class="page-title">Produs</h1>
                <p class="product-new-price">324<sup>,33</sup> Lei</p>
                <p class="product-new-price" data-test="main-price">1.049<sup>,00</sup> Lei</p>
                """);

        assertEquals(1049.0, ScraperService.parseProductPage(doc, URL).price(), 0.001);
    }

    @Test
    void fallsBackToJsonLdPrice() {
        Document doc = Jsoup.parse("""
                <h1 class="page-title">Produs</h1>
                <script type="application/ld+json">
                {"@type": "Product", "offers": {"@type": "Offer", "price": "2499.5", "priceCurrency": "RON"}}
                </script>
                """);

        assertEquals(2499.5, ScraperService.parseProductPage(doc, URL).price(), 0.001);
    }

    @Test
    void failsClearlyWhenLayoutChanged() {
        Document doc = Jsoup.parse("<h1 class=\"page-title\">Produs</h1><div>fara pret</div>");

        assertThrows(ScrapeException.class, () -> ScraperService.parseProductPage(doc, URL));
    }

    @Test
    void parsesRonFormats() {
        assertEquals(6199.0, ScraperService.parseRonPrice("6.199,00 Lei"), 0.001);
        assertEquals(89.9, ScraperService.parseRonPrice("89,90 Lei"), 0.001);
        assertEquals(12999.0, ScraperService.parseRonPrice("12.999 Lei"), 0.001);
        assertNull(ScraperService.parseRonPrice("Stoc epuizat"));
    }

    @Test
    void stripsTrackingParamsAndDuplicatesFromListing() {
        Document doc = Jsoup.parse("""
                <a class="card-v2-title" href="https://www.emag.ro/laptop-a/pd/A1/?ref=sponsored&recid=x">A</a>
                <a class="card-v2-title" href="https://www.emag.ro/laptop-a/pd/A1/?ref=listing">A again</a>
                <a class="card-v2-title" href="https://www.emag.ro/laptop-b/pd/B2/#reviews">B</a>
                """);

        assertEquals(List.of("https://www.emag.ro/laptop-a/pd/A1/", "https://www.emag.ro/laptop-b/pd/B2/"),
                ScraperService.parseListingPage(doc));
    }

    @Test
    void acceptsOnlyEmagUrls() {
        assertEquals(URL, ScraperController.requireEmagUrl(URL));
        assertThrows(IllegalArgumentException.class, () -> ScraperController.requireEmagUrl("https://example.com/x"));
        assertThrows(IllegalArgumentException.class, () -> ScraperController.requireEmagUrl("https://emag.ro.evil.com/x"));
        assertThrows(IllegalArgumentException.class, () -> ScraperController.requireEmagUrl(" "));
    }
}
