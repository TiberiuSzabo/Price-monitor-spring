package com.persPrjUBB.priceMonitor;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the real HTTP path of the scraper against a local WireMock server that
 * plays the role of eMAG: saved pages, error statuses, a captcha and a slow response.
 * Nothing here leaves the machine.
 */
class ScraperServiceHttpTest {

    private static final String PRODUCT_PATH = "/laptop-test-book-15/pd/TESTSKU1/";
    private static final int TIMEOUT_MS = 500;

    @RegisterExtension
    static WireMockExtension emag = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private MeterRegistry registry;
    private ScraperService scraper;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        scraper = new ScraperService(TIMEOUT_MS, new ScrapeMetrics(registry));
    }

    @Test
    void scrapesProductPageOverHttp() {
        servePage(PRODUCT_PATH, "emag-product.html");

        ScrapedProduct product = scraper.scrapeProduct(emag.url(PRODUCT_PATH) + "?ref=sponsored&recid=abc");

        assertEquals("Laptop Test Book 15, Intel Core i5, 16GB, 512GB SSD", product.title());
        assertEquals(new BigDecimal("3499.99"), product.price());
        assertEquals(emag.url(PRODUCT_PATH), product.url(), "tracking params are stripped before fetching");
        emag.verify(getRequestedFor(urlPathEqualTo(PRODUCT_PATH))
                .withHeader("User-Agent", matching("Mozilla/5\\.0.*"))
                .withHeader("Accept-Language", matching("ro-RO.*")));
        assertEquals(1, checked());
        assertEquals(0, totalFailures());
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @CsvSource({"403, BLOCKED", "429, BLOCKED", "404, OTHER", "500, OTHER", "503, OTHER"})
    void classifiesHttpErrors(int status, ScrapeException.Reason expected) {
        emag.stubFor(get(urlPathEqualTo(PRODUCT_PATH)).willReturn(aResponse().withStatus(status)));

        assertFailure(expected);
    }

    @Test
    void captchaPageIsReportedAsBlocked() {
        servePage(PRODUCT_PATH, "emag-captcha.html");

        assertFailure(ScrapeException.Reason.BLOCKED);
    }

    @Test
    void slowResponseIsReportedAsTimeout() {
        emag.stubFor(get(urlPathEqualTo(PRODUCT_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody(Fixtures.html("emag-product.html"))
                .withFixedDelay(TIMEOUT_MS * 6)));

        assertFailure(ScrapeException.Reason.TIMEOUT);
    }

    @Test
    void pageWithoutPriceIsReportedAsParseError() {
        servePage(PRODUCT_PATH, "emag-product-no-price.html");

        assertFailure(ScrapeException.Reason.PARSE_ERROR);
    }

    @Test
    void invalidUrlIsRejectedWithoutCountingACheck() {
        assertThrows(IllegalArgumentException.class, () -> scraper.scrapeProduct("not a url"));

        assertEquals(0, checked());
        assertEquals(0, totalFailures());
    }

    @Test
    void discoversProductsFromListingPage() {
        servePage("/search/laptop", "emag-listing.html");

        List<String> links = scraper.discoverProducts(emag.url("/search/laptop"));

        assertEquals(List.of(
                "https://www.emag.ro/laptop-test-book-15/pd/TESTSKU1/",
                "https://www.emag.ro/laptop-other-air-14/pd/TESTSKU2/"), links);
    }

    private void servePage(String path, String fixture) {
        emag.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody(Fixtures.html(fixture))));
    }

    /** The scrape must fail with this reason, and exactly that failure series must move. */
    private void assertFailure(ScrapeException.Reason expected) {
        ScrapeException e = assertThrows(ScrapeException.class, () -> scraper.scrapeProduct(emag.url(PRODUCT_PATH)));

        assertEquals(expected, e.getReason(), e.getMessage());
        assertEquals(1, checked());
        assertEquals(1, failures(expected));
        assertEquals(1, totalFailures());
    }

    private double checked() {
        return registry.get("pricemonitor.products.checked").counter().count();
    }

    private double failures(ScrapeException.Reason reason) {
        return registry.get("pricemonitor.scrape.failures").tag("reason", reason.tag()).counter().count();
    }

    private double totalFailures() {
        return registry.get("pricemonitor.scrape.failures").counters().stream()
                .mapToDouble(c -> c.count())
                .sum();
    }
}
