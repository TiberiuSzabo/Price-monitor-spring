package com.persPrjUBB.priceMonitor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertEquals(new BigDecimal("6199.99"), p.price());
    }

    @Test
    void usesMainPriceNotTheFirstPriceOnThePage() {
        Document doc = Jsoup.parse("""
                <h1 class="page-title">Produs</h1>
                <p class="product-new-price">324<sup>,33</sup> Lei</p>
                <p class="product-new-price" data-test="main-price">1.049<sup>,00</sup> Lei</p>
                """);

        assertEquals(new BigDecimal("1049.00"), ScraperService.parseProductPage(doc, URL).price());
    }

    @Test
    void fallsBackToJsonLdPrice() {
        Document doc = Jsoup.parse("""
                <h1 class="page-title">Produs</h1>
                <script type="application/ld+json">
                {"@type": "Product", "offers": {"@type": "Offer", "price": "2499.5", "priceCurrency": "RON"}}
                </script>
                """);

        assertEquals(new BigDecimal("2499.50"), ScraperService.parseProductPage(doc, URL).price());
    }

    @Test
    void jsonLdPriceWithoutDecimalsGetsTwo() {
        Document doc = Jsoup.parse("""
                <h1 class="page-title">Produs</h1>
                <script type="application/ld+json">
                {"@type": "Product", "offers": {"@type": "Offer", "price": 6199, "priceCurrency": "RON"}}
                </script>
                """);

        assertEquals(new BigDecimal("6199.00"), ScraperService.parseProductPage(doc, URL).price());
    }

    @Test
    void failsClearlyWhenLayoutChanged() {
        Document doc = Jsoup.parse("<h1 class=\"page-title\">Produs</h1><div>fara pret</div>");

        ScrapeException e = assertThrows(ScrapeException.class, () -> ScraperService.parseProductPage(doc, URL));
        assertEquals(ScrapeException.Reason.PARSE_ERROR, e.getReason());
    }

    @Test
    void parsesSavedProductPage() {
        Document doc = Jsoup.parse(Fixtures.html("emag-product.html"));

        ScrapedProduct p = ScraperService.parseProductPage(doc, URL);

        assertEquals("Laptop Test Book 15, Intel Core i5, 16GB, 512GB SSD", p.title());
        // superscript decimals: 3.499<sup><small>,</small>99</sup> - and not the bundle price that comes first
        assertEquals(new BigDecimal("3499.99"), p.price());
    }

    @Test
    void savedPageWithoutPriceIsAParseError() {
        Document doc = Jsoup.parse(Fixtures.html("emag-product-no-price.html"));

        ScrapeException e = assertThrows(ScrapeException.class, () -> ScraperService.parseProductPage(doc, URL));
        assertEquals(ScrapeException.Reason.PARSE_ERROR, e.getReason());
    }

    @Test
    void captchaPageIsRecognisedAsBlocked() {
        Document doc = Jsoup.parse(Fixtures.html("emag-captcha.html"));

        ScrapeException e = assertThrows(ScrapeException.class, () -> ScraperService.parseProductPage(doc, URL));
        assertEquals(ScrapeException.Reason.BLOCKED, e.getReason());
    }

    @Test
    void parsesSavedListingPage() {
        Document doc = Jsoup.parse(Fixtures.html("emag-listing.html"));

        assertEquals(List.of(
                        "https://www.emag.ro/laptop-test-book-15/pd/TESTSKU1/",
                        "https://www.emag.ro/laptop-other-air-14/pd/TESTSKU2/"),
                ScraperService.parseListingPage(doc));
    }

    // "." is always a thousands separator and "," always the decimal one. The expected value is
    // compared with equals, so the scale is checked too: every parsed price has exactly two decimals.
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "3.499,99 Lei        | 3499.99",
            "6.199,00 Lei        | 6199.00",
            "89,90 Lei           | 89.90",
            "0,99 Lei            | 0.99",
            "12.999 Lei          | 12999.00",
            "45 Lei              | 45.00",
            "3.499,9 Lei         | 3499.90",
            "1.234.567,89 Lei    | 1234567.89",
            "de la 3.499,99 Lei  | 3499.99",
            "3.499,99Lei         | 3499.99",
            "3.499,99 RON        | 3499.99",
    })
    void parsesRonFormats(String raw, BigDecimal expected) {
        assertEquals(expected, ScraperService.parseRonPrice(raw));
    }

    @ParameterizedTest(name = "\"{0}\" is not a price")
    @ValueSource(strings = {"Stoc epuizat", "", "Lei", ",", "."})
    void textWithoutDigitsIsNotAPrice(String raw) {
        assertNull(ScraperService.parseRonPrice(raw));
    }

    @Test
    void nonBreakingSpaceBeforeCurrencyIsIgnored() {
        assertEquals(new BigDecimal("3499.99"), ScraperService.parseRonPrice("3.499,99 Lei"));
    }

    @Test
    void parsedPricesAddUpExactly() {
        BigDecimal sum = ScraperService.parseRonPrice("0,10 Lei").add(ScraperService.parseRonPrice("0,20 Lei"));

        assertEquals(new BigDecimal("0.30"), sum);
        // the reason prices are not doubles: the same sum in binary floating point is 0.30000000000000004
        assertNotEquals(0.30, 0.10 + 0.20);
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
